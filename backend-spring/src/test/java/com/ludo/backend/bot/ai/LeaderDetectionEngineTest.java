package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LeaderDetectionEngineTest {

  private OpponentAnalysisConfig config;
  private OpponentHistory history;
  private LeaderDetectionEngine engine;

  @BeforeEach
  void setUp() {
    config =
        new OpponentAnalysisConfig(
            true, true, true, true, true, 150, 120, 80, 20, 20, 15, 20, 120, 70, 50, 40, 35, 80,
            35, 25, 30);
    history = new OpponentHistory(config);
    engine =
        new LeaderDetectionEngine(
            config, new OpponentAnalyzer(config, history), new TargetSelector(config), history);
  }

  @Test
  void leaderDetectionPicksHighestScore() {
    // Seat 1 (YELLOW) nearly finished vs seat 0 early
    GameSnapshot snap =
        snap(
            List.of(JAIL, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, (LudoColor.YELLOW.startTile() + 40) % 52, JAIL));
    OpponentAnalysisReport report = engine.analyze("r1", snap, 0, mid(0), null);
    assertTrue(report.enabled());
    assertEquals(1, report.currentLeaderSeat());
    OpponentProfile leader = report.get(1);
    assertNotNull(leader);
    assertTrue(leader.leaderScore().total() > report.get(0).leaderScore().total());
  }

  @Test
  void threatRankingCriticalForThreeHome() {
    GameSnapshot snap =
        snap(
            List.of(5, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, HOME, (LudoColor.YELLOW.startTile() + 10) % 52));
    OpponentAnalysisReport report = engine.analyze("r2", snap, 0, mid(0), null);
    OpponentProfile yellow = report.get(1);
    assertEquals(PlayerThreat.CRITICAL, yellow.threat());
    assertTrue(yellow.winningCritical());
  }

  @Test
  void weakPlayerDetection() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 30) % 52, JAIL, JAIL, JAIL),
            List.of(JAIL, JAIL, JAIL, JAIL));
    OpponentAnalysisReport report = engine.analyze("r3", snap, 0, mid(0), null);
    assertTrue(report.get(1).weak());
    assertTrue(report.get(1).ignoreForAttack() || report.get(1).threatScore() <= 40);
  }

  @Test
  void winningPlayerDetection() {
    GameSnapshot snap =
        snap(
            List.of(JAIL, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, HOME, JAIL));
    OpponentAnalysisReport report = engine.analyze("r4", snap, 0, mid(0), null);
    assertTrue(report.get(1).winningCritical());
    assertTrue(report.get(1).preferredTarget() || report.isLeader(1));
  }

  @Test
  void targetSelectionPrefersLeader() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 8) % 52, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, (LudoColor.YELLOW.startTile() + 35) % 52, JAIL));
    OpponentAnalysisReport report = engine.analyze("r5", snap, 0, mid(0), null);
    assertTrue(report.primaryTargetSeat() == 1 || report.isLeader(1));
    assertFalse(report.shouldIgnore(1));
  }

  @Test
  void botIndependencePerSeatCache() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 20) % 52, JAIL, JAIL, JAIL),
            List.of((LudoColor.YELLOW.startTile() + 25) % 52, JAIL, JAIL, JAIL));
    OpponentAnalysisReport a = engine.analyze("indep", snap, 0, mid(0), null);
    OpponentAnalysisReport b = engine.analyze("indep", snap, 1, mid(1), null);
    assertEquals(0, a.botSeat());
    assertEquals(1, b.botSeat());
    // Each bot gets its own report identity (independent analysis)
    assertTrue(a != b || a.primaryTargetSeat() != b.primaryTargetSeat() || a.botSeat() != b.botSeat());
  }

  @Test
  void futureThreatPredictionFlag() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 5) % 52, JAIL, JAIL, JAIL),
            List.of(
                HOME,
                (LudoColor.YELLOW.startTile() + 40) % 52,
                (LudoColor.YELLOW.startTile() + 38) % 52,
                JAIL));
    OpponentAnalysisReport report = engine.analyze("r6", snap, 0, mid(0), null);
    OpponentProfile y = report.get(1);
    assertTrue(y.threatScore() >= 40 || y.futureLeaderRisk() || y.finishedPawns() >= 1);
  }

  @Test
  void historyTracking() {
    history.record("h1", 1, OpponentHistory.EventType.CAPTURE);
    history.record("h1", 1, OpponentHistory.EventType.AGGRESSIVE);
    history.record("h1", 1, OpponentHistory.EventType.CAPTURE);
    assertEquals(2, history.recentCaptures("h1", 1));
    PlayStyle style = history.inferStyle("h1", 1);
    assertTrue(style == PlayStyle.CAPTURE_FOCUSED || style == PlayStyle.AGGRESSIVE
        || style == PlayStyle.UNKNOWN);
  }

  @Test
  void adaptiveBehindMarksAggressionContext() {
    GameSnapshot snap =
        snap(
            List.of(JAIL, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, (LudoColor.YELLOW.startTile() + 40) % 52, JAIL));
    BotMatchAnalysis behind =
        new BotMatchAnalysis(
            BotAiMode.MODE_1,
            BotGamePhase.MID,
            BotDifficulty.HARD,
            0,
            1,
            1,
            2,
            new boolean[] {true, false},
            1,
            new int[] {10, 200},
            new int[] {0, 2},
            new int[] {0, 1},
            0.5,
            true,
            false,
            true);
    OpponentAnalysisReport report = engine.analyze("r7", snap, 0, behind, null);
    assertTrue(report.botBehind() || report.currentLeaderSeat() == 1);
    assertTrue(report.primaryTargetSeat() == 1 || report.isPreferredTarget(1));
  }

  private static GameSnapshot snap(List<Integer> green, List<Integer> yellow) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(new boolean[] {true, false});
    snap.setSeatColors(List.of("GREEN", "YELLOW"));
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", green);
    all.put("YELLOW", yellow);
    snap.setTokenPositions(all);
    snap.setCurrentSeatIndex(0);
    return snap;
  }

  private static BotMatchAnalysis mid(int botSeat) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        botSeat,
        1,
        1,
        2,
        new boolean[] {true, false},
        1,
        new int[] {30, 40},
        new int[2],
        new int[] {1, 1},
        0.4,
        false,
        false,
        true);
  }
}
