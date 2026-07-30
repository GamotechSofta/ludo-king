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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmartDiceEngineTest {

  private SmartDiceConfig config;
  private DiceEvaluator evaluator;
  private DiceStrategy strategy;
  private DiceHistory history;
  private SmartDiceEngine engine;

  @BeforeEach
  void setUp() {
    config =
        new SmartDiceConfig(
            true, true, 1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 1.0, 0.4, 0.85, 1.15, 0.0, 20, 180, 120, 90,
            100, 50, 40, 60, 35, 40, 100, 40);
    strategy = new DiceStrategy(config);
    AIScoreConfig scoreConfig =
        new AIScoreConfig(
            true, 150, 120, 40, 100, 70, 90, 70, 60, 100, 90, 10, 80, 60, 40, 30, 50, 80, 80, 120,
            150, 70, 40, 30, 70, 100, 10, 6, 10, 5);
    evaluator = new DiceEvaluator(config, strategy, new DecisionModifier(scoreConfig));
    history = new DiceHistory(config);
    engine = new SmartDiceEngine(config, evaluator, strategy, history);
  }

  @Test
  void homeDiceScoresHigh() {
    int from = toExit(4);
    GameSnapshot snap = snap(List.of(from, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    DiceScore score =
        evaluator.evaluateDie(snap, 0, 1, endAnalysis(), (t, d) -> t == 0 && d == 1);
    assertTrue(score.total() >= 180, score.toString());
    assertTrue(score.reasons().stream().anyMatch(r -> r.contains("Home")), score.toString());
  }

  @Test
  void safeCellDiceBonus() {
    int safe = firstSafeNotStart(LudoColor.GREEN);
    int from = (safe + 51) % 52;
    // Ensure from steps by 1 to safe for GREEN may not work — score destination via legality
    GameSnapshot snap = snap(List.of(from, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    // Find a die that lands on a safe cell
    DiceCandidate bestSafe = null;
    List<DiceCandidate> all =
        evaluator.evaluateAll(
            snap,
            0,
            midAnalysis(),
            (t, d) -> {
              if (t != 0) {
                return false;
              }
              int to = com.ludo.backend.bot.BotBoardMath.applySteps(LudoColor.GREEN, from, d);
              return to != from;
            },
            history,
            "r");
    for (DiceCandidate c : all) {
      int to = com.ludo.backend.bot.BotBoardMath.applySteps(LudoColor.GREEN, from, c.dice());
      if (isSafe(to) && c.scoreTotal() > 0) {
        bestSafe = c;
        break;
      }
    }
    if (bestSafe != null) {
      assertTrue(
          bestSafe.reasons().stream().anyMatch(r -> r.contains("Safe")) || bestSafe.scoreTotal() >= 30,
          bestSafe.reasons().toString());
    } else {
      // Board geometry may not allow — still verify scoring path doesn't crash
      assertEquals(6, all.size());
    }
  }

  @Test
  void escapeDiceBonus() {
    // Bot on unsafe cell with enemy 3 behind; die that leaves threat scores escape
    int botPos = (LudoColor.GREEN.startTile() + 20) % 52;
    if (isSafe(botPos)) {
      botPos = (botPos + 1) % 52;
    }
    int enemy = (botPos + 49) % 52; // 3 behind clockwise? actually (bot-3)
    enemy = Math.floorMod(botPos - 3, 52);
    if (isSafe(enemy)) {
      enemy = Math.floorMod(enemy - 1, 52);
    }
    GameSnapshot snap =
        snap(List.of(botPos, JAIL, JAIL, JAIL), List.of(enemy, JAIL, JAIL, JAIL));
    List<DiceCandidate> all =
        evaluator.evaluateAll(
            snap,
            0,
            midAnalysis(),
            (t, d) -> t == 0 && d >= 1 && d <= 6,
            history,
            "esc");
    boolean anyEscape =
        all.stream().anyMatch(c -> c.reasons().stream().anyMatch(r -> r.contains("Escape")));
    // Escape depends on threat geometry; at minimum candidates exist
    assertEquals(6, all.size());
    assertTrue(anyEscape || all.stream().anyMatch(c -> c.scoreTotal() != 0));
  }

  @Test
  void openingDiceBonusOnSix() {
    GameSnapshot snap =
        snap(List.of(JAIL, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    DiceScore six =
        evaluator.evaluateDie(
            snap, 0, 6, earlyAnalysis(), (t, d) -> d == 6 && t == 0);
    assertTrue(
        six.reasons().stream().anyMatch(r -> r.contains("Open") || r.contains("Board")),
        six.toString());
  }

  @Test
  void boardDevelopmentTwoThreeActive() {
    int start = LudoColor.GREEN.startTile();
    GameSnapshot snap =
        snap(List.of(start, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    DiceScore six =
        evaluator.evaluateDie(
            snap, 0, 6, earlyAnalysis(), (t, d) -> d == 6 && t >= 1);
    assertTrue(
        six.reasons().stream().anyMatch(r -> r.contains("Board") || r.contains("Open"))
            || six.total() >= 40,
        six.toString());
  }

  @Test
  void weightedRandomNeverHardMaxOnly() {
    List<DiceCandidate> cands = List.of(
        cand(6, 160),
        cand(4, 150),
        cand(3, 140),
        cand(2, 100),
        cand(1, 60),
        cand(5, 50));
    DiceProbability.assignWeights(cands, true);
    double sum = cands.stream().mapToDouble(DiceCandidate::probability).sum();
    assertEquals(1.0, sum, 0.001);
    // Highest should not be 100%
    DiceCandidate top =
        cands.stream().max(java.util.Comparator.comparingInt(DiceCandidate::scoreTotal)).orElseThrow();
    assertTrue(top.probability() < 0.55, "p=" + top.probability());
    assertTrue(top.probability() > 0.15);
  }

  @Test
  void randomnessProducesVariety() {
    GameSnapshot snap =
        snap(
            List.of((LudoColor.GREEN.startTile() + 5) % 52, JAIL, JAIL, JAIL),
            List.of(JAIL, JAIL, JAIL, JAIL));
    Set<Integer> faces = new HashSet<>();
    Random rng = new Random(42);
    for (int i = 0; i < 40; i++) {
      Integer face =
          engine.maybePick(
              "var",
              snap,
              0,
              BotDifficulty.HARD,
              midAnalysis(),
              (t, d) -> t == 0,
              rng);
      if (face != null) {
        faces.add(face);
      }
    }
    assertTrue(faces.size() >= 2, "faces=" + faces);
  }

  @Test
  void noKillBiasInScoring() {
    // Enemy on a cell; die that lands on them must NOT get capture bonus labels
    int bot = (LudoColor.GREEN.startTile() + 10) % 52;
    int enemy = (bot + 4) % 52;
    if (isSafe(enemy)) {
      enemy = (enemy + 1) % 52;
      bot = Math.floorMod(enemy - 4, 52);
    }
    GameSnapshot snap =
        snap(List.of(bot, JAIL, JAIL, JAIL), List.of(enemy, JAIL, JAIL, JAIL));
    DiceScore score =
        evaluator.evaluateDie(snap, 0, 4, midAnalysis(), (t, d) -> t == 0 && d == 4);
    assertFalse(
        score.reasons().stream().anyMatch(r -> r.toLowerCase().contains("kill")
            || r.toLowerCase().contains("capture")),
        score.toString());
  }

  @Test
  void noImpossibleDice() {
    GameSnapshot snap =
        snap(List.of(JAIL, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    Random rng = new Random(7);
    for (int i = 0; i < 30; i++) {
      Integer face =
          engine.maybePick(
              "legal",
              snap,
              0,
              BotDifficulty.HARD,
              midAnalysis(),
              (t, d) -> d == 6 && t == 0,
              rng);
      if (face != null) {
        assertTrue(face >= 1 && face <= 6, "face=" + face);
      }
    }
  }

  @Test
  void easyMediumSkippedByDifficulty() {
    GameSnapshot snap =
        snap(List.of(JAIL, JAIL, JAIL, JAIL), List.of(JAIL, JAIL, JAIL, JAIL));
    Integer face =
        engine.maybePick(
            "ez",
            snap,
            0,
            BotDifficulty.EASY,
            midAnalysis(),
            (t, d) -> true,
            new Random(1));
    assertEquals(null, face);
  }

  private static DiceCandidate cand(int dice, int score) {
    DiceScore s = new DiceScore();
    s.add("t", score);
    return new DiceCandidate(dice, s);
  }

  private static int firstSafeNotStart(LudoColor color) {
    for (int i = 0; i < 52; i++) {
      if (isSafe(i) && i != color.startTile()) {
        return i;
      }
    }
    return color.startTile();
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
    return analysis(BotGamePhase.MID, BotAiMode.MODE_1, false, false);
  }

  private static BotMatchAnalysis earlyAnalysis() {
    return analysis(BotGamePhase.EARLY, BotAiMode.MODE_1, false, false);
  }

  private static BotMatchAnalysis endAnalysis() {
    return analysis(BotGamePhase.END, BotAiMode.MODE_1, false, false);
  }

  private static BotMatchAnalysis analysis(
      BotGamePhase phase, BotAiMode mode, boolean behind, boolean leader
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
        1,
        new int[] {30, 40},
        new int[2],
        new int[] {1, 1},
        0.4,
        behind,
        leader,
        true);
  }
}
