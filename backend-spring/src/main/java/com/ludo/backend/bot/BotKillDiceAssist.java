package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Legal capture-die assist only. Probability comes from {@link BotAggressionPolicy}.
 * Never fabricates illegal dice values.
 */
final class BotKillDiceAssist {

  private BotKillDiceAssist() {}

  @FunctionalInterface
  interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  static final class CaptureOpportunity {
    final int dice;
    final VictimInfo victim;
    final int victimRemaining;
    final int victimProgress;
    final boolean victimIsLeader;
    final boolean safeAfter;

    CaptureOpportunity(
        int dice,
        VictimInfo victim,
        int victimRemaining,
        int victimProgress,
        boolean victimIsLeader,
        boolean safeAfter
    ) {
      this.dice = dice;
      this.victim = victim;
      this.victimRemaining = victimRemaining;
      this.victimProgress = victimProgress;
      this.victimIsLeader = victimIsLeader;
      this.safeAfter = safeAfter;
    }
  }

  static Integer maybePickCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng,
      BotMatchAnalysis analysis
  ) {
    CaptureOpportunity best = pickBestOpportunity(snap, botSeat, legality, analysis);
    if (best == null || rng == null || analysis == null) {
      return best != null ? best.dice : null;
    }
    boolean vsHuman = best.victim != null && best.victim.isHuman;
    double chance = BotAggressionPolicy.captureAssistProbability(analysis, vsHuman);
    if (chance <= 0.0 || rng.nextDouble() >= chance) {
      return null;
    }
    return best.dice;
  }

  /**
   * When any human has 2+ pawns home, force a legal kill die in range 1–5 if a
   * human pawn is reachable. Guaranteed (no probability). Returns null otherwise.
   */
  static Integer maybeForceKillDiceWhenHumanHasTwoHome(
      GameSnapshot snap, int botSeat, MoveLegality legality
  ) {
    if (snap == null || legality == null || botSeat < 0) {
      return null;
    }
    if (!humanHasAtLeastTwoHome(snap)) {
      return null;
    }
    CaptureOpportunity best =
        pickBestOpportunityInDiceRange(snap, botSeat, legality, 1, 5, true);
    return best == null ? null : best.dice;
  }

  private static boolean humanHasAtLeastTwoHome(GameSnapshot snap) {
    boolean[] isBot = snap.getIsBot();
    List<String> seatColors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    if (isBot == null || seatColors == null || all == null) {
      return false;
    }
    for (int s = 0; s < seatColors.size(); s++) {
      if (s < isBot.length && isBot[s]) {
        continue;
      }
      if (BotBoardMath.countHome(all.get(seatColors.get(s))) >= 2) {
        return true;
      }
    }
    return false;
  }

  static Integer pickBestCaptureDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng,
      BotMatchAnalysis analysis
  ) {
    CaptureOpportunity best = pickBestOpportunity(snap, botSeat, legality, analysis);
    return best == null ? null : best.dice;
  }

  private static CaptureOpportunity pickBestOpportunity(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      BotMatchAnalysis analysis
  ) {
    List<CaptureOpportunity> ops =
        collectOpportunities(snap, botSeat, legality, 1, 6, false, analysis);
    CaptureOpportunity top = selectBest(ops);
    if (top == null) {
      return null;
    }
    // Anti-gang: if hunting leader human but not designated, pick next non-leader if any
    if (analysis != null
        && top.victimIsLeader
        && top.victim.isHuman
        && !analysis.allowAggressiveLeaderHunt) {
      for (CaptureOpportunity o : ops) {
        if (!o.victimIsLeader) {
          return o;
        }
      }
    }
    return top;
  }

  private static CaptureOpportunity pickBestOpportunityInDiceRange(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      int minDice,
      int maxDice,
      boolean humanVictimsOnly
  ) {
    return selectBest(
        collectOpportunities(snap, botSeat, legality, minDice, maxDice, humanVictimsOnly, null));
  }

  private static CaptureOpportunity selectBest(List<CaptureOpportunity> ops) {
    if (ops == null || ops.isEmpty()) {
      return null;
    }
    // Target priority: leader → closest home → progress → unsafe victim → safe landing
    ops.sort(
        Comparator
            .comparing((CaptureOpportunity o) -> !o.victimIsLeader)
            .thenComparingInt(o -> o.victimRemaining)
            .thenComparing(Comparator.comparingInt((CaptureOpportunity o) -> o.victimProgress).reversed())
            .thenComparing(o -> !o.safeAfter));
    return ops.get(0);
  }

  private static List<CaptureOpportunity> collectOpportunities(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      int minDice,
      int maxDice,
      boolean humanVictimsOnly,
      BotMatchAnalysis analysis
  ) {
    if (snap == null || legality == null || botSeat < 0) {
      return List.of();
    }
    if (snap.getIsBot() == null
        || botSeat >= snap.getIsBot().length
        || !snap.getIsBot()[botSeat]) {
      return List.of();
    }

    List<String> seatColors = snap.getSeatColors();
    if (seatColors == null || botSeat >= seatColors.size()) {
      return List.of();
    }
    String colorName = seatColors.get(botSeat);
    LudoColor color = BotBoardMath.parseColor(colorName);
    List<Integer> own = snap.getTokenPositions().get(colorName);
    Map<String, List<Integer>> all = snap.getTokenPositions();
    if (color == null || own == null || all == null) {
      return List.of();
    }

    boolean[] isBot = analysis != null ? analysis.isBot : snap.getIsBot();
    int leaderSeat = analysis != null ? analysis.leaderSeat : -1;

    List<CaptureOpportunity> ops = new ArrayList<>();
    for (int t = 0; t < own.size(); t++) {
      int from = own.get(t) == null ? JAIL : own.get(t);
      if (isJail(from) || isHome(from)) {
        continue;
      }
      for (int dice = minDice; dice <= maxDice; dice++) {
        if (!legality.canMove(t, dice)) {
          continue;
        }
        int land = BotBoardMath.applySteps(color, from, dice);
        VictimInfo victim = BotBoardMath.findCaptureVictim(botSeat, land, all, seatColors, isBot);
        if (victim == null) {
          continue;
        }
        if (humanVictimsOnly && !victim.isHuman) {
          continue;
        }
        int rem = BotBoardMath.remainingDistance(victim.color, land);
        if (rem == Integer.MAX_VALUE) {
          rem = BotBoardMath.MAX_PAWN_PROGRESS;
        }
        int prog = Math.max(0, BotBoardMath.MAX_PAWN_PROGRESS - rem);
        boolean safe = !BotBoardMath.isPositionThreatened(botSeat, land, all, seatColors);
        ops.add(
            new CaptureOpportunity(
                dice, victim, rem, prog, victim.seat == leaderSeat, safe));
      }
    }
    return ops;
  }
}
