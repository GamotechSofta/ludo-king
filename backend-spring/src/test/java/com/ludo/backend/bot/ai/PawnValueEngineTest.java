package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

class PawnValueEngineTest {

  private PawnValueConfig config;
  private PawnValueCalculator calculator;
  private PawnHistory history;
  private PawnDecision decision;
  private PawnValueEngine engine;

  @BeforeEach
  void setUp() {
    config =
        new PawnValueConfig(
            true, true, 10, 25, 40, 70, 100, 150, 200, 200, 25, 40, 30, 35, 1, 8, 10, 15);
    calculator = new PawnValueCalculator(config);
    history = new PawnHistory(config);
    decision = new PawnDecision(config);
    engine = new PawnValueEngine(config, calculator, history, decision);
  }

  @Test
  void nearHomeHasHighestPriority() {
    PawnValueReport report =
        engine.evaluate(
            "r1",
            snap(List.of(toExit(4), JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)),
            0,
            LudoColor.GREEN,
            List.of(toExit(4), JAIL, JAIL, JAIL),
            mid(),
            null);
    PawnPriority near = report.get(0);
    assertTrue(near.value() >= 150, near.debugLine());
    assertEquals(PawnImportance.HIGHEST, near.importance());
    assertTrue(near.neverSacrifice());
  }

  @Test
  void safeCellBonusApplied() {
    int safe = firstSafe();
    PawnState state =
        calculator.buildState(0, safe, LudoColor.GREEN, 20, 0, 0, LudoColor.GREEN.startTile());
    PawnPriority p = calculator.calculate(state, PawnStatistics.empty(), mid());
    assertTrue(p.labels().stream().anyMatch(l -> l.contains("Safe")), p.debugLine());
    assertTrue(p.value() >= config.safeCellBonus() + 10, p.debugLine());
  }

  @Test
  void leaderPawnMarked() {
    // Put one pawn clearly further along GREEN path than the other.
    int advanced = (LudoColor.GREEN.startTile() + 35) % 52;
    int early = (LudoColor.GREEN.startTile() + 2) % 52;
    if (isSafe(advanced)) {
      advanced = (advanced + 1) % 52;
    }
    PawnValueReport report =
        engine.evaluate(
            "r2",
            snap(List.of(advanced, early, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)),
            0,
            LudoColor.GREEN,
            List.of(advanced, early, JAIL, JAIL),
            mid(),
            null);
    PawnPriority leader = report.get(0);
    PawnPriority other = report.get(1);
    assertTrue(leader.value() > other.value(), leader.debugLine() + " vs " + other.debugLine());
    assertTrue(
        leader.state().leader() || leader.labels().stream().anyMatch(l -> l.contains("Leader")),
        leader.debugLine());
  }

  @Test
  void sacrificeNeverNearHome() {
    PawnState near =
        calculator.buildState(0, toExit(3), LudoColor.GREEN, 50, 0, 0, LudoColor.GREEN.startTile());
    PawnState early =
        calculator.buildState(1, JAIL, LudoColor.GREEN, 50, 0, 0, LudoColor.GREEN.startTile());
    PawnPriority high = calculator.calculate(near, PawnStatistics.empty(), mid());
    PawnPriority low = calculator.calculate(early, PawnStatistics.empty(), mid());
    assertTrue(high.neverSacrifice(), high.debugLine());
    assertTrue(decision.maySacrifice(low, high), low.value() + " vs " + high.value());
    assertFalse(decision.maySacrifice(high, low), high.debugLine());
  }

  @Test
  void openingJailIsLowPriority() {
    PawnState jail =
        calculator.buildState(0, JAIL, LudoColor.GREEN, 0, 0, 0, LudoColor.GREEN.startTile());
    PawnPriority p = calculator.calculate(jail, PawnStatistics.empty(), early());
    assertTrue(p.value() <= 20, p.debugLine());
    assertTrue(
        p.importance() == PawnImportance.LOWEST || p.importance() == PawnImportance.LOW,
        p.debugLine());
  }

  @Test
  void endgameFourthPawnHighest() {
    PawnState fourth =
        calculator.buildState(3, 20, LudoColor.GREEN, 30, 0, 3, LudoColor.GREEN.startTile());
    PawnPriority p = calculator.calculate(fourth, PawnStatistics.empty(), end());
    assertEquals(PawnImportance.HIGHEST, p.importance());
    assertTrue(p.labels().stream().anyMatch(l -> l.contains("Fourth")), p.debugLine());
  }

  @Test
  void dangerRaisesEscapeNeed() {
    PawnState exposed =
        calculator.buildState(0, 15, LudoColor.GREEN, 40, 80, 0, LudoColor.GREEN.startTile());
    PawnPriority p = calculator.calculate(exposed, PawnStatistics.empty(), mid());
    assertTrue(p.escapeNeeded(), p.debugLine());
    assertTrue(p.labels().stream().anyMatch(l -> l.contains("Danger")), p.debugLine());
  }

  @Test
  void historyTrackingWasteAndSafe() {
    history.record("h1", 0, PawnHistory.EventType.MOVED);
    history.record("h1", 0, PawnHistory.EventType.SAFE);
    history.record("h1", 0, PawnHistory.EventType.WASTE);
    history.record("h1", 0, PawnHistory.EventType.WASTE);
    PawnStatistics stats = history.stats("h1", 0);
    assertEquals(1, stats.timesMoved());
    assertEquals(1, stats.timesReachedSafe());
    assertEquals(2, stats.wasteStreak());
  }

  @Test
  void adaptiveFinalStretchAboveNearHome() {
    PawnState stretch =
        calculator.buildState(0, toExit(4), LudoColor.GREEN, 55, 0, 0, LudoColor.GREEN.startTile());
    PawnState midProg =
        calculator.buildState(1, 20, LudoColor.GREEN, 55, 0, 0, LudoColor.GREEN.startTile());
    PawnPriority a = calculator.calculate(stretch, PawnStatistics.empty(), mid());
    PawnPriority b = calculator.calculate(midProg, PawnStatistics.empty(), mid());
    assertTrue(a.value() > b.value(), a.value() + " vs " + b.value());
  }

  @Test
  void cacheReusedUntilBoardChanges() {
    GameSnapshot s =
        snap(List.of(10, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    List<Integer> own = List.of(10, JAIL, JAIL, JAIL);
    PawnValueReport a = engine.evaluate("c1", s, 0, LudoColor.GREEN, own, mid(), null);
    PawnValueReport b = engine.evaluate("c1", s, 0, LudoColor.GREEN, own, mid(), null);
    assertTrue(a == b);
  }

  private static int firstSafe() {
    for (int i = 0; i < 52; i++) {
      if (isSafe(i)) {
        return i;
      }
    }
    return 0;
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

  private static BotMatchAnalysis mid() {
    return analysis(BotGamePhase.MID, 0.4);
  }

  private static BotMatchAnalysis early() {
    return analysis(BotGamePhase.EARLY, 0.1);
  }

  private static BotMatchAnalysis end() {
    return analysis(BotGamePhase.END, 0.85);
  }

  private static BotMatchAnalysis analysis(BotGamePhase phase, double progress) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        phase,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        1,
        new int[] {30, 40},
        new int[2],
        new int[] {1, 1},
        progress,
        false,
        false,
        true);
  }
}
