package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bot-only: when a pawn can finish home with an exact die in range 1–4, force
 * that face so the pawn enters home on the first attempt. Humans never use this.
 */
final class BotHomeDiceAssist {

  static final int MIN_HOME_STEPS = 1;
  static final int MAX_HOME_STEPS = 4;

  private BotHomeDiceAssist() {}

  @FunctionalInterface
  interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  /**
   * @return exact finish die 1–4, or {@code null} if no bot pawn can home in range
   */
  static Integer maybeForceHomeDice(GameSnapshot snap, int botSeat, MoveLegality legality) {
    if (snap == null || legality == null || botSeat < 0) {
      return null;
    }
    if (snap.getIsBot() == null
        || botSeat >= snap.getIsBot().length
        || !snap.getIsBot()[botSeat]) {
      return null;
    }

    List<String> seatColors = snap.getSeatColors();
    if (seatColors == null || botSeat >= seatColors.size()) {
      return null;
    }
    String colorName = seatColors.get(botSeat);
    LudoColor color = BotBoardMath.parseColor(colorName);
    List<Integer> own = snap.getTokenPositions() != null
        ? snap.getTokenPositions().get(colorName)
        : null;
    if (color == null || own == null) {
      return null;
    }

    List<int[]> ops = new ArrayList<>(); // [dice, tokenIndex]
    for (int t = 0; t < own.size(); t++) {
      int from = own.get(t) == null ? JAIL : own.get(t);
      if (isJail(from) || isHome(from)) {
        continue;
      }
      int remaining = BotBoardMath.remainingDistance(color, from);
      if (remaining < MIN_HOME_STEPS || remaining > MAX_HOME_STEPS) {
        continue;
      }
      if (!legality.canMove(t, remaining)) {
        continue;
      }
      int land = BotBoardMath.applySteps(color, from, remaining);
      if (land != HOME) {
        continue;
      }
      ops.add(new int[] {remaining, t});
    }
    if (ops.isEmpty()) {
      return null;
    }
    ops.sort(
        Comparator
            .comparingInt((int[] o) -> o[0])
            .thenComparingInt(o -> o[1]));
    return ops.get(0)[0];
  }
}
