package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MoveScoreCalculatorTest {

  private AIScoreConfig config;
  private MoveScoreCalculator calculator;
  private BotMoveHistoryStore history;

  @BeforeEach
  void setUp() {
    config =
        new AIScoreConfig(
            true, 150, 120, 40, 100, 70, 90, 70, 60, 100, 90, 10, 80, 60, 40, 30, 50, 80, 80, 120,
            150, 70, 40, 30, 70, 100, 10, 6, 10, 5);
    DangerMapConfig dangerConfig =
        new DangerMapConfig(
            false, false, false, false, false, 90, 100, 40, 25, 80, 120, 80, 40, 20, 80);
    ThreatAnalyzer threatAnalyzer = new ThreatAnalyzer(dangerConfig);
    PawnValueConfig pawnConfig = testPawnConfig(false);
    OpponentAnalysisConfig oppConfig = testOppConfig(false);
    calculator =
        new MoveScoreCalculator(
            config,
            dangerConfig,
            threatAnalyzer,
            pawnConfig,
            new PawnDecision(pawnConfig),
            oppConfig,
            new DecisionModifier(config),
            disabledEndGameEngine(),
            disabledBehaviorEngine());
    history = new BotMoveHistoryStore();
  }

  private static HumanBehaviorEngine disabledBehaviorEngine() {
    BehaviorConfig cfg = new BehaviorConfig(false, 30, 0.10, 0.65);
    return new HumanBehaviorEngine(
        cfg, new BehaviorAnalyzer(), new PatternDetector(), new BehaviorPredictor());
  }

  private static EndGameEngine disabledEndGameEngine() {
    EndGameConfig cfg = new EndGameConfig(false, 20, 4, 0.7, 1.4, 0.35, 0.25);
    return new EndGameEngine(
        cfg,
        new EndGameAnalyzer(cfg),
        new FinishPlanner(),
        new WinningPathCalculator(),
        new RiskAnalyzer());
  }

  private static MonteCarloEngine disabledMonteCarloEngine() {
    MonteCarloConfig cfg = new MonteCarloConfig(false, 4, 50, 5, true, true, 140);
    DecisionCache cache = new DecisionCache(cfg);
    FutureSimulatorConfig futureConfig =
        new FutureSimulatorConfig(
            false, 2, false, true, 5, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45);
    return new MonteCarloEngine(
        cfg,
        new BranchPruner(cfg),
        new DecisionEvaluator(
            new SimulationEngine(new PredictionEngine()),
            new FutureAnalyzer(futureConfig),
            new WinningPathCalculator(),
            cache,
            cfg),
        cache);
  }

  private static PawnValueConfig testPawnConfig(boolean enabled) {
    return new PawnValueConfig(
        enabled, true, 10, 25, 40, 70, 100, 150, 200, 200, 25, 40, 30, 35, 1, 8, 10, 15);
  }

  private static OpponentAnalysisConfig testOppConfig(boolean enabled) {
    return new OpponentAnalysisConfig(
        enabled, enabled, false, false, enabled, 150, 120, 80, 20, 20, 15, 20, 120, 70, 50, 40, 35,
        80, 35, 25, 30);
  }

  @Test
  void homePriorityBeatsSafeCell() {
    int lastExit = toExit(EXIT_LEN - 1);
    BoardAnalysisCache cache = cache(List.of(lastExit, 5), Map.of("YELLOW", List.of(JAIL)));
    MoveCandidate home =
        candidate(new int[] {0, 0}, 0, 1, 0, lastExit, HOME, MoveType.HOME_FINISH, false, 0, false);
    MoveCandidate safe =
        candidate(new int[] {1, 0}, 1, 1, 0, 5, 8, MoveType.SAFE_LAND, false, 0, false);
    // Force safe flag via isSafe — if 8 not safe, still compare home vs advance
    MoveScore h = calculator.score(home, cache, analysis(), "r", history);
    MoveScore s = calculator.score(safe, cache, analysis(), "r", history);
    assertTrue(h.total() > s.total(), "Home should outrank non-home");
  }

  @Test
  void escapeGetsBonus() {
    BoardAnalysisCache cache =
        cache(List.of(10), Map.of("YELLOW", Arrays.asList(7, JAIL, JAIL, JAIL)));
    MoveCandidate escape =
        candidate(new int[] {0, 0}, 0, 3, 0, 10, 13, MoveType.ESCAPE, true, 0, false);
    MoveScore score = calculator.score(escape, cache, analysis(), "r", history);
    assertTrue(
        score.reasons().stream().anyMatch(r -> r.label().contains("Escape")),
        "Escape reason present: " + score);
  }

  @Test
  void captureLeaderScoresHigh() {
    BoardAnalysisCache cache =
        cache(List.of(13), Map.of("YELLOW", Arrays.asList(16, JAIL, JAIL, JAIL)));
    MoveCandidate cap =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            3,
            0,
            13,
            16,
            MoveType.CAPTURE,
            false,
            0,
            true,
            1,
            true,
            20,
            false,
            50,
            false,
            false);
    MoveScore score = calculator.score(cap, cache, analysis(), "r", history);
    assertTrue(score.total() >= config.captureLeaderBonus(), score.toString());
  }

  @Test
  void riskPenaltyAppliedWhenThreatenedLanding() {
    BoardAnalysisCache cache =
        cache(List.of(10), Map.of("YELLOW", Arrays.asList(7, JAIL, JAIL, JAIL)));
    MoveCandidate risky =
        candidate(new int[] {0, 0}, 0, 1, 0, 10, 11, MoveType.ADVANCE, false, 1, false);
    MoveScore score = calculator.score(risky, cache, analysis(), "r", history);
    assertTrue(
        score.reasons().stream().anyMatch(r -> r.delta() < 0 && r.label().toLowerCase().contains("risk")),
        score.toString());
  }

  @Test
  void repeatedPawnPenaltyAfterHistory() {
    history.record("room", 0, 10);
    history.record("room", 0, 10);
    history.record("room", 0, 10);
    history.record("room", 0, 10);
    history.record("room", 0, 10);
    BoardAnalysisCache cache = cache(List.of(10, 20), Map.of("YELLOW", List.of(JAIL)));
    MoveCandidate same =
        candidate(new int[] {0, 0}, 0, 1, 0, 10, 11, MoveType.ADVANCE, false, 0, false);
    MoveScore score = calculator.score(same, cache, analysis(), "room", history);
    assertTrue(
        score.reasons().stream().anyMatch(r -> r.label().contains("Repeated") || r.label().contains("Same")),
        score.toString());
  }

  @Test
  void smartRandomnessWhenClose() {
    DangerMapConfig dangerConfig =
        new DangerMapConfig(
            false, false, false, false, false, 90, 100, 40, 25, 80, 120, 80, 40, 20, 80);
    FutureSimulatorConfig futureConfig = testFutureConfig(false);
    ThreatAnalyzer threatAnalyzer = new ThreatAnalyzer(dangerConfig);
    ThreatCache threatCache = new ThreatCache(dangerConfig, new BoardScanner(), threatAnalyzer);
    PredictionEngine prediction = new PredictionEngine();
    SimulationEngine simEngine = new SimulationEngine(prediction);
    FutureAnalyzer analyzer = new FutureAnalyzer(futureConfig);
    SimulationCache simCache = new SimulationCache();
    FutureSimulator futureSimulator =
        new FutureSimulator(
            futureConfig,
            simEngine,
            analyzer,
            simCache,
            new DecisionModifier(config),
            disabledBehaviorEngine());
    PawnValueConfig pawnConfig = testPawnConfig(false);
    PawnValueEngine pawnEngine =
        new PawnValueEngine(
            pawnConfig,
            new PawnValueCalculator(pawnConfig),
            new PawnHistory(pawnConfig),
            new PawnDecision(pawnConfig));
    OpponentAnalysisConfig oppConfig = testOppConfig(false);
    LeaderDetectionEngine leaderEngine =
        new LeaderDetectionEngine(
            oppConfig,
            new OpponentAnalyzer(oppConfig, new OpponentHistory(oppConfig)),
            new TargetSelector(oppConfig),
            new OpponentHistory(oppConfig));
    AdaptiveConfig adaptiveConfig =
        new AdaptiveConfig(false, 0.45, true, true, true, 80, 35, 12);
    PerformanceTracker perf = new PerformanceTracker();
    AdaptiveDifficultyEngine adaptiveEngine =
        new AdaptiveDifficultyEngine(
            adaptiveConfig,
            new MatchAnalyzer(),
            new AdaptiveAnalyzer(adaptiveConfig, perf),
            new StrategySelector(adaptiveConfig),
            perf,
            new DifficultyHistory(adaptiveConfig));
    PersonalityConfig personalityConfig =
        new PersonalityConfig(false, "fixed", "Balanced", false, 0.0);
    BotPersonalityEngine personalityEngine =
        new BotPersonalityEngine(
            personalityConfig,
            new PersonalitySelector(personalityConfig),
            new PersonalityHistory(),
            new DecisionModifier(config));
    BotDecisionEngine engine =
        new BotDecisionEngine(
            config,
            dangerConfig,
            futureConfig,
            new MoveCandidateFactory(),
            calculator,
            history,
            threatCache,
            threatAnalyzer,
            futureSimulator,
            pawnEngine,
            leaderEngine,
            adaptiveEngine,
            personalityEngine,
            disabledEndGameEngine(),
            disabledMonteCarloEngine(),
            disabledBehaviorEngine());
    MoveCandidate a =
        candidate(new int[] {0, 0}, 0, 1, 0, 1, 2, MoveType.ADVANCE, false, 0, false);
    MoveCandidate b =
        candidate(new int[] {1, 0}, 1, 1, 0, 5, 6, MoveType.ADVANCE, false, 0, false);
    MoveScore sa = new MoveScore();
    sa.add("t", 100);
    MoveScore sb = new MoveScore();
    sb.add("t", 95);
    BotDecisionEngine.ScoredDecision ea =
        new BotDecisionEngine.ScoredDecision(a, sa, 0);
    BotDecisionEngine.ScoredDecision eb =
        new BotDecisionEngine.ScoredDecision(b, sb, 0);
    BotDecisionEngine.ScoredDecision pick =
        engine.pickWithSmartRandomness(List.of(ea, eb));
    assertTrue(pick == ea || pick == eb);
  }

  private static FutureSimulatorConfig testFutureConfig(boolean enabled) {
    return new FutureSimulatorConfig(
        enabled, 2, false, true, 5, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45);
  }

  private static MoveCandidate candidate(
      int[] raw,
      int pawn,
      int dice,
      int diceIndex,
      int from,
      int to,
      MoveType type,
      boolean threatFrom,
      int threatTo,
      boolean capture
  ) {
    return new MoveCandidate(
        raw, pawn, dice, diceIndex, from, to, type, threatFrom, threatTo, capture, -1, false,
        Integer.MAX_VALUE, false, 50, false, false);
  }

  private BoardAnalysisCache cache(List<Integer> own, Map<String, List<Integer>> others) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(new boolean[] {true, false});
    snap.setSeatColors(List.of("GREEN", "YELLOW"));
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.putAll(others);
    snap.setTokenPositions(all);
    return BoardAnalysisCache.build(snap, 0, LudoColor.GREEN, own, analysis());
  }

  private static BotMatchAnalysis analysis() {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        1,
        new int[] {40, 50},
        new int[2],
        new int[] {1, 1},
        0.4,
        false,
        false,
        true);
  }
}
