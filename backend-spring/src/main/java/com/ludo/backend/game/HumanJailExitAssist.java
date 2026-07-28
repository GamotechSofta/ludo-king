package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.isMain;

import com.ludo.backend.config.HumanJailExitAssistProperties;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Human-only jail exit assist: when all four tokens are jailed and an opponent
 * sits 1–6 shared-path cells from this seat's start square, raise (but do not
 * guarantee) the chance of rolling a six.
 */
@Component
public class HumanJailExitAssist {

  static final int MIN_STEPS_FROM_START = 1;
  static final int MAX_STEPS_FROM_START = 6;

  private final HumanJailExitAssistProperties props;

  public HumanJailExitAssist(HumanJailExitAssistProperties props) {
    this.props = props;
  }

  /** For unit tests without Spring. */
  HumanJailExitAssist(boolean enabled, int assistChancePct) {
    this.props = new HumanJailExitAssistProperties(enabled, assistChancePct);
  }

  public boolean isEnabled() {
    return props.enabled();
  }

  public int assistChancePct() {
    return props.assistChancePct();
  }

  /**
   * @return {@code true} when any opponent token is 1–6 cells from {@code startTile}
   *     on the shared clockwise ring (before or after the start along the loop).
   */
  public boolean isOpponentNearStartingPath(
      int startTile,
      int maxPlayers,
      int[][] tokens,
      boolean[] eliminated,
      boolean[] finished,
      int moverSeat
  ) {
    for (int s = 0; s < maxPlayers; s++) {
      if (s == moverSeat || eliminated[s] || finished[s]) {
        continue;
      }
      for (int t = 0; t < 4; t++) {
        int pos = tokens[s][t];
        if (isWithinStepsOfStart(startTile, pos, MIN_STEPS_FROM_START, MAX_STEPS_FROM_START)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isWithinStepsOfStart(
      int startTile, int pos, int minSteps, int maxSteps
  ) {
    if (!isMain(pos) || !isMain(startTile)) {
      return false;
    }
    int forward = (pos - startTile + TOTAL_TILES) % TOTAL_TILES;
    int backward = (startTile - pos + TOTAL_TILES) % TOTAL_TILES;
    return (forward >= minSteps && forward <= maxSteps)
        || (backward >= minSteps && backward <= maxSteps);
  }

  /**
   * Weighted roll: {@link HumanJailExitAssistProperties#assistChancePct()} chance
   * of six; otherwise a fair random 1–6.
   */
  public int rollDice(Random rng) {
    if (rng.nextInt(100) < props.assistChancePct()) {
      return 6;
    }
    return rng.nextInt(6) + 1;
  }
}
