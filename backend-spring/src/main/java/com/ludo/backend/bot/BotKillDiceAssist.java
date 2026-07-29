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
 *
 * <p>Assist probability scales down with player count so multi-bot tables do not
 * cascade-eliminate every seat.
 */
final class BotKillDiceAssist {

  /** Defaults: 2P 40%, 3P 25%, 4P 10%. */
  static final double DEFAULT_TWO_PLAYER = 0.40;
  static final double DEFAULT_THREE_PLAYER = 0.25;
  static final double DEFAULT_FOUR_PLAYER = 0.10;

  private BotKillDiceAssist() {}

  /** Per-player-count assist rates (0.0–1.0). */
  static final class KillAssistRates {
    final double twoPlayer;
    final double threePlayer;
    final double fourPlayer;

    KillAssistRates(double twoPlayer, double threePlayer, double fourPlayer) {
      this.twoPlayer = clamp01(twoPlayer);
      this.threePlayer = clamp01(threePlayer);
      this.fourPlayer = clamp01(fourPlayer);
    }

    static KillAssistRates defaults() {
      return new KillAssistRates(
          DEFAULT_TWO_PLAYER, DEFAULT_THREE_PLAYER, DEFAULT_FOUR_PLAYER);
    }
  }

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
    return maybePickCaptureDice(
        snap, botSeat, legality, ThreadLocalRandom.current(), KillAssistRates.defaults());
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
    return maybePickCaptureDice(
        snap, botSeat, legality, rng, KillAssistRates.defaults());
  }

  /**
   * Player-count–scaled assist: 2P / 3P / 4P use different probabilities.
   * Target selection stays in {@link #pickBestCaptureDice}.
   */
  static Integer maybePickCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng,
      KillAssistRates rates
  ) {
    Integer best = pickBestCaptureDice(snap, botSeat, legality, rng);
    if (best == null || rng == null) {
      return best;
    }
    KillAssistRates resolved = rates != null ? rates : KillAssistRates.defaults();
    double chance = probabilityForPlayerCount(playerCount(snap), resolved);
    if (chance <= 0.0 || rng.nextDouble() >= chance) {
      return null;
    }
    return best;
  }

  /** Resolve assist probability from seat count (Bot vs Human and Bot vs Bot). */
  static double probabilityForPlayerCount(int playerCount, KillAssistRates rates) {
    KillAssistRates resolved = rates != null ? rates : KillAssistRates.defaults();
    if (playerCount <= 2) {
      return resolved.twoPlayer;
    }
    if (playerCount == 3) {
      return resolved.threePlayer;
    }
    return resolved.fourPlayer;
  }

  private static int playerCount(GameSnapshot snap) {
    if (snap.getSeatColors() != null && !snap.getSeatColors().isEmpty()) {
      return snap.getSeatColors().size();
    }
    if (snap.getIsBot() != null) {
      return snap.getIsBot().length;
    }
    return 4;
  }

  private static double clamp01(double value) {
    if (Double.isNaN(value)) {
      return 0.0;
    }
    return Math.max(0.0, Math.min(1.0, value));
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
