package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.exitIndex;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;

import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.Map;

/**
 * Experienced-player style move evaluation. Every legal move gets one comparable
 * score; higher always wins. Difficulty gates depth (lookahead, jail range, noise).
 *
 * <p>Priority bands (high → low): capture → home → smart jail exit → save threat
 * → safe star → future prediction → multi-pawn / progress.
 */
final class BotMoveEvaluator {

  /** Opponent within one die of start after jail exit. */
  static final int JAIL_SETUP_IMMEDIATE_MAX = 6;
  /** Two-roll window (HARD). */
  static final int JAIL_SETUP_NEAR_MAX = 12;

  // --- Lexicographic-style bands (must stay ordered) ---
  static final long BAND_CAPTURE = 500_000_000L;
  static final long BAND_HOME = 200_000_000L;
  static final long BAND_JAIL_SETUP_IMMEDIATE = 150_000_000L;
  static final long BAND_JAIL_SETUP_NEAR = 120_000_000L;
  static final long BAND_JAIL_MULTI_PAWN = 110_000_000L;
  static final long BAND_JAIL_NO_ATTACK = 95_000_000L;
  static final long BAND_SAVE_THREAT = 40_000_000L;
  static final long BAND_SAFE_STAR = 5_000_000L;
  static final long BAND_ENTER_EXIT = 2_000_000L;
  static final long BAND_FUTURE_MAX = 1_500_000L;
  static final long BAND_BLOCK = 50_000L;
  static final long BAND_PROGRESS_MAX = 20_000L;
  static final long JAIL_EXIT_BASE = 120L;

  private BotMoveEvaluator() {}

  static final class Context {
    final LudoColor color;
    final int seat;
    final List<Integer> ownPositions;
    final Map<String, List<Integer>> allPositions;
    final List<String> seatColors;
    final BotDifficulty difficulty;
    final int activeCount;
    final int jailCount;
    final boolean hasAttackOpportunity;

    Context(
        LudoColor color,
        int seat,
        List<Integer> ownPositions,
        Map<String, List<Integer>> allPositions,
        List<String> seatColors,
        BotDifficulty difficulty
    ) {
      this.color = color;
      this.seat = seat;
      this.ownPositions = ownPositions;
      this.allPositions = allPositions;
      this.seatColors = seatColors;
      this.difficulty = difficulty == null ? BotDifficulty.HARD : difficulty;
      this.activeCount = countActive(ownPositions);
      this.jailCount = countJail(ownPositions);
      this.hasAttackOpportunity =
          hasAnyAttackOpportunity(color, seat, ownPositions, allPositions, seatColors);
    }

    boolean isEasy() {
      return difficulty == BotDifficulty.EASY;
    }

    boolean isMedium() {
      return difficulty == BotDifficulty.MEDIUM;
    }

    boolean isHard() {
      return difficulty == BotDifficulty.HARD;
    }

    /** Jail capture-setup look-ahead distance. */
    int jailLookaheadMax() {
      if (isEasy()) {
        return JAIL_SETUP_IMMEDIATE_MAX;
      }
      if (isMedium()) {
        return JAIL_SETUP_IMMEDIATE_MAX;
      }
      return JAIL_SETUP_NEAR_MAX;
    }

    boolean useFuturePrediction() {
      return isHard();
    }

    boolean useFullSaveThreat() {
      return !isEasy();
    }

    boolean useSmartJail() {
      return !isEasy();
    }
  }

  /**
   * Full strategic value for one legal move (including captures).
   */
  static long scoreMove(
      Context ctx,
      int token,
      int from,
      int to,
      int dice
  ) {
    long score = 0;

    VictimInfo victim = findCaptureVictim(ctx.seat, to, ctx.allPositions, ctx.seatColors);
    if (victim != null) {
      score += scoreCapture(ctx, from, to, victim);
    }

    if (isHome(to)) {
      score += BAND_HOME;
    }

    if (isJail(from) && dice == 6) {
      score += scoreJailExit(ctx);
    }

    if (ctx.useFullSaveThreat()
        && isMain(from)
        && isPositionThreatened(ctx.seat, from, ctx.allPositions, ctx.seatColors)
        && !isPositionThreatened(ctx.seat, to, ctx.allPositions, ctx.seatColors)) {
      score += BAND_SAVE_THREAT;
      // Prefer saving a pawn closer to home (more valuable)
      int rem = remainingDistance(ctx.color, from);
      if (rem != Integer.MAX_VALUE) {
        score += Math.max(0, 500 - rem);
      }
    }

    // Safe star / start / exit lane / home landing
    if (isSafe(to)) {
      score += BAND_SAFE_STAR;
    } else if (isExit(to) || isHome(to)) {
      score += BAND_SAFE_STAR / 2;
    }

    if (isMain(from) && isExit(to)) {
      score += BAND_ENTER_EXIT;
    }

    // Prefer not landing in danger (HARD/MEDIUM strong; EASY mild)
    boolean landSafe = !isPositionThreatened(ctx.seat, to, ctx.allPositions, ctx.seatColors);
    if (landSafe) {
      score += ctx.isEasy() ? 5_000L : 100_000L;
    } else if (ctx.isHard()) {
      score -= 250_000L;
    } else if (ctx.isMedium()) {
      score -= 80_000L;
    }

    int ownOnDest = countOwnOnCell(ctx.ownPositions, token, to);
    if (isMain(to) && ownOnDest >= 1) {
      score += BAND_BLOCK;
    }
    int ownOnFrom = countOwnOnCell(ctx.ownPositions, -1, from);
    if (!(isMain(from) && ownOnFrom >= 2)) {
      score += BAND_BLOCK / 50;
    }

    if (ctx.useFuturePrediction()) {
      score += scoreFuture(ctx, to);
    } else if (ctx.isMedium()) {
      // Light one-die capture peek
      score += Math.min(BAND_FUTURE_MAX / 3, scoreFuture(ctx, to) / 3);
    }

    int remainingAfter = remainingDistance(ctx.color, to);
    if (remainingAfter != Integer.MAX_VALUE) {
      int progress = Math.max(0, TOTAL_TILES + HOME_STEPS - remainingAfter);
      score += Math.min(BAND_PROGRESS_MAX, progress * 50L);
      // Prefer finishing the furthest-ahead pawn when scores are close
      score += Math.min(2_000L, progress);
    } else if (isJail(from)) {
      score += 10L;
    }

    return score;
  }

  private static long scoreCapture(Context ctx, int from, int to, VictimInfo victim) {
    long score = BAND_CAPTURE;
    int victimRemaining = remainingDistance(victim.color, to);
    if (victimRemaining == Integer.MAX_VALUE) {
      victimRemaining = TOTAL_TILES + HOME_STEPS;
    }
    // Closer to home → higher (invert remaining into 0..10k)
    score += Math.max(0, 10_000 - victimRemaining * 20L);
    int victimProgress = Math.max(0, TOTAL_TILES + HOME_STEPS - victimRemaining);
    score += victimProgress;

    int botFromRem = remainingDistance(ctx.color, from);
    int botToRem = remainingDistance(ctx.color, to);
    if (botFromRem != Integer.MAX_VALUE && botToRem != Integer.MAX_VALUE) {
      score += Math.max(0, botFromRem - botToRem);
    }

    // Prefer captures that leave us safe
    if (!isPositionThreatened(ctx.seat, to, ctx.allPositions, ctx.seatColors)) {
      score += 50_000L;
    } else if (ctx.isHard()) {
      score -= 15_000L;
    }
    return score;
  }

  private static long scoreJailExit(Context ctx) {
    long score = JAIL_EXIT_BASE;
    if (!ctx.useSmartJail()) {
      // EASY: mild preference to develop when empty board
      if (ctx.activeCount == 0) {
        score += 1_000L;
      }
      return score;
    }

    int nearest =
        nearestCapturableOpponentAhead(
            ctx.color, ctx.seat, ctx.allPositions, ctx.seatColors);
    int look = ctx.jailLookaheadMax();

    if (nearest >= 1 && nearest <= JAIL_SETUP_IMMEDIATE_MAX) {
      score += BAND_JAIL_SETUP_IMMEDIATE;
      score += (JAIL_SETUP_IMMEDIATE_MAX - nearest + 1) * 100L;
    } else if (nearest >= 1 && nearest <= look) {
      score += BAND_JAIL_SETUP_NEAR;
      score += (look - nearest + 1) * 50L;
    }

    // Need a second (or first) pawn for board control
    if (ctx.activeCount <= 1 && ctx.jailCount >= 1) {
      score += BAND_JAIL_MULTI_PAWN;
      if (ctx.activeCount == 0) {
        score += 5_000L;
      } else {
        // Exactly one active — strongly prefer opening a second
        score += 8_000L;
      }
    }

    // No current attacking chances with pieces already out
    if (ctx.activeCount >= 1 && !ctx.hasAttackOpportunity) {
      score += BAND_JAIL_NO_ATTACK;
    }

    return score;
  }

  /**
   * Next 1–2 dice of opportunity from the landing square (HARD).
   */
  private static long scoreFuture(Context ctx, int to) {
    if (isJail(to) || isHome(to)) {
      return 0;
    }
    long score = 0;
    // One-ply: can we capture with 1..6 from here?
    for (int d = 1; d <= 6; d++) {
      int land = applySteps(ctx.color, to, d);
      VictimInfo v = findCaptureVictim(ctx.seat, land, ctx.allPositions, ctx.seatColors);
      if (v != null) {
        int rem = remainingDistance(v.color, land);
        if (rem == Integer.MAX_VALUE) {
          rem = TOTAL_TILES + HOME_STEPS;
        }
        score += 80_000L + Math.max(0, 6_000 - rem * 10L) - d * 500L;
      }
      if (isHome(land)) {
        score += 40_000L - d * 200L;
      }
      if (isSafe(land)) {
        score += 2_000L;
      }
    }
    // Two-ply sketch: after a hypothetical safe step of 3 (average die), peek again
    if (ctx.isHard() && isMain(to)) {
      int mid = applySteps(ctx.color, to, 3);
      if (!isHome(mid) && !isJail(mid)) {
        for (int d = 1; d <= 6; d++) {
          int land = applySteps(ctx.color, mid, d);
          if (findCaptureVictim(ctx.seat, land, ctx.allPositions, ctx.seatColors) != null) {
            score += 12_000L;
            break;
          }
        }
      }
    }
    return Math.min(BAND_FUTURE_MAX, score);
  }

  static int countActive(List<Integer> ownPositions) {
    if (ownPositions == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : ownPositions) {
      int pos = p == null ? JAIL : p;
      if (!isJail(pos) && !isHome(pos)) {
        n++;
      }
    }
    return n;
  }

  static int countJail(List<Integer> ownPositions) {
    if (ownPositions == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : ownPositions) {
      int pos = p == null ? JAIL : p;
      if (isJail(pos)) {
        n++;
      }
    }
    return n;
  }

  /** True if any active own pawn can capture someone with dice 1–6. */
  static boolean hasAnyAttackOpportunity(
      LudoColor color,
      int seat,
      List<Integer> ownPositions,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (ownPositions == null) {
      return false;
    }
    for (Integer fromObj : ownPositions) {
      int from = fromObj == null ? JAIL : fromObj;
      if (isJail(from) || isHome(from)) {
        continue;
      }
      for (int d = 1; d <= 6; d++) {
        int land = applySteps(color, from, d);
        if (findCaptureVictim(seat, land, allPositions, seatColors) != null) {
          return true;
        }
      }
    }
    return false;
  }

  static int nearestCapturableOpponentAhead(
      LudoColor color,
      int seat,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (allPositions == null || seatColors == null) {
      return -1;
    }
    int start = color.startTile();
    int best = Integer.MAX_VALUE;
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == seat) {
        continue;
      }
      String name = seatColors.get(s);
      List<Integer> positions = allPositions.get(name);
      if (positions == null) {
        continue;
      }
      for (Integer posObj : positions) {
        if (posObj == null) {
          continue;
        }
        int pos = posObj;
        if (!isMain(pos) || isSafe(pos)) {
          continue;
        }
        int stacked = 0;
        for (Integer p : positions) {
          if (p != null && p == pos) {
            stacked++;
          }
        }
        if (stacked != 1) {
          continue;
        }
        int ahead = stepsAheadOnMain(start, pos);
        if (ahead >= 1 && ahead < best) {
          best = ahead;
        }
      }
    }
    return best == Integer.MAX_VALUE ? -1 : best;
  }

  static int stepsAheadOnMain(int from, int to) {
    if (!isMain(from) || !isMain(to)) {
      return -1;
    }
    return (to - from + TOTAL_TILES) % TOTAL_TILES;
  }

  static int countOwnOnCell(List<Integer> ownPositions, int excludeToken, int cell) {
    if (ownPositions == null || isJail(cell) || isHome(cell)) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < ownPositions.size(); i++) {
      if (i == excludeToken) {
        continue;
      }
      Integer p = ownPositions.get(i);
      if (p != null && p == cell) {
        count++;
      }
    }
    return count;
  }

  static boolean isPositionThreatened(
      int defenderSeat,
      int pos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (!isMain(pos) || isSafe(pos) || allPositions == null || seatColors == null) {
      return false;
    }
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == defenderSeat) {
        continue;
      }
      String name = seatColors.get(s);
      LudoColor attacker;
      try {
        attacker = LudoColor.valueOf(name);
      } catch (RuntimeException ignored) {
        continue;
      }
      List<Integer> positions = allPositions.get(name);
      if (positions == null) {
        continue;
      }
      for (Integer from : positions) {
        if (from == null || !isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (applySteps(attacker, from, d) == pos) {
            return true;
          }
        }
      }
    }
    return false;
  }

  static final class VictimInfo {
    final LudoColor color;

    VictimInfo(LudoColor color) {
      this.color = color;
    }
  }

  static VictimInfo findCaptureVictim(
      int moverSeat,
      int landPos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (!isMain(landPos) || isSafe(landPos) || allPositions == null || seatColors == null) {
      return null;
    }
    VictimInfo best = null;
    int bestRemaining = Integer.MAX_VALUE;
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == moverSeat) {
        continue;
      }
      String c = seatColors.get(s);
      List<Integer> positions = allPositions.get(c);
      if (positions == null) {
        continue;
      }
      int n = 0;
      for (Integer p : positions) {
        if (p != null && p == landPos) {
          n++;
        }
      }
      if (n != 1) {
        continue;
      }
      LudoColor victimColor;
      try {
        victimColor = LudoColor.valueOf(c);
      } catch (RuntimeException ignored) {
        continue;
      }
      int rem = remainingDistance(victimColor, landPos);
      if (rem == Integer.MAX_VALUE) {
        rem = TOTAL_TILES + HOME_STEPS;
      }
      if (best == null || rem < bestRemaining) {
        best = new VictimInfo(victimColor);
        bestRemaining = rem;
      }
    }
    return best;
  }

  static int remainingDistance(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return Integer.MAX_VALUE;
    }
    if (isExit(pos)) {
      return HOME_STEPS - 1 - exitIndex(pos);
    }
    int toExit = (color.exitTile() - pos + TOTAL_TILES) % TOTAL_TILES;
    return toExit + HOME_STEPS;
  }

  static int applySteps(LudoColor color, int from, int steps) {
    if (isJail(from)) {
      return color.startTile();
    }
    int pos = from;
    for (int i = 0; i < steps; i++) {
      if (isMain(pos)) {
        if (pos == color.exitTile()) {
          pos = toExit(0);
        } else {
          pos = (pos + 1) % TOTAL_TILES;
        }
      } else if (isExit(pos)) {
        int idx = exitIndex(pos);
        if (idx >= EXIT_LEN - 1) {
          pos = HOME;
        } else {
          pos = toExit(idx + 1);
        }
      } else {
        break;
      }
    }
    return pos;
  }
}
