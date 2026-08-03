package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EarlyKillDelayTest {

  @Test
  void detectsBotVsHumanOnly() {
    assertTrue(EarlyKillDelay.isBotVsHumanMatch(new boolean[] {true, false}));
    assertTrue(EarlyKillDelay.isBotVsHumanMatch(new boolean[] {false, true, true, true}));
    assertFalse(EarlyKillDelay.isBotVsHumanMatch(new boolean[] {false, false}));
    assertFalse(EarlyKillDelay.isBotVsHumanMatch(new boolean[] {true, true}));
  }

  @Test
  void neverSuppressesKillMovesWhenKillChanceExists() {
    GameEngineService.MatchRuntime rt = runtime(true, false);
    rt.diceList.add(2);
    List<int[]> raw = List.of(new int[] {0, 0}, new int[] {1, 0});
    EarlyKillDelay.CaptureProbe killOnToken0 = (seat, token, dice) -> token == 0;

    // Even on first / second opportunity, kills stay in the legal set
    for (int i = 0; i < 3; i++) {
      EarlyKillDelay.noteOpportunity(rt, 0, raw, killOnToken0);
      assertFalse(rt.earlyKillSuppressActive[0]);
      List<int[]> filtered = EarlyKillDelay.filterMoves(rt, 0, raw, killOnToken0);
      assertEquals(2, filtered.size());
    }
  }

  @Test
  void humanKillAlsoNeverSuppressed() {
    GameEngineService.MatchRuntime rt = runtime(true, false);
    rt.diceList.add(2);
    List<int[]> raw = List.of(new int[] {0, 0}, new int[] {1, 0});
    EarlyKillDelay.CaptureProbe killOnToken0 = (seat, token, dice) -> token == 0;

    EarlyKillDelay.noteOpportunity(rt, 1, raw, killOnToken0);
    assertFalse(rt.earlyKillSuppressActive[1]);
    assertEquals(raw, EarlyKillDelay.filterMoves(rt, 1, raw, killOnToken0));
  }

  @Test
  void resetAfterSuccessfulCapture() {
    GameEngineService.MatchRuntime rt = runtime(false, true);
    rt.earlyKillSkipCount[0] = 2;
    rt.earlyKillSuppressActive[0] = true;

    EarlyKillDelay.onSuccessfulCapture(rt, 0);

    assertEquals(0, rt.earlyKillSkipCount[0]);
    assertFalse(rt.earlyKillSuppressActive[0]);
  }

  @Test
  void doesNothingInHumanOnlyMatch() {
    GameEngineService.MatchRuntime rt = runtime(false, false);
    rt.diceList.add(2);
    List<int[]> raw = List.of(new int[] {0, 0});

    EarlyKillDelay.noteOpportunity(rt, 0, raw, (s, t, d) -> true);

    assertEquals(0, rt.earlyKillSkipCount[0]);
    assertFalse(rt.earlyKillSuppressActive[0]);
    assertEquals(raw, EarlyKillDelay.filterMoves(rt, 0, raw, (s, t, d) -> true));
  }

  private static GameEngineService.MatchRuntime runtime(boolean seat0Bot, boolean seat1Bot) {
    return new GameEngineService.MatchRuntime(
        "early-kill",
        List.of(
            new GameEngineService.SeatInfo("u0", "P0", LudoColor.RED, seat0Bot),
            new GameEngineService.SeatInfo("u1", "P1", LudoColor.YELLOW, seat1Bot)));
  }
}
