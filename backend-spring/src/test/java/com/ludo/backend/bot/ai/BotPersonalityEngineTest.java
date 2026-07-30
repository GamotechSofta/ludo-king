package com.ludo.backend.bot.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.room.BotDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BotPersonalityEngineTest {

  private AIScoreConfig scoreConfig;
  private DecisionModifier modifier;
  private PersonalityHistory history;

  @BeforeEach
  void setUp() {
    scoreConfig =
        new AIScoreConfig(
            true, 150, 120, 40, 100, 70, 90, 70, 60, 100, 90, 10, 80, 60, 40, 30, 50, 80, 80, 120,
            150, 70, 40, 30, 70, 100, 10, 6, 10, 5);
    modifier = new DecisionModifier(scoreConfig);
    history = new PersonalityHistory();
  }

  @Test
  void balancedWeightsNeutral() {
    BehaviorWeights w = BehaviorWeights.forType(BotPersonality.BALANCED);
    assertEquals(1.0, w.capture(), 0.001);
    assertEquals(1.0, w.home(), 0.001);
    assertEquals(1.0, w.escape(), 0.001);
  }

  @Test
  void aggressiveBoostsCaptureAndRisk() {
    BehaviorWeights w = BehaviorWeights.forType(BotPersonality.AGGRESSIVE);
    assertEquals(1.35, w.capture(), 0.001);
    assertEquals(1.20, w.risk(), 0.001);
    assertEquals(0.90, w.home(), 0.001);
    assertEquals(1.25, w.leaderTarget(), 0.001);
  }

  @Test
  void defensiveBoostsEscapeAndSafe() {
    BehaviorWeights w = BehaviorWeights.forType(BotPersonality.DEFENSIVE);
    assertEquals(1.40, w.escape(), 0.001);
    assertEquals(1.35, w.safe(), 0.001);
    assertEquals(0.75, w.capture(), 0.001);
  }

  @Test
  void speedRunnerBoostsHomeAndFuture() {
    BehaviorWeights w = BehaviorWeights.forType(BotPersonality.SPEED_RUNNER);
    assertEquals(1.45, w.home(), 0.001);
    assertEquals(0.80, w.capture(), 0.001);
    assertEquals(1.30, w.future(), 0.001);
  }

  @Test
  void opportunistBoostsFutureAndLeader() {
    BehaviorWeights w = BehaviorWeights.forType(BotPersonality.OPPORTUNIST);
    assertEquals(1.25, w.future(), 0.001);
    assertEquals(1.20, w.leaderTarget(), 0.001);
    assertEquals(0.90, w.risk(), 0.001);
  }

  @Test
  void assignsOncePerSeat() {
    PersonalityConfig cfg = new PersonalityConfig(true, "fixed", "Aggressive", true, 0.0);
    BotPersonalityEngine engine =
        new BotPersonalityEngine(cfg, new PersonalitySelector(cfg), history, modifier);
    PersonalityProfile a =
        engine.evaluate("r1", 1, BotDifficulty.HARD, midAnalysis(false, false), null);
    PersonalityProfile b =
        engine.evaluate("r1", 1, BotDifficulty.HARD, midAnalysis(false, false), null);
    assertEquals(BotPersonality.AGGRESSIVE, a.personality());
    assertEquals(BotPersonality.AGGRESSIVE, b.personality());
    assertTrue(a.enabled());
  }

  @Test
  void easyMediumSkipped() {
    PersonalityConfig cfg = new PersonalityConfig(true, "random", "Balanced", true, 0.05);
    BotPersonalityEngine engine =
        new BotPersonalityEngine(cfg, new PersonalitySelector(cfg), history, modifier);
    PersonalityProfile p =
        engine.evaluate("r", 0, BotDifficulty.EASY, midAnalysis(false, false), null);
    assertFalse(p.enabled());
  }

  @Test
  void aggressiveRecoveryWhenBehind() {
    PersonalityConfig cfg = new PersonalityConfig(true, "fixed", "Aggressive", true, 0.0);
    BotPersonalityEngine engine =
        new BotPersonalityEngine(cfg, new PersonalitySelector(cfg), history, modifier);
    PersonalityProfile p =
        engine.evaluate(
            "evo", 0, BotDifficulty.HARD, midAnalysis(true, false), adaptiveBehind());
    assertEquals(BotPersonality.AGGRESSIVE, p.personality());
    assertTrue(
        p.evolutionLabel().contains("Recovery") || p.weights().home() > 0.90,
        p.debugLine());
  }

  @Test
  void endgameConvergesFinishFocused() {
    PersonalityConfig cfg = new PersonalityConfig(true, "fixed", "Aggressive", true, 0.0);
    BotPersonalityEngine engine =
        new BotPersonalityEngine(cfg, new PersonalitySelector(cfg), history, modifier);
    PersonalityProfile p =
        engine.evaluate("end", 0, BotDifficulty.HARD, endAnalysis(false, true), null);
    assertTrue(
        "Finish Focused".equals(p.evolutionLabel()) || p.weights().home() > 1.0,
        p.debugLine());
  }

  @Test
  void randomVarianceChangesWeights() {
    BehaviorWeights base = BehaviorWeights.forType(BotPersonality.BALANCED);
    BehaviorWeights a = base.withVariance(0.05, new double[] {0, 0, 0, 0, 0, 0, 0, 0, 0});
    BehaviorWeights b = base.withVariance(0.05, new double[] {1, 1, 1, 1, 1, 1, 1, 1, 1});
    assertNotEquals(a.capture(), b.capture(), 0.0001);
    assertTrue(Math.abs(a.capture() - 1.0) <= 0.06);
    assertTrue(Math.abs(b.capture() - 1.0) <= 0.06);
  }

  @Test
  void decisionModifierAffectsCapture() {
    PersonalityProfile aggressive =
        new PersonalityProfile(
            BotPersonality.AGGRESSIVE,
            BehaviorWeights.forType(BotPersonality.AGGRESSIVE),
            BehaviorWeights.forType(BotPersonality.AGGRESSIVE),
            "",
            true);
    MoveCandidate capture =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            1,
            0,
            10,
            14,
            MoveType.CAPTURE,
            false,
            0,
            true,
            1,
            false,
            20,
            false,
            50,
            false,
            false);
    MoveScore score = new MoveScore();
    score.add("base", 0);
    modifier.apply(score, capture, aggressive);
    assertTrue(score.total() > 0, score.toString());
    assertTrue(score.reasons().stream().anyMatch(r -> r.label().contains("Personality Capture")));
  }

  @Test
  void futureMultiplierSpeedRunner() {
    PersonalityProfile speed =
        new PersonalityProfile(
            BotPersonality.SPEED_RUNNER,
            BehaviorWeights.forType(BotPersonality.SPEED_RUNNER),
            BehaviorWeights.forType(BotPersonality.SPEED_RUNNER),
            "",
            true);
    assertEquals(1.30, modifier.futureMultiplier(speed), 0.001);
  }

  private static DifficultyProfile adaptiveBehind() {
    return new DifficultyProfile(
        BotStatus.BEHIND,
        AdaptiveStrategy.RECOVERY,
        80,
        0.35,
        20,
        10,
        10,
        15,
        10,
        -10,
        0,
        1.1,
        false,
        "test",
        true);
  }

  private static BotMatchAnalysis midAnalysis(boolean behind, boolean leader) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        leader ? 0 : 1,
        new int[] {40, 50},
        new int[] {0, 0},
        new int[] {2, 2},
        0.4,
        behind,
        leader,
        true);
  }

  private static BotMatchAnalysis endAnalysis(boolean behind, boolean leader) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.END,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        leader ? 0 : 1,
        new int[] {180, 100},
        new int[] {3, 1},
        new int[] {1, 2},
        0.85,
        behind,
        leader,
        true);
  }
}
