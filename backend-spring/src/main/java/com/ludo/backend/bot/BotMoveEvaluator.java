package com.ludo.backend.bot;

import com.ludo.backend.game.LudoColor;
import java.util.List;
import java.util.Map;

/**
 * Compatibility facade over {@link BotBoardMath} / {@link BotMoveScoringEngine}.
 * Prefer the Dynamic AI Engine for new call sites.
 */
final class BotMoveEvaluator {

  private BotMoveEvaluator() {}

  static int applySteps(LudoColor color, int from, int steps) {
    return BotBoardMath.applySteps(color, from, steps);
  }

  static int remainingDistance(LudoColor color, int pos) {
    return BotBoardMath.remainingDistance(color, pos);
  }

  static boolean isPositionThreatened(
      int defenderSeat,
      int pos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    return BotBoardMath.isPositionThreatened(defenderSeat, pos, allPositions, seatColors);
  }

  static BotBoardMath.VictimInfo findCaptureVictim(
      int moverSeat,
      int landPos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    return BotBoardMath.findCaptureVictim(moverSeat, landPos, allPositions, seatColors, null);
  }

  static int countActive(List<Integer> ownPositions) {
    return BotBoardMath.countActive(ownPositions);
  }
}
