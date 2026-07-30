package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Scoring smoke tests for the Dynamic AI move engine. */
class BotMoveEvaluatorTest {

  @Test
  void endFinishBeatsCapture() {
    LudoColor green = LudoColor.GREEN;
    int lastExit = toExit(EXIT_LEN - 1);
    List<Integer> own = Arrays.asList(lastExit, 5);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(6, JAIL, JAIL, JAIL));

    BotMatchAnalysis a =
        new BotMatchAnalysis(
            BotAiMode.MODE_1,
            BotGamePhase.END,
            BotDifficulty.HARD,
            0,
            1,
            1,
            2,
            new boolean[] {true, false},
            1,
            new int[] {90, 100},
            new int[2],
            new int[2],
            0.75,
            true,
            false,
            true);

    long finish =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 0, lastExit, HOME, 1);
    long capture =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 1, 5, 6, 1);
    assertTrue(finish > capture);
  }
}
