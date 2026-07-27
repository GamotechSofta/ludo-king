package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.BotMoveEvaluator.VictimInfo;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hard-bot kill dice assist: scans legal capture opportunities within 1–6 pips
 * and usually rolls the exact value needed. Safe stars / blocks are ignored via
 * {@link BotMoveEvaluator#findCaptureVictim}.
 */
final class BotKillDiceAssist {

  /** Chance (0–100) to roll the exact capture die when a kill is reachable. */
  static final int CAPTURE_ASSIST_CHANCE_PCT = 80;

  private BotKillDiceAssist() {}

  @FunctionalInterface
  interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  static final class CaptureOpportunity {
    final int dice;
    final int tokenIndex;
    final int landPos;
    final VictimInfo victim;
    final int victimRemaining;
    final int victimProgress;
    final boolean safeAfterCapture;
    final int botAdvance;

    CaptureOpportunity(
        int dice,
        int tokenIndex,
        int landPos,
        VictimInfo victim,
        int victimRemaining,
        int victimProgress,
        boolean safeAfterCapture,
        int botAdvance
    ) {
      this.dice = dice;
      this.tokenIndex = tokenIndex;
      this.landPos = landPos;
      this.victim = victim;
      this.victimRemaining = victimRemaining;
      this.victimProgress = victimProgress;
      this.safeAfterCapture = safeAfterCapture;
      this.botAdvance = botAdvance;
    }
  }

  /**
   * @return dice 1–6 for the best capture, or {@code null} for a normal random roll
   */
  static Integer maybePickCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality
  ) {
    return maybePickCaptureDice(snap, botSeat, legality, ThreadLocalRandom.current());
  }

  /**
   * Same as {@link #maybePickCaptureDice(GameSnapshot, int, MoveLegality)} with injectable RNG.
   */
  static Integer maybePickCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng
  ) {
    Integer best = pickBestCaptureDice(snap, botSeat, legality, rng);
    if (best == null || rng == null) {
      return best;
    }
    if (rng.nextInt(100) >= CAPTURE_ASSIST_CHANCE_PCT) {
      return null;
    }
    return best;
  }

  /** Always picks the highest-priority capture die when one exists. */
  static Integer pickBestCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality
  ) {
    return pickBestCaptureDice(snap, botSeat, legality, ThreadLocalRandom.current());
  }

  static Integer pickBestCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng
  ) {
    if (snap == null || legality == null || botSeat < 0) {
      return null;
    }
    if (snap.getIsBot() == null
        || botSeat >= snap.getIsBot().length
        || !Boolean.TRUE.equals(snap.getIsBot()[botSeat])) {
      return null;
    }

    String colorName = seatColor(snap, botSeat);
    if (colorName == null) {
      return null;
    }
    LudoColor color;
    try {
      color = LudoColor.valueOf(colorName);
    } catch (RuntimeException ex) {
      return null;
    }

    List<Integer> own = snap.getTokenPositions().get(colorName);
    Map<String, List<Integer>> all = snap.getTokenPositions();
    List<String> seatColors = snap.getSeatColors();
    if (own == null || all == null || seatColors == null) {
      return null;
    }

    List<CaptureOpportunity> opportunities = new ArrayList<>();
    for (int t = 0; t < own.size(); t++) {
      Integer fromObj = own.get(t);
      int from = fromObj == null ? JAIL : fromObj;
      if (isJail(from) || isHome(from)) {
        continue;
      }
      for (int dice = 1; dice <= 6; dice++) {
        if (!legality.canMove(t, dice)) {
          continue;
        }
        int land = BotMoveEvaluator.applySteps(color, from, dice);
        VictimInfo victim =
            BotMoveEvaluator.findCaptureVictim(botSeat, land, all, seatColors);
        if (victim == null) {
          continue;
        }
        int victimRem = BotMoveEvaluator.remainingDistance(victim.color, land);
        if (victimRem == Integer.MAX_VALUE) {
          victimRem = TOTAL_TILES + HOME_STEPS;
        }
        int victimProgress = Math.max(0, TOTAL_TILES + HOME_STEPS - victimRem);
        boolean safe =
            !BotMoveEvaluator.isPositionThreatened(botSeat, land, all, seatColors);
        int botFromRem = BotMoveEvaluator.remainingDistance(color, from);
        int botToRem = BotMoveEvaluator.remainingDistance(color, land);
        int botAdvance =
            botFromRem != Integer.MAX_VALUE && botToRem != Integer.MAX_VALUE
                ? botFromRem - botToRem
                : 0;
        opportunities.add(
            new CaptureOpportunity(
                dice,
                t,
                land,
                victim,
                victimRem,
                victimProgress,
                safe,
                botAdvance));
      }
    }

    if (opportunities.isEmpty()) {
      return null;
    }

    opportunities.sort(priorityComparator());
    CaptureOpportunity best = opportunities.get(0);
    List<CaptureOpportunity> ties = new ArrayList<>();
    for (CaptureOpportunity o : opportunities) {
      if (priorityComparator().compare(o, best) == 0) {
        ties.add(o);
      }
    }
    Random pickRng = rng != null ? rng : ThreadLocalRandom.current();
    CaptureOpportunity chosen = ties.get(pickRng.nextInt(ties.size()));
    return chosen.dice;
  }

  /** @deprecated use {@link #pickBestCaptureDice} or {@link #maybePickCaptureDice} */
  @Deprecated
  static Integer pickCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality
  ) {
    return pickBestCaptureDice(snap, botSeat, legality);
  }

  private static Comparator<CaptureOpportunity> priorityComparator() {
    return Comparator
        // Closest victim to HOME first (lowest remaining steps)
        .comparingInt((CaptureOpportunity o) -> o.victimRemaining)
        // Then greatest victim progress on the board
        .thenComparing(Comparator.comparingInt((CaptureOpportunity o) -> o.victimProgress).reversed())
        // Prefer landing safe after the capture
        .thenComparing((CaptureOpportunity o) -> !o.safeAfterCapture);
  }

  private static String seatColor(GameSnapshot snap, int seat) {
    List<String> colors = snap.getSeatColors();
    if (colors != null && seat >= 0 && seat < colors.size()) {
      return colors.get(seat);
    }
    return snap.getCurrentColor();
  }
}
