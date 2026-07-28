package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class HumanJailExitAssistTest {

  private HumanJailExitAssist assist() {
    return new HumanJailExitAssist(true, 70);
  }

  @Test
  void detectsOpponentOneCellBeforeStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {51, -1, -1, -1},
    };
    assertTrue(
        assist.isOpponentNearStartingPath(
            0, 2, tokens, new boolean[] {false, false}, new boolean[] {false, false}, 0));
  }

  @Test
  void detectsOpponentSixCellsAheadOfStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {6, -1, -1, -1},
    };
    assertTrue(
        assist.isOpponentNearStartingPath(
            0, 2, tokens, new boolean[] {false, false}, new boolean[] {false, false}, 0));
  }

  @Test
  void ignoresOpponentOnStartTile() {
    assertFalse(HumanJailExitAssist.isWithinStepsOfStart(0, 0, 1, 6));
  }

  @Test
  void ignoresOpponentTooFarFromStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {20, -1, -1, -1},
    };
    assertFalse(
        assist.isOpponentNearStartingPath(
            0, 2, tokens, new boolean[] {false, false}, new boolean[] {false, false}, 0));
  }

  @Test
  void ignoresSelfTokens() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {1, -1, -1, -1},
        {-1, -1, -1, -1},
    };
    assertFalse(
        assist.isOpponentNearStartingPath(
            0, 2, tokens, new boolean[] {false, false}, new boolean[] {false, false}, 0));
  }

  @Test
  void rollDiceReturnsValidValues() {
    HumanJailExitAssist assist = assist();
    Random rng = new Random(3L);
    for (int i = 0; i < 500; i++) {
      int value = assist.rollDice(rng);
      assertTrue(value >= 1 && value <= 6);
    }
  }

  @Test
  void boostedRollsSixMoreOftenThanFairDice() {
    HumanJailExitAssist assist = new HumanJailExitAssist(true, 70);
    Random fairRng = new Random(19L);
    Random boostedRng = new Random(19L);
    int fairSixes = 0;
    int boostedSixes = 0;
    int trials = 20_000;
    for (int i = 0; i < trials; i++) {
      if (fairRng.nextInt(6) + 1 == 6) {
        fairSixes++;
      }
      if (assist.rollDice(boostedRng) == 6) {
        boostedSixes++;
      }
    }
    assertTrue(boostedSixes > fairSixes);
  }

  @Test
  void assistChanceIsClampedBelowGuarantee() {
    HumanJailExitAssist assist = new HumanJailExitAssist(true, 100);
    assertEquals(99, assist.assistChancePct());
  }
}
