package com.ludo.backend.bot;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.Map;

/** Mode / phase / leader snapshot for one bot decision. */
public final class BotMatchAnalysis {

  public final BotAiMode mode;
  public final BotGamePhase phase;
  public final BotDifficulty difficulty;
  public final int botSeat;
  public final int humanCount;
  public final int botCount;
  public final int playerCount;
  public final boolean[] isBot;
  public final int leaderSeat;
  public final int[] seatProgress;
  public final int[] finishedPawns;
  public final int[] activePawns;
  public final double tableProgress;
  public final boolean botBehind;
  public final boolean botIsLeader;
  public final boolean allowAggressiveLeaderHunt;

  public BotMatchAnalysis(
      BotAiMode mode,
      BotGamePhase phase,
      BotDifficulty difficulty,
      int botSeat,
      int humanCount,
      int botCount,
      int playerCount,
      boolean[] isBot,
      int leaderSeat,
      int[] seatProgress,
      int[] finishedPawns,
      int[] activePawns,
      double tableProgress,
      boolean botBehind,
      boolean botIsLeader,
      boolean allowAggressiveLeaderHunt
  ) {
    this.mode = mode;
    this.phase = phase;
    this.difficulty = difficulty;
    this.botSeat = botSeat;
    this.humanCount = humanCount;
    this.botCount = botCount;
    this.playerCount = playerCount;
    this.isBot = isBot;
    this.leaderSeat = leaderSeat;
    this.seatProgress = seatProgress;
    this.finishedPawns = finishedPawns;
    this.activePawns = activePawns;
    this.tableProgress = tableProgress;
    this.botBehind = botBehind;
    this.botIsLeader = botIsLeader;
    this.allowAggressiveLeaderHunt = allowAggressiveLeaderHunt;
  }

  public boolean hardDynamic() {
    return difficulty == BotDifficulty.HARD && mode != BotAiMode.OTHER;
  }

  public static BotAiMode detectMode(GameSnapshot snap) {
    if (snap == null || snap.getIsBot() == null) {
      return BotAiMode.OTHER;
    }
    boolean[] isBot = snap.getIsBot();
    int bots = 0;
    int humans = 0;
    for (boolean b : isBot) {
      if (b) {
        bots++;
      } else {
        humans++;
      }
    }
    int n = isBot.length;
    if (n == 2 && humans == 1 && bots == 1) {
      return BotAiMode.MODE_1;
    }
    if (n == 4 && humans == 1 && bots == 3) {
      return BotAiMode.MODE_2;
    }
    if (n == 4 && humans == 2 && bots == 2) {
      return BotAiMode.MODE_3;
    }
    if (n == 4 && humans == 3 && bots == 1) {
      return BotAiMode.MODE_4;
    }
    return BotAiMode.OTHER;
  }

  public static BotGamePhase detectPhase(GameSnapshot snap, double tableProgress) {
    if (snap == null || snap.getSeatColors() == null || snap.getTokenPositions() == null) {
      return BotGamePhase.EARLY;
    }
    Map<String, List<Integer>> all = snap.getTokenPositions();
    int jail = 0;
    int active = 0;
    int nearHome = 0;
    for (String c : snap.getSeatColors()) {
      LudoColor color = BotBoardMath.parseColor(c);
      List<Integer> pos = all.get(c);
      jail += BotBoardMath.countJail(pos);
      active += BotBoardMath.countActive(pos);
      nearHome += BotBoardMath.countNearHome(color, pos);
    }
    int seats = snap.getSeatColors().size();
    int tokens = seats * 4;

    // EARLY: <25% progress, most in jail
    if (tableProgress < 0.25 || (jail >= tokens * 0.6 && active <= seats)) {
      return BotGamePhase.EARLY;
    }
    // END: >70% or several near home
    if (tableProgress > 0.70 || nearHome >= Math.max(3, seats)) {
      return BotGamePhase.END;
    }
    return BotGamePhase.MID;
  }

  public static double tableProgressRatio(GameSnapshot snap) {
    if (snap == null || snap.getSeatColors() == null || snap.getTokenPositions() == null) {
      return 0;
    }
    double sum = 0;
    int n = 0;
    for (String c : snap.getSeatColors()) {
      LudoColor color = BotBoardMath.parseColor(c);
      sum += BotBoardMath.progressRatio(color, snap.getTokenPositions().get(c));
      n++;
    }
    return n == 0 ? 0 : sum / n;
  }
}
