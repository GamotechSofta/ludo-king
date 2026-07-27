package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotMoveEvaluator.Context;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotMoveEvaluatorTest {

  @Test
  void hardPrefersFinishFromHomeColumnOverMediocreCapture() {
    LudoColor green = LudoColor.GREEN;
    int lastExit = toExit(EXIT_LEN - 1);
    List<Integer> own = Arrays.asList(lastExit, 5);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(6, JAIL, JAIL, JAIL));

    Context ctx = new Context(
        green,
        0,
        own,
        all,
        List.of("GREEN", "YELLOW"),
        BotDifficulty.HARD
    );

    long finish = BotMoveEvaluator.scoreMove(ctx, 0, lastExit, HOME, 1);
    long capture = BotMoveEvaluator.scoreMove(ctx, 1, 5, 6, 1);

    assertTrue(finish > capture, "finish from home column should beat weak capture");
  }

  @Test
  void hardPrefersHomeColumnProgressOverMediocreCapture() {
    LudoColor green = LudoColor.GREEN;
    int midExit = toExit(2);
    List<Integer> own = Arrays.asList(midExit, 5);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(10, JAIL, JAIL, JAIL));

    Context ctx = new Context(
        green,
        0,
        own,
        all,
        List.of("GREEN", "YELLOW"),
        BotDifficulty.HARD
    );

    long progress = BotMoveEvaluator.scoreMove(ctx, 0, midExit, toExit(3), 1);
    long capture = BotMoveEvaluator.scoreMove(ctx, 1, 5, 6, 1);

    assertTrue(progress > capture, "home column progress should beat weak capture");
  }

  @Test
  void mediumDoesNotApplyHomeColumnFinishBand() {
    LudoColor green = LudoColor.GREEN;
    int lastExit = toExit(EXIT_LEN - 1);
    List<Integer> own = List.of(lastExit);
    Context hard = new Context(
        green, 0, own, Map.of("GREEN", own), List.of("GREEN"), BotDifficulty.HARD);
    Context medium = new Context(
        green, 0, own, Map.of("GREEN", own), List.of("GREEN"), BotDifficulty.MEDIUM);

    long hardFinish = BotMoveEvaluator.scoreMove(hard, 0, lastExit, HOME, 1);
    long mediumFinish = BotMoveEvaluator.scoreMove(medium, 0, lastExit, HOME, 1);

    assertTrue(
        hardFinish > mediumFinish,
        "HARD should score home-column finish higher than MEDIUM");
  }

  @Test
  void hardPrioritizesClosestHomeColumnPawn() {
    LudoColor green = LudoColor.GREEN;
    int closer = toExit(EXIT_LEN - 1);
    int farther = toExit(1);
    List<Integer> own = Arrays.asList(closer, farther);
    Context ctx = new Context(
        green,
        0,
        own,
        Map.of("GREEN", own),
        List.of("GREEN"),
        BotDifficulty.HARD
    );

    long closerMove = BotMoveEvaluator.scoreMove(ctx, 0, closer, HOME, 1);
    long fartherMove = BotMoveEvaluator.scoreMove(ctx, 1, farther, toExit(2), 1);

    assertTrue(closerMove >= fartherMove, "pawn one step from HOME should score at least as high");
  }
}
