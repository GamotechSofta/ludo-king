package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.ludo.backend.game.GameSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotHomeDiceAssistTest {

  @Test
  void forcesExactHomeDiceInRange1to4() {
    // Exit index 2 → remaining 3 (HOME_STEPS=6 → rem = 5 - exitIndex).
    GameSnapshot snap =
        snapshot(
            new boolean[] {true, false},
            Map.of(
                "RED", Arrays.asList(toExit(2), JAIL, JAIL, JAIL),
                "YELLOW", Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertEquals(
        3,
        BotHomeDiceAssist.maybeForceHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 3));
  }

  @Test
  void doesNotForceWhenRemainingIsFive() {
    GameSnapshot snap =
        snapshot(
            new boolean[] {true, false},
            Map.of(
                "RED", Arrays.asList(toExit(0), JAIL, JAIL, JAIL),
                "YELLOW", Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertNull(
        BotHomeDiceAssist.maybeForceHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 5));
  }

  @Test
  void doesNotForceForHumanSeat() {
    GameSnapshot snap =
        snapshot(
            new boolean[] {false, true},
            Map.of(
                "RED", Arrays.asList(toExit(3), JAIL, JAIL, JAIL),
                "YELLOW", Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    assertNull(
        BotHomeDiceAssist.maybeForceHomeDice(
            snap, 0, (token, d) -> token == 0 && d == 2));
  }

  @Test
  void prefersClosestHomeWhenMultiple() {
    GameSnapshot snap =
        snapshot(
            new boolean[] {true, false},
            Map.of(
                "RED", Arrays.asList(toExit(1), toExit(3), JAIL, JAIL),
                "YELLOW", Arrays.asList(JAIL, JAIL, JAIL, JAIL)),
            List.of("RED", "YELLOW"));

    // token1 rem=2 closer than token0 rem=4
    assertEquals(
        2,
        BotHomeDiceAssist.maybeForceHomeDice(
            snap, 0, (token, d) -> (token == 0 && d == 4) || (token == 1 && d == 2)));
  }

  @SuppressWarnings("unused")
  private static int home() {
    return HOME;
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
