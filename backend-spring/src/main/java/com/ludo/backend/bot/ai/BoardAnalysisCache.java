package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cached per-turn board facts so scoring avoids nested rescans.
 *
 * <p>Built once per bot decision; shared across all {@link MoveCandidate} builds.
 */
public final class BoardAnalysisCache {

  private final LudoColor color;
  private final int botSeat;
  private final List<Integer> own;
  private final Map<String, List<Integer>> all;
  private final List<String> seatColors;
  private final boolean[] isBot;
  private final int leaderSeat;
  private final int activeCount;
  private final int finishedCount;
  private final int bestOwnProgress;
  private final int strongestPawnIndex;
  /** from-cell → threatened? */
  private final Map<Integer, Boolean> threatFromCache = new HashMap<>();
  /** to-cell → distinct attacker seats that can reach in 1–6 */
  private final Map<Integer, Integer> threatCountCache = new HashMap<>();

  private BoardAnalysisCache(
      LudoColor color,
      int botSeat,
      List<Integer> own,
      Map<String, List<Integer>> all,
      List<String> seatColors,
      boolean[] isBot,
      int leaderSeat,
      int activeCount,
      int finishedCount,
      int bestOwnProgress,
      int strongestPawnIndex
  ) {
    this.color = color;
    this.botSeat = botSeat;
    this.own = own;
    this.all = all;
    this.seatColors = seatColors;
    this.isBot = isBot;
    this.leaderSeat = leaderSeat;
    this.activeCount = activeCount;
    this.finishedCount = finishedCount;
    this.bestOwnProgress = bestOwnProgress;
    this.strongestPawnIndex = strongestPawnIndex;
  }

  public static BoardAnalysisCache build(
      GameSnapshot snap,
      int botSeat,
      LudoColor color,
      List<Integer> own,
      BotMatchAnalysis analysis
  ) {
    Map<String, List<Integer>> all = snap.getTokenPositions();
    List<String> colors = snap.getSeatColors();
    boolean[] isBot = analysis != null ? analysis.isBot : snap.getIsBot();
    int leader = analysis != null ? analysis.leaderSeat : -1;

    int active = BotBoardMath.countActive(own);
    int finished = BotBoardMath.countHome(own);
    int bestProg = 0;
    int strongest = 0;
    if (own != null) {
      for (int i = 0; i < own.size(); i++) {
        int pos = own.get(i) == null ? JAIL : own.get(i);
        int p = BotBoardMath.pawnProgress(color, pos);
        if (p > bestProg) {
          bestProg = p;
          strongest = i;
        }
      }
    }
    return new BoardAnalysisCache(
        color, botSeat, own, all, colors, isBot, leader, active, finished, bestProg, strongest);
  }

  public LudoColor color() {
    return color;
  }

  public int botSeat() {
    return botSeat;
  }

  public List<Integer> own() {
    return own;
  }

  public Map<String, List<Integer>> all() {
    return all;
  }

  public List<String> seatColors() {
    return seatColors;
  }

  public boolean[] isBot() {
    return isBot;
  }

  public int leaderSeat() {
    return leaderSeat;
  }

  public int activeCount() {
    return activeCount;
  }

  public int finishedCount() {
    return finishedCount;
  }

  public int bestOwnProgress() {
    return bestOwnProgress;
  }

  public int strongestPawnIndex() {
    return strongestPawnIndex;
  }

  public boolean isThreatened(int pos) {
    if (!isMain(pos) || isSafe(pos)) {
      return false;
    }
    return threatFromCache.computeIfAbsent(
        pos, p -> BotBoardMath.isPositionThreatened(botSeat, p, all, seatColors));
  }

  /** Number of opponent seats that can reach {@code pos} with dice 1–6. */
  public int threatSeatCount(int pos) {
    if (!isMain(pos) || isSafe(pos)) {
      return 0;
    }
    return threatCountCache.computeIfAbsent(pos, this::countThreatSeats);
  }

  private int countThreatSeats(int pos) {
    if (seatColors == null || all == null) {
      return 0;
    }
    int seats = 0;
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == botSeat) {
        continue;
      }
      LudoColor attacker = BotBoardMath.parseColor(seatColors.get(s));
      List<Integer> positions = all.get(seatColors.get(s));
      if (attacker == null || positions == null) {
        continue;
      }
      boolean can = false;
      for (Integer from : positions) {
        if (from == null || !isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (BotBoardMath.applySteps(attacker, from, d) == pos) {
            can = true;
            break;
          }
        }
        if (can) {
          break;
        }
      }
      if (can) {
        seats++;
      }
    }
    return seats;
  }

  public VictimInfo findVictim(int landPos) {
    return BotBoardMath.findCaptureVictim(botSeat, landPos, all, seatColors, isBot);
  }

  public static int pawnValue(LudoColor color, int pos) {
    if (isJail(pos)) {
      return 10;
    }
    if (isHome(pos)) {
      return 200;
    }
    int rem = BotBoardMath.remainingDistance(color, pos);
    if (rem == Integer.MAX_VALUE) {
      return 20;
    }
    if (rem <= 2) {
      return 200;
    }
    if (rem <= HOME_STEPS + 4 || isExit(pos)) {
      return 150;
    }
    int prog = BotBoardMath.pawnProgress(color, pos);
    if (prog >= BotBoardMath.MAX_PAWN_PROGRESS * 0.55) {
      return 100;
    }
    if (prog >= BotBoardMath.MAX_PAWN_PROGRESS * 0.25) {
      return 70;
    }
    if (prog <= 8) {
      return 25;
    }
    return 40;
  }

  public boolean isNearStart(LudoColor victimColor, int landPos) {
    if (victimColor == null || !isMain(landPos)) {
      return false;
    }
    int start = victimColor.startTile();
    int dist = (landPos - start + com.ludo.backend.game.BoardConstants.TOTAL_TILES)
        % com.ludo.backend.game.BoardConstants.TOTAL_TILES;
    return dist <= 6;
  }
}
