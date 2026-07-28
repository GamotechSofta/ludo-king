package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.config.HumanJailAssistProperties;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HumanJailDiceAssistTest {

  private HumanJailDiceAssist assist() {
    return new HumanJailDiceAssist(
        new HumanJailAssistProperties(true, 6, 2, 0.05, 0.45));
  }

  @Test
  void firstTwoAttemptsUseFairSixProbability() {
    HumanJailDiceAssist assist = assist();
    assertEquals(HumanJailDiceAssist.FAIR_SIX_PROBABILITY, assist.sixProbability(0), 1e-9);
    assertEquals(HumanJailDiceAssist.FAIR_SIX_PROBABILITY, assist.sixProbability(1), 1e-9);
  }

  @Test
  void thirdAttemptStartsBoostingSixProbability() {
    HumanJailDiceAssist assist = assist();
    assertTrue(assist.sixProbability(2) > HumanJailDiceAssist.FAIR_SIX_PROBABILITY);
    assertEquals(HumanJailDiceAssist.FAIR_SIX_PROBABILITY + 0.05, assist.sixProbability(2), 1e-9);
    assertEquals(HumanJailDiceAssist.FAIR_SIX_PROBABILITY + 0.10, assist.sixProbability(3), 1e-9);
  }

  @Test
  void sixProbabilityCapsAtConfiguredMaximum() {
    HumanJailDiceAssist assist = assist();
    assertEquals(0.45, assist.sixProbability(20), 1e-9);
  }

  @Test
  void disabledAssistAlwaysUsesFairProbability() {
    HumanJailDiceAssist assist =
        new HumanJailDiceAssist(
            new HumanJailAssistProperties(false, 6, 2, 0.05, 0.45));
    assertFalse(assist.isEnabled());
    assertEquals(HumanJailDiceAssist.FAIR_SIX_PROBABILITY, assist.sixProbability(10), 1e-9);
  }

  @Test
  void rollDiceNeverReturnsOutsideOneToSix() {
    HumanJailDiceAssist assist = assist();
    Random rng = new Random(7L);
    for (int streak = 0; streak < 12; streak++) {
      for (int i = 0; i < 200; i++) {
        int value = assist.rollDice(rng, streak);
        assertTrue(value >= 1 && value <= 6);
      }
    }
  }

  @Test
  void boostedStreakRollsSixMoreOftenThanFairDice() {
    HumanJailDiceAssist assist = assist();
    Random fairRng = new Random(11L);
    Random boostedRng = new Random(11L);

    int fairSixes = 0;
    int boostedSixes = 0;
    int trials = 20_000;
    for (int i = 0; i < trials; i++) {
      if (fairRng.nextInt(6) + 1 == 6) {
        fairSixes++;
      }
      if (assist.rollDice(boostedRng, 5) == 6) {
        boostedSixes++;
      }
    }
    assertTrue(boostedSixes > fairSixes);
  }

  @Test
  void allTokensInJailDetectsFullBase() {
    assertTrue(HumanJailDiceAssist.allTokensInJail(new int[] {-1, -1, -1, -1}));
    assertFalse(HumanJailDiceAssist.allTokensInJail(new int[] {10, -1, -1, -1}));
    assertFalse(HumanJailDiceAssist.allTokensInJail(new int[] {}));
  }
}
