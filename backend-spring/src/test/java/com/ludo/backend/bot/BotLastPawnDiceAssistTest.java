package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.game.GameSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BotLastPawnDiceAssistTest {

  @Test
  void detectsExactlyOnePawnLeft() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(HOME, HOME, HOME, 40),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertTrue(BotLastPawnDiceAssist.hasExactlyOnePawnLeft(snap, 0));
  }

  @Test
  void ignoresWhenMultiplePawnsRemain() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(HOME, HOME, 10, 40),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertFalse(BotLastPawnDiceAssist.hasExactlyOnePawnLeft(snap, 0));
  }

  @Test
  void maybePickReturnsFiveOrSixOnAssist() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(HOME, HOME, HOME, 40),
                "YELLOW",
                Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    Random assistRng =
        new Random() {
          private int calls;

          @Override
          public double nextDouble() {
            return 0.10;
          }

          @Override
          public boolean nextBoolean() {
            return (++calls % 2) == 0;
          }
        };

    Integer dice =
        BotLastPawnDiceAssist.maybePickHighDice(snap, 0, assistRng, 0.55);
    assertTrue(dice != null && (dice == 5 || dice == 6));
  }

  @Test
  void maybePickFallsBackWhenMiss() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "RED",
                Arrays.asList(HOME, HOME, HOME, 40),
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

    assertNull(BotLastPawnDiceAssist.maybePickHighDice(snap, 0, missRng, 0.55));
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
