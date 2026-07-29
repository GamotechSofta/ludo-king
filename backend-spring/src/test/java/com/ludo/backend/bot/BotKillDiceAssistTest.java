package com.ludo.backend.bot;

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

class BotKillDiceAssistTest {

  @Test
  void returnsExactDiceWhenOpponentIsReachable() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "GREEN",
                Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    Integer dice =
        BotKillDiceAssist.pickBestCaptureDice(
            snap, 0, (token, d) -> token == 0 && d == 3, new Random(1));

    assertEquals(3, dice);
  }

  @Test
  void returnsNullWhenNoCaptureInOneToSix() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "GREEN",
                Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(30, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    assertNull(
        BotKillDiceAssist.pickBestCaptureDice(snap, 0, (token, d) -> false, new Random(1)));
  }

  @Test
  void ignoresHumanSeats() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {false, false},
            Map.of(
                "GREEN",
                Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    assertNull(
        BotKillDiceAssist.pickBestCaptureDice(snap, 0, (token, d) -> true, new Random(1)));
  }

  @Test
  void prefersVictimClosestToHome() {
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
        BotKillDiceAssist.pickBestCaptureDice(
            snap,
            0,
            (token, d) -> (token == 0 && d == 1) || (token == 1 && d == 1),
            new Random(1));

    assertEquals(1, dice);
  }

  @Test
  void maybePickUsesExactDiceOnAssistRoll() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "GREEN",
                Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    // 2P rate 0.40; nextDouble 0.10 → assist
    Random assistRng = new Random() {
      @Override
      public double nextDouble() {
        return 0.10;
      }
    };

    assertEquals(
        3,
        BotKillDiceAssist.maybePickCaptureDice(
            snap,
            0,
            (token, d) -> token == 0 && d == 3,
            assistRng,
            BotKillDiceAssist.KillAssistRates.defaults()));
  }

  @Test
  void maybePickFallsBackToRandomOnMissRoll() {
    GameSnapshot snap =
        snapshot(
            0,
            new boolean[] {true, false},
            Map.of(
                "GREEN",
                Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW",
                Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    // 2P rate 0.40; nextDouble 0.50 → random
    Random missRng = new Random() {
      @Override
      public double nextDouble() {
        return 0.50;
      }
    };

    assertNull(
        BotKillDiceAssist.maybePickCaptureDice(
            snap,
            0,
            (token, d) -> token == 0 && d == 3,
            missRng,
            BotKillDiceAssist.KillAssistRates.defaults()));
  }

  @Test
  void probabilityScalesByPlayerCount() {
    var rates = BotKillDiceAssist.KillAssistRates.defaults();
    assertEquals(0.40, BotKillDiceAssist.probabilityForPlayerCount(2, rates));
    assertEquals(0.25, BotKillDiceAssist.probabilityForPlayerCount(3, rates));
    assertEquals(0.10, BotKillDiceAssist.probabilityForPlayerCount(4, rates));
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
