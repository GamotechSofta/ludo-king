package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;

import com.ludo.backend.config.HumanJailExitAssistProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Human-only jail exit assist: when a <em>bot</em> pawn sits in range of this
 * human's start/jail path and the human still has jailed pawns, force a six so
 * a pawn can leave jail. Up to {@link HumanJailExitAssistProperties#maxExits()}
 * forced exits (1, else 2) per threat window; resets when no bot remains in range.
 */
@Component
public class HumanJailExitAssist {

  static final int MIN_STEPS_FROM_START = 1;
  static final int MAX_STEPS_FROM_START = 6;

  private final HumanJailExitAssistProperties props;

  @Autowired
  public HumanJailExitAssist(HumanJailExitAssistProperties props) {
    this.props = props;
  }

  /** For unit tests without Spring. */
  HumanJailExitAssist(boolean enabled, int maxExits) {
    this.props = new HumanJailExitAssistProperties(enabled, maxExits);
  }

  public boolean isEnabled() {
    return props.enabled();
  }

  public int maxExits() {
    return props.maxExits();
  }

  /**
   * @return {@code 6} when human should be forced a jail-exit six; otherwise {@code null}
   */
  public Integer maybeForceJailExitSix(
      boolean humanSeat,
      int humanSeatIndex,
      int startTile,
      int[] ownTokens,
      int maxPlayers,
      int[][] tokens,
      boolean[] isBot,
      boolean[] eliminated,
      boolean[] finished,
      int assistsUsed
  ) {
    if (!props.enabled() || !humanSeat || ownTokens == null) {
      return null;
    }
    if (assistsUsed >= props.maxExits()) {
      return null;
    }
    if (!hasJailedPawn(ownTokens)) {
      return null;
    }
    if (!isBotNearStartingPath(
        startTile, maxPlayers, tokens, isBot, eliminated, finished, humanSeatIndex)) {
      return null;
    }
    return 6;
  }

  /** True when any bot token is 1–6 cells from {@code startTile} on the shared path. */
  public boolean isBotNearStartingPath(
      int startTile,
      int maxPlayers,
      int[][] tokens,
      boolean[] isBot,
      boolean[] eliminated,
      boolean[] finished,
      int humanSeat
  ) {
    if (tokens == null || isBot == null) {
      return false;
    }
    for (int s = 0; s < maxPlayers; s++) {
      if (s == humanSeat || s >= isBot.length || !isBot[s]) {
        continue;
      }
      if (eliminated != null && s < eliminated.length && eliminated[s]) {
        continue;
      }
      if (finished != null && s < finished.length && finished[s]) {
        continue;
      }
      if (s >= tokens.length || tokens[s] == null) {
        continue;
      }
      for (int t = 0; t < tokens[s].length; t++) {
        int pos = tokens[s][t];
        if (isWithinStepsOfStart(startTile, pos, MIN_STEPS_FROM_START, MAX_STEPS_FROM_START)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean hasJailedPawn(int[] ownTokens) {
    if (ownTokens == null) {
      return false;
    }
    for (int pos : ownTokens) {
      if (isJail(pos)) {
        return true;
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
}
