package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.ai.HumanBehaviorEngine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Engine-level: kill opportunities stay legal on every chance (kill priority).
 */
class EarlyKillDelayEngineTest {

  private GameEngineService engine;

  @BeforeEach
  void setUp() {
    engine = new GameEngineService(emptyBehavior());
  }

  @Test
  void botKillStaysLegalOnFirstOpportunity() {
    String room = "ekd-bot";
    createBotVsHuman(room);

    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    GameSnapshot rolled = engine.rollDiceAsSeat(room, 0, 2);
    assertEquals(GameEngineService.PHASE_MOVE, rolled.getPhase());
    assertTrue(legalContainsCaptureOn(room, 12), "1st kill chance must stay legal");
    GameSnapshot after = engine.moveTokenAsSeat(room, 0, 0, 0);
    assertEquals(JAIL, after.getTokenPositions().get("YELLOW").get(0));
  }

  @Test
  void humanKillStaysLegalOnFirstOpportunity() {
    String room = "ekd-human";
    createBotVsHuman(room);

    prepareTurn(room, 1, List.of(16, JAIL, JAIL, JAIL), List.of(14, 20, JAIL, JAIL));
    GameSnapshot rolled = engine.rollDiceAsSeat(room, 1, 2);
    assertEquals(GameEngineService.PHASE_MOVE, rolled.getPhase());
    assertTrue(legalContainsCaptureOn(room, 16), "human 1st kill chance must stay legal");
    GameSnapshot after = engine.moveTokenAsSeat(room, 1, 0, 0);
    assertEquals(JAIL, after.getTokenPositions().get("RED").get(0));
  }

  @Test
  void doesNotBlockInHumanVsHuman() {
    String room = "ekd-hvsh";
    engine.createMatch(
        room,
        List.of(
            new GameEngineService.SeatInfo("u0", "H1", LudoColor.RED, false),
            new GameEngineService.SeatInfo("u1", "H2", LudoColor.YELLOW, false)));

    prepareTurn(room, 0, List.of(10, 5, JAIL, JAIL), List.of(12, JAIL, JAIL, JAIL));
    engine.rollDiceAsSeat(room, 0, 2);
    assertTrue(legalContainsCaptureOn(room, 12));
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
      int to = from + dice;
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
