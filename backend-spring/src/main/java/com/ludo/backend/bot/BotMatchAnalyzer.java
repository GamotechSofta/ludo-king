package com.ludo.backend.bot;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Builds {@link BotMatchAnalysis}: mode, phase, leader, comeback, anti-gang-up seat.
 */
@Component
final class BotMatchAnalyzer {

  /** roomId → seat allowed to aggressively hunt the current leader. */
  private final ConcurrentHashMap<String, Integer> leaderHunterByRoom = new ConcurrentHashMap<>();

  BotMatchAnalysis analyze(String roomId, GameSnapshot snap, int botSeat, BotDifficulty difficulty) {
    BotDifficulty diff = difficulty == null ? BotDifficulty.HARD : difficulty;
    boolean[] isBot = snap.getIsBot() != null ? snap.getIsBot().clone() : new boolean[0];
    int humans = 0;
    int bots = 0;
    for (boolean b : isBot) {
      if (b) {
        bots++;
      } else {
        humans++;
      }
    }

    BotAiMode mode = BotMatchAnalysis.detectMode(snap);
    double tableProgress = BotMatchAnalysis.tableProgressRatio(snap);
    BotGamePhase phase = BotMatchAnalysis.detectPhase(snap, tableProgress);

    List<String> colors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    int seats = colors != null ? colors.size() : 0;
    int[] progress = new int[seats];
    int[] finished = new int[seats];
    int[] active = new int[seats];
    int leaderSeat = 0;
    int bestScore = Integer.MIN_VALUE;

    for (int s = 0; s < seats; s++) {
      String name = colors.get(s);
      LudoColor color = BotBoardMath.parseColor(name);
      List<Integer> pos = all != null ? all.get(name) : null;
      int tot = BotBoardMath.totalProgress(color, pos);
      int home = BotBoardMath.countHome(pos);
      int act = BotBoardMath.countActive(pos);
      // Leader score: progress + finished bonus + active presence
      int score = tot + home * 80 + act * 5;
      progress[s] = tot;
      finished[s] = home;
      active[s] = act;
      if (score > bestScore) {
        bestScore = score;
        leaderSeat = s;
      }
    }

    boolean botBehind =
        botSeat >= 0
            && botSeat < progress.length
            && progress[botSeat] + 40 < bestScore;
    boolean botIsLeader = botSeat == leaderSeat;

    boolean allowHunt =
        resolveAntiGangUp(roomId, snap, botSeat, leaderSeat, isBot);

    return new BotMatchAnalysis(
        mode,
        phase,
        diff,
        botSeat,
        humans,
        bots,
        seats,
        isBot,
        leaderSeat,
        progress,
        finished,
        active,
        tableProgress,
        botBehind,
        botIsLeader,
        allowHunt);
  }

  /**
   * At most one bot aggressively hunts the leader. Prefer the bot closest in
   * progress to the leader (self-interest), never coordinated team play.
   */
  private boolean resolveAntiGangUp(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      int leaderSeat,
      boolean[] isBot
  ) {
    if (roomId == null || isBot == null || snap.getSeatColors() == null) {
      return true;
    }
    // Leader is this bot → no hunt needed
    if (botSeat == leaderSeat) {
      leaderHunterByRoom.remove(roomId);
      return false;
    }

    int designated = -1;
    int bestDist = Integer.MAX_VALUE;
    Map<String, List<Integer>> all = snap.getTokenPositions();
    List<String> colors = snap.getSeatColors();
    for (int s = 0; s < isBot.length; s++) {
      if (!isBot[s] || s == leaderSeat) {
        continue;
      }
      LudoColor color = BotBoardMath.parseColor(colors.get(s));
      int prog = BotBoardMath.totalProgress(color, all != null ? all.get(colors.get(s)) : null);
      int leaderProg =
          BotBoardMath.totalProgress(
              BotBoardMath.parseColor(colors.get(leaderSeat)),
              all != null ? all.get(colors.get(leaderSeat)) : null);
      int gap = Math.abs(leaderProg - prog);
      if (gap < bestDist || (gap == bestDist && s < designated)) {
        bestDist = gap;
        designated = s;
      }
    }
    if (designated < 0) {
      return true;
    }
    leaderHunterByRoom.put(roomId, designated);
    return botSeat == designated;
  }
}
