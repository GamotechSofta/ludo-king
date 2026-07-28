package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.config.HumanJailAssistProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameEngineForfeitTest {

  private GameEngineService engine;

  @BeforeEach
  void setUp() {
    HumanJailDiceAssist jailAssist =
        new HumanJailDiceAssist(
            new HumanJailAssistProperties(false, 6, 2, 0.05, 0.45));
    HumanJailExitAssist exitAssist = new HumanJailExitAssist(false, 70);
    engine = new GameEngineService(jailAssist, exitAssist);
  }

  @Test
  void forfeitEndsTwoPlayerHumanMatchWithOpponentWin() {
    List<GameEngineService.SeatInfo> seats = List.of(
        new GameEngineService.SeatInfo("u1", "Player 1", LudoColor.RED, false),
        new GameEngineService.SeatInfo("u2", "Player 2", LudoColor.YELLOW, false)
    );
    engine.createMatch("room-forfeit", seats);

    GameSnapshot snap = engine.forfeitOnExit("room-forfeit", "u1");

    assertEquals(GameEngineService.PHASE_FINISHED, snap.getPhase());
    assertEquals(Integer.valueOf(1), snap.getWinnerSeat());
    assertEquals(0, snap.getStandings().get(0));
    assertEquals(1, snap.getStandings().get(1));
    assertTrue(snap.getEliminated()[0]);
    assertEquals("FORFEIT", snap.getLastActionType());
  }

  @Test
  void forfeitRemovesFourPlayerFromRotationAndContinuesMatch() {
    List<GameEngineService.SeatInfo> seats = List.of(
        new GameEngineService.SeatInfo("a", "A", LudoColor.RED, false),
        new GameEngineService.SeatInfo("b", "B", LudoColor.GREEN, false),
        new GameEngineService.SeatInfo("c", "C", LudoColor.YELLOW, false),
        new GameEngineService.SeatInfo("d", "D", LudoColor.BLUE, false)
    );
    engine.createMatch("room-4p", seats);
    GameSnapshot before = engine.getSnapshot("room-4p");
    int seatB = 1;

    GameSnapshot snap = engine.forfeitOnExit("room-4p", "b");

    assertEquals(GameEngineService.PHASE_ROLL, snap.getPhase());
    assertTrue(snap.getEliminated()[seatB]);
    assertEquals(0, snap.getStandings().get(seatB));
    assertEquals("FORFEIT", snap.getLastActionType());
    if (before.getCurrentSeatIndex() == seatB) {
      assertEquals(2, snap.getCurrentSeatIndex());
    }
  }

  @Test
  void forfeitIsIdempotentWhenAlreadyFinished() {
    List<GameEngineService.SeatInfo> seats = List.of(
        new GameEngineService.SeatInfo("u1", "Player 1", LudoColor.RED, false),
        new GameEngineService.SeatInfo("u2", "Player 2", LudoColor.YELLOW, false)
    );
    engine.createMatch("room-forfeit2", seats);
    engine.forfeitOnExit("room-forfeit2", "u1");
    GameSnapshot again = engine.forfeitOnExit("room-forfeit2", "u2");
    assertEquals(GameEngineService.PHASE_FINISHED, again.getPhase());
    assertEquals(Integer.valueOf(1), again.getWinnerSeat());
  }
}
