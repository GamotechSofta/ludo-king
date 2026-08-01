package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.ai.HumanBehaviorEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Engine-level: first 3 cross-side kill opportunities skipped for human and bot;
 * 4th allowed; counter resets after capture. Bot vs Human only.
 */
class EarlyKillDelayEngineTest {

  private GameEngineService engine;

  @BeforeEach
  void setUp() {
    engine = new GameEngineService(emptyBehavior());
  }

  @Test
  void botSkipsFirstThreeKillOpportunitiesThenCapturesOnFourth() {
    String room = "ekd-bot";
    createBotVsHuman(room);

    // Opportunities 1–3: kill available but non-kill also available → kill filtered out
    for (int i = 0; i < 3; i++) {
      prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
      GameSnapshot rolled = engine.rollDiceAsSeat(room, 0, 2);
      assertEquals(GameEngineService.PHASE_MOVE, rolled.getPhase());
      assertFalse(legalContainsCaptureOn(room, 12), "kill should be suppressed on opportunity " + (i + 1));
      // Play non-kill: token 1 from 5 + 2 → 7
      engine.moveTokenAsSeat(room, 0, 1, 0);
    }

    // 4th opportunity: kill allowed
    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 0, 2);
    assertTrue(legalContainsCaptureOn(room, 12), "4th kill opportunity must be legal");
    GameSnapshot after = engine.moveTokenAsSeat(room, 0, 0, 0);
    assertEquals(Integer.valueOf(12), after.getLastActionTo());
    assertEquals(JAIL, after.getTokenPositions().get("YELLOW").get(0));
  }

  @Test
  void humanSkipsFirstThreeKillOpportunitiesThenCapturesOnFourth() {
    String room = "ekd-human";
    createBotVsHuman(room);

    for (int i = 0; i < 3; i++) {
      // Human YELLOW seat 1 at 14 can kill bot RED at 16 with dice 2; also has pawn at 20
      prepareTurn(room, 1, List.of(16, JAIL, JAIL, JAIL), List.of(14, 20, JAIL, JAIL));
      GameSnapshot rolled = engine.rollDiceAsSeat(room, 1, 2);
      assertEquals(GameEngineService.PHASE_MOVE, rolled.getPhase());
      assertFalse(legalContainsCaptureOn(room, 16), "human kill suppressed on opportunity " + (i + 1));
      assertThrows(
          IllegalStateException.class,
          () -> engine.moveTokenAsSeat(room, 1, 0, 0),
          "direct kill move must be rejected while delayed");
      // Non-kill: token 1 from 20 + 2 → 22
      engine.moveTokenAsSeat(room, 1, 1, 0);
    }

    prepareTurn(room, 1, List.of(16, JAIL, JAIL, JAIL), List.of(14, 20, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 1, 2);
    assertTrue(legalContainsCaptureOn(room, 16));
    GameSnapshot after = engine.moveTokenAsSeat(room, 1, 0, 0);
    assertEquals(JAIL, after.getTokenPositions().get("RED").get(0));
  }

  @Test
  void doesNotApplyInHumanVsHuman() {
    String room = "ekd-hvsh";
    engine.createMatch(
        room,
        List.of(
            new GameEngineService.SeatInfo("u0", "H1", LudoColor.RED, false),
            new GameEngineService.SeatInfo("u1", "H2", LudoColor.YELLOW, false)));

    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 0, 2);
    assertTrue(legalContainsCaptureOn(room, 12), "HvH must allow immediate kill");
  }

  @Test
  void captureResetsCounterSoNextThreeAreDelayedAgain() {
    String room = "ekd-reset";
    createBotVsHuman(room);

    for (int i = 0; i < 3; i++) {
      prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
      engine.rollDiceAsSeat(room, 0, 2);
      engine.moveTokenAsSeat(room, 0, 1, 0);
    }
    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 0, 2);
    engine.moveTokenAsSeat(room, 0, 0, 0); // capture → reset

    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 0, 2);
    assertFalse(legalContainsCaptureOn(room, 12), "after capture, delay restarts");
  }

  private void createBotVsHuman(String room) {
    engine.createMatch(
        room,
        List.of(
            new GameEngineService.SeatInfo("bot", "Bot", LudoColor.RED, true),
            new GameEngineService.SeatInfo("human", "Human", LudoColor.YELLOW, false)));
  }

  private void prepareTurn(
      String room, int seat, List<Integer> redTokens, List<Integer> yellowTokens
  ) {
    GameSnapshot snap = engine.getSnapshot(room);
    Map<String, List<Integer>> pos = new HashMap<>();
    pos.put("RED", redTokens);
    pos.put("YELLOW", yellowTokens);
    snap.setTokenPositions(pos);
    snap.setPhase(GameEngineService.PHASE_ROLL);
    snap.setCurrentSeatIndex(seat);
    snap.setDiceList(List.of());
    snap.setDiceValue(0);
    // Keep actionSeq high enough so restore applies
    snap.setActionSeq(snap.getActionSeq() + 1);
    engine.restoreFromSnapshot(snap);
  }

  private boolean legalContainsCaptureOn(String room, int landPos) {
    GameSnapshot snap = engine.getSnapshot(room);
    if (snap.getLegalMoves() == null) {
      return false;
    }
    String color = snap.getSeatColors().get(snap.getCurrentSeatIndex());
    List<Integer> own = snap.getTokenPositions().get(color);
    int dice = snap.getDiceList().get(0);
    for (Map<String, Integer> m : snap.getLegalMoves()) {
      int token = m.get("tokenIndex");
      int from = own.get(token);
      int to = from + dice; // main-path only setups in these tests (no wrap / exit)
      if (to == landPos) {
        return true;
      }
    }
    return false;
  }

  private static ObjectProvider<HumanBehaviorEngine> emptyBehavior() {
    return new ObjectProvider<>() {
      @Override
      public HumanBehaviorEngine getObject() {
        return null;
      }

      @Override
      public HumanBehaviorEngine getObject(Object... args) {
        return null;
      }

      @Override
      public HumanBehaviorEngine getIfAvailable() {
        return null;
      }

      @Override
      public HumanBehaviorEngine getIfUnique() {
        return null;
      }
    };
  }
}
