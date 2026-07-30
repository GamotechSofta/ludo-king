package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HumanBehaviorEngineTest {

  private BehaviorConfig config;
  private HumanBehaviorEngine engine;
  private AIScoreConfig scoreConfig;

  @BeforeEach
  void setUp() {
    config = new BehaviorConfig(true, 30, 0.10, 0.55);
    engine =
        new HumanBehaviorEngine(
            config, new BehaviorAnalyzer(), new PatternDetector(), new BehaviorPredictor());
    scoreConfig =
        new AIScoreConfig(
            true, 150, 120, 40, 100, 70, 90, 70, 60, 100, 90, 10, 80, 60, 40, 30, 50, 80, 80, 120,
            150, 70, 40, 30, 70, 100, 10, 6, 10, 5);
  }

  @Test
  void detectsAggressivePlayer() {
    seedAggressive("r1", 1);
    BehaviorProfile p = engine.evaluate("r1", BotDifficulty.HARD, analysisHumanAt(1), null);
    assertTrue(p.enabled());
    assertEquals(HumanPlayStyle.AGGRESSIVE, p.style(), p.debugLine());
    assertTrue(p.styleConfidence() >= 0.55, p.debugLine());
  }

  @Test
  void detectsDefensivePlayer() {
    seedDefensive("r2", 1);
    BehaviorProfile p = engine.evaluate("r2", BotDifficulty.HARD, analysisHumanAt(1), null);
    assertTrue(
        p.style() == HumanPlayStyle.DEFENSIVE || p.style() == HumanPlayStyle.SAFE_PLAYER,
        p.debugLine());
  }

  @Test
  void multiHumanProfilesSeparate() {
    seedAggressive("rm", 1);
    seedDefensive("rm", 2);
    BehaviorProfile a =
        engine.evaluate(
            "rm",
            BotDifficulty.HARD,
            analysisHumans(new boolean[] {true, false, false}, 1),
            null);
    // primary human is first human seat (1)
    assertEquals(1, a.humanSeat());
    assertEquals(HumanPlayStyle.AGGRESSIVE, a.style());
  }

  @Test
  void resetClearsMatchMemory() {
    seedAggressive("rx", 1);
    engine.clear("rx");
    BehaviorProfile p = engine.evaluate("rx", BotDifficulty.HARD, analysisHumanAt(1), null);
    assertFalse(p.enabled());
  }

  @Test
  void lowConfidenceNotInfluential() {
    // Only 2 moves — insufficient
    engine.observeMove(
        "low", 1, false, LudoColor.YELLOW, 0, 3, 10, 13, false, tokens(), colors());
    engine.observeMove(
        "low", 1, false, LudoColor.YELLOW, 0, 2, 13, 15, false, tokens(), colors());
    BehaviorProfile p = engine.evaluate("low", BotDifficulty.HARD, analysisHumanAt(1), null);
    assertFalse(p.influential());
  }

  @Test
  void influenceCappedAtTenPercent() {
    seedAggressive("cap", 1);
    BehaviorProfile p = engine.evaluate("cap", BotDifficulty.HARD, analysisHumanAt(1), null);
    MoveCandidate escape =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            2,
            0,
            10,
            8,
            MoveType.ESCAPE,
            true,
            0,
            false,
            -1,
            false,
            Integer.MAX_VALUE,
            false,
            80,
            false,
            false);
    MoveScore score = new MoveScore();
    score.add("base", 100);
    engine.apply(score, escape, p, scoreConfig);
    int delta = score.total() - 100;
    int cap =
        (int) Math.round(Math.abs(scoreConfig.escapeBonus() + scoreConfig.safeBonus()) * 0.10);
    assertTrue(Math.abs(delta) <= cap + 1, "delta=" + delta + " cap=" + cap);
  }

  @Test
  void easyBotSkipped() {
    seedAggressive("ez", 1);
    BehaviorProfile p = engine.evaluate("ez", BotDifficulty.EASY, analysisHumanAt(1), null);
    assertFalse(p.enabled());
  }

  @Test
  void neverObservesBotSeats() {
    engine.observeRoll("b", 0, true, 6);
    engine.observeMove(
        "b", 0, true, LudoColor.GREEN, 0, 6, JAIL, 1, false, tokens(), colors());
    BehaviorProfile p =
        engine.evaluate(
            "b",
            BotDifficulty.HARD,
            analysisHumans(new boolean[] {true, false}, 1),
            null);
    assertFalse(p.enabled()); // no human memory for seat 1
  }

  @Test
  void analysisUnderOneMs() {
    seedAggressive("perf", 1);
    long t0 = System.nanoTime();
    for (int i = 0; i < 50; i++) {
      engine.evaluate("perf", BotDifficulty.HARD, analysisHumanAt(1), null);
    }
    long avgUs = (System.nanoTime() - t0) / 50 / 1_000L;
    assertTrue(avgUs < 1000, "avg " + avgUs + "µs");
  }

  private void seedAggressive(String room, int seat) {
    LudoColor c = LudoColor.YELLOW;
    for (int i = 0; i < 8; i++) {
      engine.observeRoll(room, seat, false, 4);
      engine.observeMove(
          room, seat, false, c, 0, 4, 10 + i, 14 + i, true, tokens(), colors());
    }
  }

  private void seedDefensive(String room, int seat) {
    LudoColor c = LudoColor.YELLOW;
    int safe = firstSafe();
    for (int i = 0; i < 8; i++) {
      engine.observeRoll(room, seat, false, 2);
      engine.observeMove(
          room, seat, false, c, 1, 2, safe, safe, false, tokens(), colors());
    }
  }

  private static int firstSafe() {
    for (int i = 0; i < 52; i++) {
      if (com.ludo.backend.game.BoardConstants.isSafe(i)) {
        return i;
      }
    }
    return 8;
  }

  private static int[][] tokens() {
    return new int[][] {
      {5, JAIL, JAIL, JAIL},
      {12, JAIL, JAIL, JAIL},
      {JAIL, JAIL, JAIL, JAIL},
      {JAIL, JAIL, JAIL, JAIL}
    };
  }

  private static LudoColor[] colors() {
    return new LudoColor[] {LudoColor.GREEN, LudoColor.YELLOW, LudoColor.BLUE, LudoColor.RED};
  }

  private static BotMatchAnalysis analysisHumanAt(int humanSeat) {
    boolean[] bots = new boolean[2];
    bots[0] = true;
    bots[humanSeat] = false;
    return analysisHumans(bots, humanSeat);
  }

  private static BotMatchAnalysis analysisHumans(boolean[] isBot, int focusHuman) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        0,
        1,
        1,
        isBot.length,
        isBot,
        focusHuman,
        new int[] {40, 50},
        new int[] {0, 0},
        new int[] {2, 2},
        0.4,
        false,
        false,
        true);
  }
}
