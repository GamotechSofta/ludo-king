package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_BASE;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ludo.backend.game.GameSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BotHomeDiceAssistTest {

  @Test
  void returnsExactDiceWhenHomeIsOneStepAway() {
    // Exit lane last cell (index 4) → remaining 1 → HOME
    int lastExit = EXIT_BASE + 4;
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(lastExit, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    Integer dice =
        BotHomeDiceAssist.pickBestHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 1, new Random(1));

    assertEquals(1, dice);
  }

  @Test
  void returnsNullWhenNotBotSeat() {
    int lastExit = EXIT_BASE + 4;
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {false, true},
            Map.of(
                "RED",
                Arrays.asList(lastExit, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertNull(
        BotHomeDiceAssist.pickBestHomeDice(
            snap, 0, (token, d) -> true, new Random(1)));
  }

  @Test
  void maybePickUsesExactDiceOnAssistRoll() {
    int lastExit = EXIT_BASE + 4;
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(lastExit, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    Random assistRng =
        new Random() {
          @Override
          public double nextDouble() {
            return 0.10;
          }
        };

    assertEquals(
        1,
        BotHomeDiceAssist.maybePickHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 1, assistRng, 0.75));
  }

  @Test
  void maybePickFallsBackToRandomOnMissRoll() {
    int lastExit = EXIT_BASE + 4;
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(lastExit, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    Random missRng =
        new Random() {
          @Override
          public double nextDouble() {
            return 0.90;
          }
        };

    assertNull(
        BotHomeDiceAssist.maybePickHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 1, missRng, 0.75));
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
