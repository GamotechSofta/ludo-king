package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;

import com.ludo.backend.game.GameSnapshot;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * When a bot has exactly one pawn still not home, bias the roll toward 5 or 6
 * so the last piece finishes faster. Humans never use this path.
 */
final class BotLastPawnDiceAssist {

  /** Default chance to force a high die (5 or 6) when only one pawn remains. */
  static final double DEFAULT_HIGH_DICE_PROBABILITY = 0.55;

  private BotLastPawnDiceAssist() {}

  static Integer maybePickHighDice(GameSnapshot snap, int botSeat) {
    return maybePickHighDice(
        snap, botSeat, ThreadLocalRandom.current(), DEFAULT_HIGH_DICE_PROBABILITY);
  }

  /**
   * @return 5 or 6 when assist fires, otherwise {@code null} for normal random roll
   */
  static Integer maybePickHighDice(
      GameSnapshot snap,
      int botSeat,
      Random rng,
      double highDiceProbability
  ) {
    if (!hasExactlyOnePawnLeft(snap, botSeat) || rng == null) {
      return null;
    }
    double chance = Math.max(0.0, Math.min(1.0, highDiceProbability));
    if (chance <= 0.0 || rng.nextDouble() >= chance) {
      return null;
    }
    // Equal split between 5 and 6
    return rng.nextBoolean() ? 5 : 6;
  }

  /** True when exactly one token for this bot seat is not yet HOME. */
  static boolean hasExactlyOnePawnLeft(GameSnapshot snap, int botSeat) {
    if (snap == null || botSeat < 0) {
      return false;
    }
    if (snap.getIsBot() == null
        || botSeat >= snap.getIsBot().length
        || !Boolean.TRUE.equals(snap.getIsBot()[botSeat])) {
      return false;
    }
    String colorName = seatColor(snap, botSeat);
    if (colorName == null || snap.getTokenPositions() == null) {
      return false;
    }
    List<Integer> own = snap.getTokenPositions().get(colorName);
    if (own == null || own.isEmpty()) {
      return false;
    }
    int notHome = 0;
    for (Integer posObj : own) {
      int pos = posObj == null ? JAIL : posObj;
      if (!isHome(pos)) {
        notHome++;
      }
    }
    return notHome == 1;
  }

  private static String seatColor(GameSnapshot snap, int seat) {
    List<String> colors = snap.getSeatColors();
    if (colors != null && seat >= 0 && seat < colors.size()) {
      return colors.get(seat);
    }
    return snap.getCurrentColor();
  }
}
