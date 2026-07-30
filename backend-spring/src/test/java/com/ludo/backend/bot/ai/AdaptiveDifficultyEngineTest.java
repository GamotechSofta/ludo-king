package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

class AdaptiveDifficultyEngineTest {

  private AdaptiveConfig config;
  private AdaptiveDifficultyEngine engine;
  private StrategySelector selector;
  private AdaptiveAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    config = new AdaptiveConfig(true, 0.45, true, true, true, 80, 35, 12);
    PerformanceTracker perf = new PerformanceTracker();
    analyzer = new AdaptiveAnalyzer(config, perf);
    selector = new StrategySelector(config);
    engine =
        new AdaptiveDifficultyEngine(
            config,
            new MatchAnalyzer(),
            analyzer,
            selector,
            perf,
            new DifficultyHistory(config));
  }

  @Test
  void leadingStrategyIsDefensive() {
    GameSnapshot snap =
        snap(
            List.of(HOME, HOME, (LudoColor.GREEN.startTile() + 40) % 52, JAIL),
            List.of(JAIL, JAIL, JAIL, JAIL));
    DifficultyProfile p =
        engine.evaluate("lead", snap, 0, BotDifficulty.HARD, mode1(false, true), null, null);
    assertEquals(BotStatus.LEADING, p.status());
    assertTrue(
        p.strategy() == AdaptiveStrategy.DEFENSIVE || p.strategy() == AdaptiveStrategy.FINISH,
        p.debugLine());
    assertTrue(p.aggression() <= 40, p.debugLine());
    assertTrue(p.diceAssistRate() <= 0.15, p.debugLine());
  }

  @Test
  void balancedStrategyBoardControl() {
    int g = (LudoColor.GREEN.startTile() + 20) % 52;
    int y = (LudoColor.YELLOW.startTile() + 18) % 52;
    GameSnapshot snap = snap(List.of(g, JAIL, JAIL, JAIL), List.of(y, JAIL, JAIL, JAIL));
    DifficultyProfile p =
        engine.evaluate("bal", snap, 0, BotDifficulty.HARD, mode1(false, false), null, null);
    assertTrue(
        p.status() == BotStatus.BALANCED || p.status() == BotStatus.LEADING || p.status() == BotStatus.BEHIND,
        p.debugLine());
    assertTrue(p.enabled());
  }

  @Test
  void behindStrategyRecovery() {
    GameSnapshot snap =
        snap(
            List.of(JAIL, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, (LudoColor.YELLOW.startTile() + 40) % 52, JAIL));
    DifficultyProfile p =
        engine.evaluate("behind", snap, 0, BotDifficulty.HARD, mode1(true, false), null, null);
    assertTrue(
        p.status() == BotStatus.BEHIND || p.status() == BotStatus.CRITICAL, p.debugLine());
    assertEquals(AdaptiveStrategy.RECOVERY, p.strategy());
    assertTrue(p.aggression() >= 70, p.debugLine());
    assertTrue(p.captureWeightDelta() > 0, p.debugLine());
    assertTrue(p.diceAssistRate() >= 0.30, p.debugLine());
  }

  @Test
  void criticalIncreasesFutureWeight() {
    GameSnapshot snap =
        snap(
            List.of(JAIL, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, HOME, (LudoColor.YELLOW.startTile() + 30) % 52));
    DifficultyProfile p =
        engine.evaluate("crit", snap, 0, BotDifficulty.HARD, mode1(true, false), null, null);
    assertEquals(BotStatus.CRITICAL, p.status());
    assertTrue(p.futureScoreMult() >= 1.1 || p.futureDepthBoost() >= 1, p.debugLine());
    assertTrue(p.diceAssistRate() <= 0.45);
    assertEquals(0.45, p.diceAssistRate(), 0.001);
  }

  @Test
  void twoPlayerModeMoreAggressive() {
    int a = analyzer.baseAggression(BotStatus.BALANCED, mode1(false, false), BotGamePhase.MID);
    int b =
        analyzer.baseAggression(
            BotStatus.BALANCED,
            analysis(BotAiMode.MODE_2, BotGamePhase.MID, false, false),
            BotGamePhase.MID);
    assertTrue(a > b, a + " vs " + b);
  }

  @Test
  void fourPlayerModeReducesAggression() {
    int mode2 =
        analyzer.baseAggression(
            BotStatus.BEHIND,
            analysis(BotAiMode.MODE_2, BotGamePhase.MID, true, false),
            BotGamePhase.MID);
    int mode1 =
        analyzer.baseAggression(BotStatus.BEHIND, mode1(true, false), BotGamePhase.MID);
    assertTrue(mode2 < mode1, mode2 + " vs " + mode1);
  }

  @Test
  void endgameFinishStrategy() {
    MatchAnalyzer.MatchSnapshot ms =
        new MatchAnalyzer.MatchSnapshot(
            0, 1, 2, 100, 80, -20, 3, 0, 0, true, false, mode1(false, true));
    AdaptiveStrategy s =
        selector.select(BotStatus.LEADING, BotGamePhase.END, ms, mode1(false, true));
    assertEquals(AdaptiveStrategy.FINISH, s);
  }

  @Test
  void comebackBoostsAssist() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 5) % 52, JAIL, JAIL, JAIL),
            List.of(HOME, HOME, (LudoColor.YELLOW.startTile() + 40) % 52, JAIL));
    BotMatchAnalysis behind = mode1(true, false);
    DifficultyProfile p =
        engine.evaluate("cb", snap, 0, BotDifficulty.HARD, behind, null, null);
    assertTrue(p.diceAssistRate() > 0.2 || p.homeWeightDelta() > 0, p.debugLine());
  }

  @Test
  void dynamicStrategySwitchBetweenStates() {
    GameSnapshot behindSnap =
        snap(List.of(JAIL, JAIL, JAIL, JAIL), List.of(HOME, HOME, HOME, JAIL));
    DifficultyProfile a =
        engine.evaluate("sw", behindSnap, 0, BotDifficulty.HARD, mode1(true, false), null, null);
    GameSnapshot leadSnap =
        snap(
            List.of(HOME, HOME, HOME, (LudoColor.GREEN.startTile() + 10) % 52),
            List.of(JAIL, JAIL, JAIL, JAIL));
    engine.invalidate("sw", 0);
    DifficultyProfile b =
        engine.evaluate("sw", leadSnap, 0, BotDifficulty.HARD, mode1(false, true), null, null);
    assertTrue(a.status() != b.status() || a.strategy() != b.strategy(), a + " -> " + b);
  }

  @Test
  void easySkipped() {
    GameSnapshot snap =
        snap(List.of(JAIL, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    DifficultyProfile p =
        engine.evaluate("ez", snap, 0, BotDifficulty.EASY, mode1(false, false), null, null);
    assertEquals(false, p.enabled());
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

  private static BotMatchAnalysis mode1(boolean behind, boolean leader) {
    return analysis(BotAiMode.MODE_1, BotGamePhase.MID, behind, leader);
  }

  private static BotMatchAnalysis analysis(
      BotAiMode mode, BotGamePhase phase, boolean behind, boolean leader
  ) {
    return new BotMatchAnalysis(
        mode,
        phase,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        leader ? 0 : 1,
        new int[] {behind ? 10 : 80, behind ? 200 : 40},
        new int[] {0, behind ? 2 : 0},
        new int[] {1, 1},
        0.4,
        behind,
        leader,
        true);
  }
}
