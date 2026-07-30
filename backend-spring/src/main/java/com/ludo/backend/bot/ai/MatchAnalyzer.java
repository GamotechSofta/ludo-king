package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Per-turn match metrics for adaptive difficulty (rank, gaps, finished, danger).
 */
@Component
public class MatchAnalyzer {

  public MatchSnapshot analyze(
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis match,
      OpponentAnalysisReport opponents,
      DangerMap dangerMap
  ) {
    if (snap == null || snap.getSeatColors() == null) {
      return MatchSnapshot.empty(botSeat);
    }
    List<String> colors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    int seats = colors.size();
    int[] progress = new int[seats];
    int[] finished = new int[seats];
    int botProg = 0;
    int botFinished = 0;
    int bestOther = 0;
    int rank = 1;

    for (int s = 0; s < seats; s++) {
      LudoColor c = BotBoardMath.parseColor(colors.get(s));
      List<Integer> pos = all != null ? all.get(colors.get(s)) : null;
      int tot = BotBoardMath.totalProgress(c, pos);
      int home = BotBoardMath.countHome(pos);
      progress[s] = tot;
      finished[s] = home;
      if (s == botSeat) {
        botProg = tot;
        botFinished = home;
      }
    }
    for (int s = 0; s < seats; s++) {
      if (s == botSeat) {
        continue;
      }
      bestOther = Math.max(bestOther, progress[s]);
      if (progress[s] > botProg
          || (progress[s] == botProg && finished[s] > botFinished)) {
        rank++;
      }
    }

    int gap = bestOther - botProg;
    int leaderSeat =
        opponents != null && opponents.enabled()
            ? opponents.currentLeaderSeat()
            : (match != null ? match.leaderSeat : -1);

    int ownDanger = 0;
    if (dangerMap != null && all != null && botSeat < colors.size()) {
      List<Integer> own = all.get(colors.get(botSeat));
      if (own != null) {
        for (Integer p : own) {
          if (p != null) {
            ownDanger = Math.max(ownDanger, dangerMap.dangerAt(p));
          }
        }
      }
    }

    boolean endgameFourth = botFinished >= 3;
    boolean humanDominating =
        match != null
            && match.humanCount >= 1
            && leaderSeat >= 0
            && match.isBot != null
            && leaderSeat < match.isBot.length
            && !match.isBot[leaderSeat]
            && gap >= 40;

    return new MatchSnapshot(
        botSeat,
        rank,
        seats,
        botProg,
        bestOther,
        gap,
        botFinished,
        leaderSeat,
        ownDanger,
        endgameFourth,
        humanDominating,
        match);
  }

  /** Immutable match metrics for one evaluation. */
  public static final class MatchSnapshot {
    public final int botSeat;
    public final int rank;
    public final int playerCount;
    public final int botProgress;
    public final int bestOpponentProgress;
    public final int progressGap;
    public final int finishedPawns;
    public final int leaderSeat;
    public final int maxOwnDanger;
    public final boolean endgameFourth;
    public final boolean humanDominating;
    public final BotMatchAnalysis match;

    MatchSnapshot(
        int botSeat,
        int rank,
        int playerCount,
        int botProgress,
        int bestOpponentProgress,
        int progressGap,
        int finishedPawns,
        int leaderSeat,
        int maxOwnDanger,
        boolean endgameFourth,
        boolean humanDominating,
        BotMatchAnalysis match
    ) {
      this.botSeat = botSeat;
      this.rank = rank;
      this.playerCount = playerCount;
      this.botProgress = botProgress;
      this.bestOpponentProgress = bestOpponentProgress;
      this.progressGap = progressGap;
      this.finishedPawns = finishedPawns;
      this.leaderSeat = leaderSeat;
      this.maxOwnDanger = maxOwnDanger;
      this.endgameFourth = endgameFourth;
      this.humanDominating = humanDominating;
      this.match = match;
    }

    static MatchSnapshot empty(int botSeat) {
      return new MatchSnapshot(botSeat, 1, 2, 0, 0, 0, 0, -1, 0, false, false, null);
    }
  }
}
