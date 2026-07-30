package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FutureSimulatorTest {

  private FutureSimulatorConfig config;
  private FutureAnalyzer analyzer;
  private FutureSimulator simulator;

  @BeforeEach
  void setUp() {
    config =
        new FutureSimulatorConfig(
            true, 3, true, true, 50, 120, 150, 80, 60, 40, 80, 150, 40, 50, 90, 30, 45);
    analyzer = new FutureAnalyzer(config);
    PredictionEngine prediction = new PredictionEngine();
    simulator =
        new FutureSimulator(
            config,
            new SimulationEngine(prediction),
            analyzer,
            new SimulationCache(),
            new DecisionModifier(dummyScoreConfig()),
            disabledBehavior());
  }

  private static HumanBehaviorEngine disabledBehavior() {
    BehaviorConfig cfg = new BehaviorConfig(false, 30, 0.10, 0.65);
    return new HumanBehaviorEngine(
        cfg, new BehaviorAnalyzer(), new PatternDetector(), new BehaviorPredictor());
  }

  private static AIScoreConfig dummyScoreConfig() {
    return new AIScoreConfig(
        true, 150, 120, 40, 100, 70, 90, 70, 60, 100, 90, 10, 80, 60, 40, 30, 50, 80, 80, 120,
        150, 70, 40, 30, 70, 100, 10, 6, 10, 5);
  }

  @Test
  void futureHomeScoresHigh() {
    int from = toExit(4); // one step from home for GREEN (HOME_STEPS=6, indices 0..4)
    MoveCandidate home =
        move(0, 1, from, HOME, MoveType.HOME_FINISH);
    SimulationBoard board = SimulationBoard.fromSnapshot(snap(List.of(from, JAIL, JAIL, JAIL),
        List.of(JAIL, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, home, -1, false, endAnalysis());
    assertTrue(score.total() >= 150, score.toString());
    assertTrue(score.reasons().stream().anyMatch(r -> r.contains("Home")), score.toString());
  }

  @Test
  void futureSafeCellBonus() {
    int safe = firstSafe();
    MoveCandidate toSafe =
        move(0, 1, (safe + 51) % 52, safe, MoveType.SAFE_LAND);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of((safe + 51) % 52, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)),
            0,
            1);
    SimulationScore score = analyzer.analyzeRoot(board, toSafe, -1, false, midAnalysis());
    assertTrue(score.total() >= 80, score.toString());
  }

  @Test
  void futureCaptureBonus() {
    MoveCandidate cap =
        move(0, 3, 10, 13, MoveType.CAPTURE);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(13, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, cap, 1, false, midAnalysis());
    assertTrue(score.total() >= 40, score.toString());
  }

  @Test
  void futureDangerPenalizesExposedLanding() {
    // Bot lands on 15; yellow sits 3 behind on 12 → reachable with die 3
    MoveCandidate land =
        move(0, 2, 13, 15, MoveType.ADVANCE);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(15, JAIL, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, land, -1, false, midAnalysis());
    assertTrue(
        score.reasons().stream().anyMatch(r -> r.contains("Dangerous") || r.contains("Risk"))
            || score.total() < 40,
        score.toString());
  }

  @Test
  void trapDetectionOnMultiThreatState() {
    // Two enemies can reach bot on 20
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(20, JAIL, JAIL, JAIL), List.of(17, 14, JAIL, JAIL)), 0, 1);
    SimulationScore state = analyzer.analyzeState(board, midAnalysis());
    assertTrue(
        state.reasons().stream().anyMatch(r -> r.contains("Trap") || r.contains("Risk")),
        state.toString());
  }

  @Test
  void leaderPredictionPenaltyWhenLeaderAhead() {
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(
                List.of(5, JAIL, JAIL, JAIL),
                List.of(40, 38, 36, JAIL)),
            0,
            1);
    SimulationScore state = analyzer.analyzeState(board, midAnalysis());
    assertTrue(
        state.reasons().stream().anyMatch(r -> r.contains("Leader")) || state.total() <= 0,
        state.toString());
  }

  @Test
  void blockPredictionBonus() {
    MoveCandidate block =
        move(0, 2, 8, 10, MoveType.BLOCK);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(10, 10, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, block, -1, false, midAnalysis());
    assertTrue(score.reasons().stream().anyMatch(r -> r.contains("Block")), score.toString());
  }

  @Test
  void openingStrategyRewardsMultipleActive() {
    MoveCandidate advance =
        move(0, 4, 1, 5, MoveType.ADVANCE);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(5, 8, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, advance, -1, false, earlyAnalysis());
    assertTrue(score.reasons().stream().anyMatch(r -> r.contains("Opening")), score.toString());
  }

  @Test
  void endgamePrefersExactFinish() {
    int from = toExit(4);
    MoveCandidate home = move(0, 1, from, HOME, MoveType.HOME_FINISH);
    SimulationBoard board =
        SimulationBoard.fromSnapshot(
            snap(List.of(HOME, HOME, from, JAIL), List.of(10, JAIL, JAIL, JAIL)), 0, 1);
    SimulationScore score = analyzer.analyzeRoot(board, home, -1, false, endAnalysis());
    assertTrue(score.total() >= 190, score.toString());
  }

  @Test
  void simulatorReturnsEvWithinBudget() {
    simulator.beginTurn();
    GameSnapshot snap =
        snap(List.of(10, JAIL, JAIL, JAIL), List.of(20, JAIL, JAIL, JAIL));
    MoveCandidate root = move(0, 3, 10, 13, MoveType.ADVANCE);
    long deadline = System.nanoTime() + 200_000_000L;
    SimulationResult result =
        simulator.simulate(root, snap, midAnalysis(), 50, 50, deadline);
    assertTrue(result != null);
    assertTrue(result.elapsedNanos() < 200_000_000L, "elapsed=" + result.elapsedNanos());
    assertTrue(!result.pruned());
  }

  @Test
  void deepCopyDoesNotMutateOriginal() {
    GameSnapshot snap =
        snap(List.of(10, JAIL, JAIL, JAIL), List.of(20, JAIL, JAIL, JAIL));
    SimulationBoard base = SimulationBoard.fromSnapshot(snap, 0, 1);
    SimulationBoard copy = base.deepCopy();
    copy.applyMove(new SimulationMove(0, 0, 3, 10, 13));
    assertTrue(base.token(0, 0) == 10);
    assertTrue(copy.token(0, 0) == 13);
  }

  private static int firstSafe() {
    for (int i = 0; i < 52; i++) {
      if (isSafe(i)) {
        return i;
      }
    }
    return 0;
  }

  private static MoveCandidate move(int pawn, int dice, int from, int to, MoveType type) {
    return new MoveCandidate(
        new int[] {pawn, 0},
        pawn,
        dice,
        0,
        from,
        to,
        type,
        false,
        0,
        type == MoveType.CAPTURE,
        type == MoveType.CAPTURE ? 1 : -1,
        false,
        Integer.MAX_VALUE,
        false,
        50,
        type == MoveType.BLOCK,
        false);
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

  private static BotMatchAnalysis midAnalysis() {
    return analysis(BotGamePhase.MID, 0.4);
  }

  private static BotMatchAnalysis earlyAnalysis() {
    return analysis(BotGamePhase.EARLY, 0.1);
  }

  private static BotMatchAnalysis endAnalysis() {
    return analysis(BotGamePhase.END, 0.8);
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
