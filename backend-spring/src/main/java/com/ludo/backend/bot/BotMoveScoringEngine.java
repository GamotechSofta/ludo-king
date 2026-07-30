package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.Map;

/**
 * Production move scoring. Base weights match the design doc; phase / comeback /
 * anti-gang / risk adjust them. Always pick the highest score among legal moves.
 */
final class BotMoveScoringEngine {

  // Spec base weights (scaled ×1_000_000). Home finish is lexicographically above captures.
  static final long REACH_HOME = 250_000_000L;
  static final long FINISH_PAWN = 90_000_000L;
  static final long CAPTURE_LEADER = 80_000_000L;
  static final long CAPTURE_ADVANCED = 70_000_000L;
  static final long REACH_SAFE = 60_000_000L;
  static final long ESCAPE_CAPTURE = 50_000_000L;
  static final long RELEASE_PAWN = 40_000_000L;
  static final long ADVANCE_STRONG = 30_000_000L;
  static final long AVOID_DANGER = 20_000_000L;
  static final long INTO_DANGER = -40_000_000L;
  static final long LOSE_SAFE = -50_000_000L;
  static final long RISK_CAPTURE = -60_000_000L;

  private BotMoveScoringEngine() {}

  static long scoreMove(
      BotMatchAnalysis analysis,
      LudoColor color,
      int seat,
      List<Integer> ownPositions,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors,
      int token,
      int from,
      int to,
      int dice
  ) {
    if (analysis != null && analysis.hardDynamic()) {
      return scoreDynamic(analysis, color, seat, ownPositions, allPositions, seatColors, token, from, to, dice);
    }
    return scoreBaseline(analysis, color, seat, ownPositions, allPositions, seatColors, from, to, dice);
  }

  private static long scoreDynamic(
      BotMatchAnalysis a,
      LudoColor color,
      int seat,
      List<Integer> own,
      Map<String, List<Integer>> all,
      List<String> colors,
      int token,
      int from,
      int to,
      int dice
  ) {
    long score = 0;
    BotGamePhase phase = a.phase;
    boolean comeback = a.botBehind;
    int active = BotBoardMath.countActive(own);

    // Reach Home / Finish pawn
    if (isHome(to)) {
      score += REACH_HOME;
      if (isExit(from)) {
        score += FINISH_PAWN;
      }
      if (phase == BotGamePhase.END || comeback) {
        score += 25_000_000L;
      }
    }

    VictimInfo victim = BotBoardMath.findCaptureVictim(seat, to, all, colors, a.isBot);
    if (victim != null) {
      score += scoreCapture(a, color, from, to, victim, phase, comeback);
    }

    // Escape capture
    boolean fromThreatened =
        isMain(from) && BotBoardMath.isPositionThreatened(seat, from, all, colors);
    boolean toThreatened = BotBoardMath.isPositionThreatened(seat, to, all, colors);
    if (fromThreatened && !toThreatened) {
      long esc = ESCAPE_CAPTURE;
      if (phase == BotGamePhase.END || comeback) {
        esc += 15_000_000L;
      }
      // Prefer saving advanced pawns
      int rem = BotBoardMath.remainingDistance(color, from);
      if (rem != Integer.MAX_VALUE) {
        esc += Math.max(0, 8_000_000L - rem * 50_000L);
      }
      score += esc;
    }

    // Safe cell / star
    if (isSafe(to)) {
      long safe = REACH_SAFE;
      if (phase == BotGamePhase.MID || phase == BotGamePhase.END || comeback) {
        safe += 10_000_000L;
      }
      if (phase == BotGamePhase.EARLY) {
        safe = safe / 2 + 5_000_000L;
      }
      score += safe;
    }

    // Risk analysis
    if (toThreatened && !isHome(to) && !isSafe(to)) {
      score += INTO_DANGER;
      score += RISK_CAPTURE;
      if (phase == BotGamePhase.END) {
        score -= 20_000_000L;
      }
    } else if (!toThreatened && isMain(to)) {
      score += AVOID_DANGER;
    }

    if (isSafe(from) && !isSafe(to) && toThreatened) {
      score += LOSE_SAFE;
    }

    // Release / open pawns (smart opening: maintain 2–3 active)
    if (isJail(from) && dice == 6) {
      long release = RELEASE_PAWN;
      if (phase == BotGamePhase.EARLY) {
        release += 20_000_000L;
        if (active == 0) {
          release += 15_000_000L;
        } else if (active < 3) {
          release += 10_000_000L;
        }
      } else if (phase == BotGamePhase.MID && active < 3) {
        release += 15_000_000L;
      } else if (phase == BotGamePhase.END && active >= 2) {
        release -= 10_000_000L; // prefer finishing
      }
      // Don't spam same pawn — soft penalty if already many active of one style
      score += release;
    }

    // Advance strongest pawn / home path
    int remFrom = BotBoardMath.remainingDistance(color, from);
    int remTo = BotBoardMath.remainingDistance(color, to);
    if (remFrom != Integer.MAX_VALUE && remTo != Integer.MAX_VALUE && remTo < remFrom) {
      long adv = ADVANCE_STRONG;
      int progress = BotBoardMath.pawnProgress(color, from);
      // Strongest = highest progress
      int bestOwn = bestOwnProgress(color, own);
      if (progress >= bestOwn - 2) {
        adv += 12_000_000L;
      }
      if (phase == BotGamePhase.END) {
        adv += 20_000_000L;
        if (isExit(from) || isExit(to)) {
          adv += 25_000_000L; // protect / push home path
        }
      }
      if (phase == BotGamePhase.EARLY) {
        adv = adv / 2;
      }
      score += adv;
      score += (remFrom - remTo) * 100_000L;
    }

    // EARLY: avoid aggressive human targeting
    if (phase == BotGamePhase.EARLY && victim != null && victim.isHuman) {
      score -= 35_000_000L;
    }

    // END: ignore unnecessary fights (weak captures far from race)
    if (phase == BotGamePhase.END
        && victim != null
        && !isHome(to)
        && (isExit(from) || remFrom != Integer.MAX_VALUE && remFrom <= HOME_STEPS + 8)) {
      int vRem = BotBoardMath.remainingDistance(victim.color, to);
      if (vRem == Integer.MAX_VALUE || vRem > HOME_STEPS + 20) {
        score -= 40_000_000L;
      }
    }

    // Spread: mild bonus for moving a less-advanced pawn in EARLY/MID when opening
    if ((phase == BotGamePhase.EARLY || phase == BotGamePhase.MID)
        && !isJail(from)
        && remFrom != Integer.MAX_VALUE) {
      int best = bestOwnProgress(color, own);
      int mine = BotBoardMath.pawnProgress(color, from);
      if (active >= 2 && mine < best - 10) {
        score += 3_000_000L;
      }
    }

    // Tie-break noise only in EARLY for natural feel
    if (phase == BotGamePhase.EARLY) {
      score += (token * 31L + from * 7L + to) % 500_000L;
    }

    return score;
  }

  private static long scoreCapture(
      BotMatchAnalysis a,
      LudoColor color,
      int from,
      int to,
      VictimInfo victim,
      BotGamePhase phase,
      boolean comeback
  ) {
    int vRem = BotBoardMath.remainingDistance(victim.color, to);
    if (vRem == Integer.MAX_VALUE) {
      vRem = BotBoardMath.MAX_PAWN_PROGRESS;
    }
    int vProg = Math.max(0, BotBoardMath.MAX_PAWN_PROGRESS - vRem);
    boolean isLeader = victim.seat == a.leaderSeat;
    boolean advanced = vRem <= HOME_STEPS + 16 || vProg >= BotBoardMath.MAX_PAWN_PROGRESS * 0.45;

    long cap;
    if (isLeader) {
      cap = CAPTURE_LEADER;
      if (!a.allowAggressiveLeaderHunt && victim.isHuman) {
        // Anti-gang-up: non-designated bots don't pile on the leader
        cap = CAPTURE_ADVANCED / 2;
      } else if (a.allowAggressiveLeaderHunt) {
        cap += 15_000_000L;
      }
    } else if (advanced) {
      cap = CAPTURE_ADVANCED;
    } else {
      cap = CAPTURE_ADVANCED / 2;
    }

    // Target priority: closest to home, then progress, outside safe already required
    cap += Math.max(0, 20_000_000L - vRem * 150_000L);
    cap += vProg * 1_000L;

    double mult = BotAggressionPolicy.captureScoreMultiplier(a, victim.isHuman);
    cap = (long) (cap * mult);

    if (phase == BotGamePhase.EARLY) {
      cap = (long) (cap * 0.35);
    } else if (phase == BotGamePhase.MID) {
      cap = (long) (cap * 1.05);
    } else {
      cap = (long) (cap * 1.15);
    }

    if (comeback) {
      cap += 20_000_000L;
      if (isLeader) {
        cap += 15_000_000L;
      }
    }

    // Prefer self-interest over bot-vs-bot side fights
    if (!victim.isHuman && a.botCount > 1) {
      cap = (long) (cap * 0.85);
    }

    return cap;
  }

  private static int bestOwnProgress(LudoColor color, List<Integer> own) {
    if (own == null) {
      return 0;
    }
    int best = 0;
    for (Integer p : own) {
      int pos = p == null ? com.ludo.backend.game.BoardConstants.JAIL : p;
      best = Math.max(best, BotBoardMath.pawnProgress(color, pos));
    }
    return best;
  }

  private static long scoreBaseline(
      BotMatchAnalysis a,
      LudoColor color,
      int seat,
      List<Integer> own,
      Map<String, List<Integer>> all,
      List<String> colors,
      int from,
      int to,
      int dice
  ) {
    long score = 0;
    if (isHome(to)) {
      score += 50_000L;
    }
    boolean[] isBot = a != null ? a.isBot : null;
    VictimInfo v = BotBoardMath.findCaptureVictim(seat, to, all, colors, isBot);
    if (v != null) {
      score += 25_000L;
    }
    if (isJail(from) && dice == 6) {
      score += 8_000L;
    }
    if (isSafe(to)) {
      score += 5_000L;
    }
    if (BotBoardMath.isPositionThreatened(seat, to, all, colors)) {
      score -= (a != null && a.difficulty == BotDifficulty.EASY) ? 500L : 8_000L;
    }
    int rem = BotBoardMath.remainingDistance(color, to);
    if (rem != Integer.MAX_VALUE) {
      score += Math.max(0, BotBoardMath.MAX_PAWN_PROGRESS - rem);
    }
    return score;
  }
}
