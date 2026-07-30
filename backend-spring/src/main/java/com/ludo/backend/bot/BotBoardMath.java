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
import java.util.List;
import java.util.Map;

/** Shared board math for AI (legal path only — mirrors engine rules). */
public final class BotBoardMath {

  /** Max progress for one pawn (main path + home column). */
  public static final int MAX_PAWN_PROGRESS = TOTAL_TILES + HOME_STEPS;

  private BotBoardMath() {}

  public static int countActive(List<Integer> positions) {
    if (positions == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : positions) {
      int pos = p == null ? JAIL : p;
      if (!isJail(pos) && !isHome(pos)) {
        n++;
      }
    }
    return n;
  }

  public static int countJail(List<Integer> positions) {
    if (positions == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : positions) {
      int pos = p == null ? JAIL : p;
      if (isJail(pos)) {
        n++;
      }
    }
    return n;
  }

  public static int countHome(List<Integer> positions) {
    if (positions == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : positions) {
      int pos = p == null ? JAIL : p;
      if (isHome(pos)) {
        n++;
      }
    }
    return n;
  }

  public static int remainingDistance(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return Integer.MAX_VALUE;
    }
    if (isExit(pos)) {
      return HOME_STEPS - 1 - exitIndex(pos);
    }
    int toExit = (color.exitTile() - pos + TOTAL_TILES) % TOTAL_TILES;
    return toExit + HOME_STEPS;
  }

  /** 0 = jail / unknown; finished = max. */
  public static int pawnProgress(LudoColor color, int pos) {
    if (isJail(pos)) {
      return 0;
    }
    if (isHome(pos)) {
      return MAX_PAWN_PROGRESS;
    }
    int rem = remainingDistance(color, pos);
    if (rem == Integer.MAX_VALUE) {
      return 0;
    }
    return Math.max(0, MAX_PAWN_PROGRESS - rem);
  }

  public static int totalProgress(LudoColor color, List<Integer> positions) {
    if (positions == null || color == null) {
      return 0;
    }
    int sum = 0;
    for (Integer p : positions) {
      sum += pawnProgress(color, p == null ? JAIL : p);
    }
    return sum;
  }

  public static double progressRatio(LudoColor color, List<Integer> positions) {
    int max = MAX_PAWN_PROGRESS * 4;
    if (max <= 0) {
      return 0;
    }
    return Math.min(1.0, totalProgress(color, positions) / (double) max);
  }

  public static boolean isNearHome(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return false;
    }
    if (isExit(pos)) {
      return true;
    }
    int rem = remainingDistance(color, pos);
    return rem != Integer.MAX_VALUE && rem <= HOME_STEPS + 10;
  }

  public static int countNearHome(LudoColor color, List<Integer> positions) {
    if (positions == null || color == null) {
      return 0;
    }
    int n = 0;
    for (Integer p : positions) {
      if (isNearHome(color, p == null ? JAIL : p)) {
        n++;
      }
    }
    return n;
  }

  public static boolean isPositionThreatened(
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
      LudoColor attacker = parseColor(seatColors.get(s));
      if (attacker == null) {
        continue;
      }
      List<Integer> positions = allPositions.get(seatColors.get(s));
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

  public static final class VictimInfo {
    public final LudoColor color;
    public final int seat;
    public final boolean isHuman;

    public VictimInfo(LudoColor color, int seat, boolean isHuman) {
      this.color = color;
      this.seat = seat;
      this.isHuman = isHuman;
    }
  }

  public static VictimInfo findCaptureVictim(
      int moverSeat,
      int landPos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors,
      boolean[] isBot
  ) {
    if (!isMain(landPos) || isSafe(landPos) || allPositions == null || seatColors == null) {
      return null;
    }
    VictimInfo best = null;
    int bestRem = Integer.MAX_VALUE;
    int bestProg = -1;
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
      LudoColor victimColor = parseColor(c);
      if (victimColor == null) {
        continue;
      }
      boolean human = isBot == null || s >= isBot.length || !isBot[s];
      int rem = remainingDistance(victimColor, landPos);
      if (rem == Integer.MAX_VALUE) {
        rem = MAX_PAWN_PROGRESS;
      }
      int prog = Math.max(0, MAX_PAWN_PROGRESS - rem);
      if (best == null || rem < bestRem || (rem == bestRem && prog > bestProg)) {
        best = new VictimInfo(victimColor, s, human);
        bestRem = rem;
        bestProg = prog;
      }
    }
    return best;
  }

  public static int applySteps(LudoColor color, int from, int steps) {
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

  public static LudoColor parseColor(String name) {
    if (name == null) {
      return null;
    }
    try {
      return LudoColor.valueOf(name);
    } catch (RuntimeException ex) {
      return null;
    }
  }
}
