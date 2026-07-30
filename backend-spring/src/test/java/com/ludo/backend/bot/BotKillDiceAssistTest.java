package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BotKillDiceAssistTest {

  @Test
  void returnsExactLegalCaptureDice() {
    GameSnapshot snap =
        snapshot(
            new boolean[] {true, false},
            Map.of(
                "GREEN", Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW", Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    BotMatchAnalysis a =
        new BotMatchAnalysis(
            BotAiMode.MODE_1,
            BotGamePhase.MID,
            BotDifficulty.HARD,
            0,
            1,
            1,
            2,
            new boolean[] {true, false},
            1,
            new int[] {10, 20},
            new int[2],
            new int[2],
            0.4,
            true,
            false,
            true);

    Integer dice =
        BotKillDiceAssist.pickBestCaptureDice(
            snap, 0, (token, d) -> token == 0 && d == 3, new Random(1), a);
    assertEquals(3, dice);
  }

  @Test
  void maybePickRespectsAggressionProbability() {
    GameSnapshot snap =
        snapshot(
            new boolean[] {true, false},
            Map.of(
                "GREEN", Arrays.asList(13, JAIL, JAIL, JAIL),
                "YELLOW", Arrays.asList(16, JAIL, JAIL, JAIL)),
            List.of("GREEN", "YELLOW"));

    BotMatchAnalysis early =
        new BotMatchAnalysis(
            BotAiMode.MODE_1,
            BotGamePhase.EARLY,
            BotDifficulty.HARD,
            0,
            1,
            1,
            2,
            new boolean[] {true, false},
            1,
            new int[] {5, 5},
            new int[2],
            new int[2],
            0.1,
            false,
            false,
            true);

    Random miss =
        new Random() {
          @Override
          public double nextDouble() {
            return 0.50; // EARLY 0.20 → miss
          }
        };
    assertNull(
        BotKillDiceAssist.maybePickCaptureDice(
            snap, 0, (token, d) -> token == 0 && d == 3, miss, early));

    Random hit =
        new Random() {
          @Override
          public double nextDouble() {
            return 0.10; // EARLY 0.20 → hit
          }
        };
    assertEquals(
        3,
        BotKillDiceAssist.maybePickCaptureDice(
            snap, 0, (token, d) -> token == 0 && d == 3, hit, early));
  }

  private static GameSnapshot snapshot(
      boolean[] isBot, Map<String, List<Integer>> positions, List<String> colors
  ) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(isBot);
    snap.setSeatColors(colors);
    snap.setTokenPositions(new HashMap<>(positions));
    snap.setCurrentSeatIndex(0);
    snap.setPhase("AWAITING_ROLL");
    return snap;
  }
}
