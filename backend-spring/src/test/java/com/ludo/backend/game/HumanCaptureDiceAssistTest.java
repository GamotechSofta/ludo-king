package com.ludo.backend.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class HumanCaptureDiceAssistTest {

  private HumanCaptureDiceAssist assist() {
    return new HumanCaptureDiceAssist(true, 60);
  }

  @Test
  void returnsExactDiceWhenBotIsOneStepAhead() {
    HumanCaptureDiceAssist assist = assist();
    // 2P: RED bot seat 0, YELLOW human seat 1 — human at 13, bot at 14 → dice 1
    CaptureScanContext ctx =
        ctx(
            1,
            LudoColor.YELLOW,
            new LudoColor[] {LudoColor.RED, LudoColor.YELLOW},
            new int[][] {
              {-1, -1, -1, -1},
              {13, -1, -1, -1},
            },
            new boolean[] {true, false},
            2);

    Integer dice =
        assist.pickBestCaptureDice(
            ctx, (token, d) -> token == 0 && d == 1, new Random(1));

    assertEquals(1, dice);
  }

  @Test
  void returnsExactDiceWhenBotIsThreeStepsAhead() {
    HumanCaptureDiceAssist assist = assist();
    CaptureScanContext ctx =
        ctx(
            1,
            LudoColor.YELLOW,
            new LudoColor[] {LudoColor.GREEN, LudoColor.YELLOW},
            new int[][] {
              {16, -1, -1, -1},
              {13, -1, -1, -1},
            },
            new boolean[] {true, false},
            2);

    Integer dice =
        assist.pickBestCaptureDice(
            ctx, (token, d) -> token == 0 && d == 3, new Random(1));

    assertEquals(3, dice);
  }

  @Test
  void ignoresHumanOpponents() {
    HumanCaptureDiceAssist assist = assist();
    CaptureScanContext ctx =
        ctx(
            0,
            LudoColor.GREEN,
            new LudoColor[] {LudoColor.GREEN, LudoColor.YELLOW},
            new int[][] {
              {13, -1, -1, -1},
              {16, -1, -1, -1},
            },
            new boolean[] {false, false},
            2);

    assertNull(
        assist.pickBestCaptureDice(ctx, (token, d) -> true, new Random(1)));
  }

  @Test
  void ignoresBotSeats() {
    HumanCaptureDiceAssist assist = assist();
    CaptureScanContext ctx =
        ctx(
            0,
            LudoColor.GREEN,
            new LudoColor[] {LudoColor.GREEN, LudoColor.YELLOW},
            new int[][] {
              {13, -1, -1, -1},
              {16, -1, -1, -1},
            },
            new boolean[] {true, false},
            2);

    assertNull(
        assist.pickBestCaptureDice(ctx, (token, d) -> true, new Random(1)));
  }

  @Test
  void maybePickUsesExactDiceOnAssistRoll() {
    HumanCaptureDiceAssist assist = assist();
    CaptureScanContext ctx =
        ctx(
            1,
            LudoColor.YELLOW,
            new LudoColor[] {LudoColor.RED, LudoColor.YELLOW},
            new int[][] {
              {14, -1, -1, -1},
              {13, -1, -1, -1},
            },
            new boolean[] {true, false},
            2);

    Random assistRng =
        new Random() {
          @Override
          public int nextInt(int bound) {
            return bound == 100 ? 10 : 0;
          }
        };

    assertEquals(
        1,
        assist.pickBestCaptureDice(ctx, (token, d) -> token == 0 && d == 1, assistRng));
    assertEquals(
        1,
        new HumanCaptureDiceAssist(true, 60)
            .maybePickCaptureDice(
                runtime(ctx),
                1,
                (token, d) -> token == 0 && d == 1,
                assistRng));
  }

  @Test
  void maybePickFallsBackToRandomOnMissRoll() {
    HumanCaptureDiceAssist assist = assist();
    CaptureScanContext ctx =
        ctx(
            1,
            LudoColor.YELLOW,
            new LudoColor[] {LudoColor.RED, LudoColor.YELLOW},
            new int[][] {
              {14, -1, -1, -1},
              {13, -1, -1, -1},
            },
            new boolean[] {true, false},
            2);

    Random missRng =
        new Random() {
          @Override
          public int nextInt(int bound) {
            return bound == 100 ? 85 : 0;
          }
        };

    assertNull(
        assist.maybePickCaptureDice(
            runtime(ctx), 1, (token, d) -> token == 0 && d == 1, missRng));
  }

  @Test
  void assistChanceIsClampedBelowGuarantee() {
    HumanCaptureDiceAssist assist = new HumanCaptureDiceAssist(true, 100);
    assertEquals(99, assist.assistChancePct());
  }

  private static CaptureScanContext ctx(
      int humanSeat,
      LudoColor humanColor,
      LudoColor[] colors,
      int[][] tokens,
      boolean[] isBot,
      int maxPlayers
  ) {
    return new CaptureScanContext(
        humanSeat,
        humanColor,
        colors,
        tokens,
        isBot,
        new boolean[maxPlayers],
        new boolean[maxPlayers],
        maxPlayers);
  }

  private static GameEngineService.MatchRuntime runtime(CaptureScanContext ctx) {
    List<GameEngineService.SeatInfo> seats = new ArrayList<>(ctx.maxPlayers());
    for (int i = 0; i < ctx.maxPlayers(); i++) {
      seats.add(
          new GameEngineService.SeatInfo(
              "u" + i, "p" + i, ctx.colors()[i], ctx.isBot()[i]));
    }
    GameEngineService.MatchRuntime rt =
        new GameEngineService.MatchRuntime("test", seats);
    for (int s = 0; s < ctx.maxPlayers(); s++) {
      System.arraycopy(ctx.tokens()[s], 0, rt.tokens[s], 0, 4);
    }
    return rt;
  }
}
