package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HumanJailExitAssistTest {

  private HumanJailExitAssist assist() {
    return new HumanJailExitAssist(true, 2);
  }

  @Test
  void detectsBotOneCellBeforeStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {51, -1, -1, -1},
    };
    assertTrue(
        assist.isBotNearStartingPath(
            0,
            2,
            tokens,
            new boolean[] {false, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void detectsBotSixCellsAheadOfStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {6, -1, -1, -1},
    };
    assertTrue(
        assist.isBotNearStartingPath(
            0,
            2,
            tokens,
            new boolean[] {false, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void ignoresHumanOpponentNearStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {51, -1, -1, -1},
    };
    assertFalse(
        assist.isBotNearStartingPath(
            0,
            2,
            tokens,
            new boolean[] {false, false},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void ignoresOpponentOnStartTile() {
    assertFalse(HumanJailExitAssist.isWithinStepsOfStart(0, 0, 1, 6));
  }

  @Test
  void ignoresBotTooFarFromStart() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {20, -1, -1, -1},
    };
    assertFalse(
        assist.isBotNearStartingPath(
            0,
            2,
            tokens,
            new boolean[] {false, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void forcesSixWhenBotInRangeAndHumanHasJailedPawn() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, 10, -1}, // human: 1 out, 3 jailed
        {2, -1, -1, -1}, // bot near RED start 0
    };
    assertEquals(
        6,
        assist.maybeForceJailExitSix(
            true,
            0,
            0,
            tokens[0],
            2,
            tokens,
            new boolean[] {false, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void allowsSecondForcedSixButNotThird() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {3, -1, -1, -1},
    };
    boolean[] isBot = {false, true};
    boolean[] flags = {false, false};

    assertEquals(
        6,
        assist.maybeForceJailExitSix(
            true, 0, 0, tokens[0], 2, tokens, isBot, flags, flags, 0));
    assertEquals(
        6,
        assist.maybeForceJailExitSix(
            true, 0, 0, tokens[0], 2, tokens, isBot, flags, flags, 1));
    assertNull(
        assist.maybeForceJailExitSix(
            true, 0, 0, tokens[0], 2, tokens, isBot, flags, flags, 2));
  }

  @Test
  void doesNotForceForBotSeats() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {-1, -1, -1, -1},
        {2, -1, -1, -1},
    };
    assertNull(
        assist.maybeForceJailExitSix(
            false,
            0,
            0,
            tokens[0],
            2,
            tokens,
            new boolean[] {true, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void doesNotForceWhenNoJailedPawn() {
    HumanJailExitAssist assist = assist();
    int[][] tokens = {
        {1, 2, 3, 4},
        {5, -1, -1, -1},
    };
    assertNull(
        assist.maybeForceJailExitSix(
            true,
            0,
            0,
            tokens[0],
            2,
            tokens,
            new boolean[] {false, true},
            new boolean[] {false, false},
            new boolean[] {false, false},
            0));
  }

  @Test
  void maxExitsClampedToTwo() {
    HumanJailExitAssist assist = new HumanJailExitAssist(true, 99);
    assertEquals(2, assist.maxExits());
  }
}
