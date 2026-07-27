package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ludo.backend.game.GameSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotKillDiceAssistTest {

  @Test
  void returnsExactDiceWhenOpponentIsReachable() {
    // GREEN at 13, YELLOW alone at 16 → 3 steps ahead
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of("GREEN", Arrays.asList(13, JAIL, JAIL, JAIL), "YELLOW", Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    Integer dice =
        BotKillDiceAssist.pickCaptureDice(
            snap, 0, (token, d) -> token == 0 && d == 3);

    assertEquals(3, dice);
  }

  @Test
  void returnsNullWhenNoCaptureInOneToSix() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of("GREEN", Arrays.asList(13, JAIL, JAIL, JAIL), "YELLOW", Arrays.asList(30, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    assertNull(
        BotKillDiceAssist.pickCaptureDice(snap, 0, (token, d) -> false));
  }

  @Test
  void ignoresHumanSeats() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {false, false},
            Map.of("GREEN", Arrays.asList(13, JAIL, JAIL, JAIL), "YELLOW", Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    assertNull(
        BotKillDiceAssist.pickCaptureDice(snap, 0, (token, d) -> true));
  }

  @Test
  void prefersVictimClosestToHome() {
    // Two captures: dice 2 for far victim, dice 1 for victim closer to home
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "GREEN",
                Arrays.asList(10, 20, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(11, 21, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    Integer dice =
        BotKillDiceAssist.pickCaptureDice(
            snap,
            0,
            (token, d) ->
                (token == 0 && d == 1) || (token == 1 && d == 1));

    assertEquals(1, dice);
  }

  private static GameSnapshot snapshot(
      int currentSeat,
      boolean[] isBot,
      Map<String, List<Integer>> positions,
      List<String> seatColors
  ) {
    GameSnapshot snap = new GameSnapshot();
    snap.setCurrentSeatIndex(currentSeat);
    snap.setIsBot(isBot);
    snap.setSeatColors(seatColors);
    snap.setTokenPositions(new HashMap<>(positions));
    snap.setPhase("AWAITING_ROLL");
    return snap;
  }
}
