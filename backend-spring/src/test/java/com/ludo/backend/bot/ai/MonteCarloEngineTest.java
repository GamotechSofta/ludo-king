package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MonteCarloEngineTest {

  private MonteCarloConfig config;
  private MonteCarloEngine engine;
  private DecisionCache cache;
  private BranchPruner pruner;
  private ExpectedValueCalculator ev;

  @BeforeEach
  void setUp() {
    config = new MonteCarloConfig(true, 3, 24, 50, true, true, 140);
    cache = new DecisionCache(config);
    pruner = new BranchPruner(config);
    FutureSimulatorConfig futureConfig =
        new FutureSimulatorConfig(
            true, 2, true, true, 50, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45);
    DecisionEvaluator evaluator =
        new DecisionEvaluator(
            new SimulationEngine(new PredictionEngine()),
            new FutureAnalyzer(futureConfig),
            new WinningPathCalculator(),
            cache,
            config);
    engine = new MonteCarloEngine(config, pruner, evaluator, cache);
    ev = new ExpectedValueCalculator();
  }

  @Test
  void prefersExactFinishOverCapture() {
    int near = toExit(4);
    MoveCandidate finish = candidate(0, near, HOME, MoveType.HOME_FINISH, false);
    MoveCandidate capture = candidate(1, 10, 14, MoveType.CAPTURE, true);
    List<BotDecisionEngine.ScoredDecision> scored = new ArrayList<>();
    scored.add(scored(finish, 80, 20));
    scored.add(scored(capture, 90, 10)); // higher current but not finish

    GameSnapshot snap =
        snap2(
            List.of(near, 10, HOME, HOME),
            List.of(14, JAIL, JAIL, JAIL));
    BotMatchAnalysis analysis = analysis(BotGamePhase.END, 0.9, new int[] {2, 0}, false, true);

    EndGameConfig egCfg = new EndGameConfig(true, 20, 4, 0.7, 1.4, 0.35, 0.25);
    EndGameEngine eg =
        new EndGameEngine(
            egCfg,
            new EndGameAnalyzer(egCfg),
            new FinishPlanner(),
            new WinningPathCalculator(),
            new RiskAnalyzer());
    EndGameProfile endGame =
        eg.evaluate(
            BotDifficulty.HARD,
            analysis,
            null,
            List.of(near, 10, HOME, HOME),
            LudoColor.GREEN,
            List.of(finish, capture),
            null,
            null,
            null,
            null);

    BotDecisionEngine.ScoredDecision chosen =
        engine.select(
            scored,
            snap,
            analysis,
            BotDifficulty.HARD,
            null,
            null,
            null,
            endGame,
            null,
            System.nanoTime());
    assertNotNull(chosen);
    assertEquals(HOME, chosen.candidate.to());
  }

  @Test
  void easyDifficultySkipped() {
    MoveCandidate a = candidate(0, 5, 8, MoveType.ADVANCE, false);
    List<BotDecisionEngine.ScoredDecision> scored = List.of(scored(a, 50, 10));
    assertNull(
        engine.select(
            scored,
            snap2(List.of(5, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)),
            analysis(BotGamePhase.MID, 0.4, new int[] {0, 0}, false, false),
            BotDifficulty.EASY,
            null,
            null,
            null,
            null,
            null,
            System.nanoTime()));
  }

  @Test
  void branchPrunerDropsFarWorseMoves() {
    SimulationNode good = new SimulationNode(candidate(0, 5, 8, MoveType.ADVANCE, false), 200, 50, EndGameRisk.SAFE);
    SimulationNode bad = new SimulationNode(candidate(1, 1, 2, MoveType.ADVANCE, false), 10, 0, EndGameRisk.BALANCED);
    List<SimulationNode> nodes = new ArrayList<>(List.of(good, bad));
    pruner.prune(nodes, false, null, null);
    assertTrue(bad.pruned());
    assertTrue(!good.pruned());
  }

  @Test
  void expectedValueFormula() {
    SimulationNode n = new SimulationNode(candidate(0, 5, 8, MoveType.ADVANCE, false), 100, 50, EndGameRisk.SAFE);
    n.record(40, 70, 10);
    n.record(60, 80, 10);
    // avgSim=50 + current=100 + win=75 - risk=10 = 215
    assertEquals(215.0, ev.expectedValue(n), 0.01);
  }

  @Test
  void timeBudgetNeverBlocks() {
    MonteCarloConfig tight = new MonteCarloConfig(true, 4, 50, 1, true, true, 140);
    DecisionCache c = new DecisionCache(tight);
    MonteCarloEngine tightEngine =
        new MonteCarloEngine(
            tight,
            new BranchPruner(tight),
            new DecisionEvaluator(
                new SimulationEngine(new PredictionEngine()),
                new FutureAnalyzer(
                    new FutureSimulatorConfig(
                        true, 2, true, true, 50, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45)),
                new WinningPathCalculator(),
                c,
                tight),
            c);
    List<BotDecisionEngine.ScoredDecision> scored = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      scored.add(scored(candidate(i % 4, 5 + i, 8 + i, MoveType.ADVANCE, false), 40 + i, 5));
    }
    long t0 = System.nanoTime();
    BotDecisionEngine.ScoredDecision chosen =
        tightEngine.select(
            scored,
            snap2(List.of(5, 6, 7, 8), List.of(20, JAIL, JAIL, JAIL)),
            analysis(BotGamePhase.MID, 0.5, new int[] {0, 0}, false, false),
            BotDifficulty.HARD,
            null,
            null,
            null,
            null,
            null,
            t0);
    long ms = (System.nanoTime() - t0) / 1_000_000L;
    assertTrue(ms < 80, "should stay near budget, was " + ms + "ms");
    assertNotNull(chosen); // may still return a pick within budget
  }

  @Test
  void allocateQuotasPreferStrongerPriors() {
    List<SimulationNode> alive =
        List.of(
            new SimulationNode(candidate(0, 1, 2, MoveType.ADVANCE, false), 200, 0, EndGameRisk.SAFE),
            new SimulationNode(candidate(1, 3, 4, MoveType.ADVANCE, false), 20, 0, EndGameRisk.BALANCED));
    int[] q = MonteCarloEngine.allocateQuotas(alive, 20, null);
    assertEquals(20, q[0] + q[1]);
    assertTrue(q[0] > q[1]);
  }

  @Test
  void cacheReuseSameFingerprint() {
    cache.beginTurn("boardA");
    cache.put("k1", 42.0);
    cache.beginTurn("boardA");
    assertEquals(42.0, cache.get("k1"), 0.001);
    cache.beginTurn("boardB");
    assertNull(cache.get("k1"));
  }

  @Test
  void disabledConfigReturnsNullFallback() {
    MonteCarloConfig off = new MonteCarloConfig(false, 4, 50, 5, true, true, 140);
    MonteCarloEngine offEngine =
        new MonteCarloEngine(off, new BranchPruner(off), engineEvaluator(off), new DecisionCache(off));
    assertNull(
        offEngine.select(
            List.of(scored(candidate(0, 1, 2, MoveType.ADVANCE, false), 10, 0)),
            snap2(List.of(1, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)),
            analysis(BotGamePhase.MID, 0.3, new int[] {0, 0}, false, false),
            BotDifficulty.HARD,
            null,
            null,
            null,
            null,
            null,
            System.nanoTime()));
  }

  @Test
  void fourPlayerSnapshotSupported() {
    MoveCandidate m = candidate(0, 5, 8, MoveType.ADVANCE, false);
    GameSnapshot snap = snap4(List.of(5, JAIL, JAIL, JAIL));
    BotDecisionEngine.ScoredDecision chosen =
        engine.select(
            List.of(scored(m, 60, 15)),
            snap,
            analysis4(),
            BotDifficulty.HARD,
            null,
            null,
            null,
            null,
            null,
            System.nanoTime());
    assertNotNull(chosen);
  }

  private DecisionEvaluator engineEvaluator(MonteCarloConfig cfg) {
    FutureSimulatorConfig futureConfig =
        new FutureSimulatorConfig(
            true, 2, true, true, 50, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45);
    return new DecisionEvaluator(
        new SimulationEngine(new PredictionEngine()),
        new FutureAnalyzer(futureConfig),
        new WinningPathCalculator(),
        new DecisionCache(cfg),
        cfg);
  }

  private static BotDecisionEngine.ScoredDecision scored(MoveCandidate c, int cur, int fut) {
    MoveScore s = new MoveScore();
    s.add("t", cur);
    return new BotDecisionEngine.ScoredDecision(c, s, fut);
  }

  private static MoveCandidate candidate(
      int pawn, int from, int to, MoveType type, boolean capture
  ) {
    return new MoveCandidate(
        new int[] {pawn, 0},
        pawn,
        1,
        0,
        from,
        to,
        type,
        false,
        0,
        capture,
        capture ? 1 : -1,
        false,
        20,
        false,
        80,
        false,
        false);
  }

  private static GameSnapshot snap2(List<Integer> green, List<Integer> yellow) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(new boolean[] {true, false});
    snap.setSeatColors(List.of("GREEN", "YELLOW"));
    snap.setCurrentSeatIndex(0);
    Map<String, List<Integer>> pos = new HashMap<>();
    pos.put("GREEN", green);
    pos.put("YELLOW", yellow);
    snap.setTokenPositions(pos);
    return snap;
  }

  private static GameSnapshot snap4(List<Integer> green) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(new boolean[] {true, false, true, true});
    snap.setSeatColors(List.of("GREEN", "YELLOW", "BLUE", "RED"));
    snap.setCurrentSeatIndex(0);
    Map<String, List<Integer>> pos = new HashMap<>();
    pos.put("GREEN", green);
    pos.put("YELLOW", List.of(JAIL, JAIL, JAIL, JAIL));
    pos.put("BLUE", List.of(JAIL, JAIL, JAIL, JAIL));
    pos.put("RED", List.of(JAIL, JAIL, JAIL, JAIL));
    snap.setTokenPositions(pos);
    return snap;
  }

  private static BotMatchAnalysis analysis(
      BotGamePhase phase, double tp, int[] finished, boolean behind, boolean leader
  ) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        phase,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        leader ? 0 : 1,
        new int[] {100, 80},
        finished,
        new int[] {2, 2},
        tp,
        behind,
        leader,
        true);
  }

  private static BotMatchAnalysis analysis4() {
    return new BotMatchAnalysis(
        BotAiMode.MODE_2,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        0,
        1,
        3,
        4,
        new boolean[] {true, false, true, true},
        1,
        new int[] {40, 50, 30, 20},
        new int[] {0, 0, 0, 0},
        new int[] {1, 1, 0, 0},
        0.35,
        false,
        false,
        true);
  }
}
