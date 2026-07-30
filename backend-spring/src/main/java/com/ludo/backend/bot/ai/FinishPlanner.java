package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.game.LudoColor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Finish-priority planner: never delay a guaranteed home finish for a capture.
 */
@Component
public class FinishPlanner {

  public FinishPriority classify(MoveCandidate move, LudoColor color, boolean anyExactFinish) {
    if (move == null) {
      return FinishPriority.OTHER;
    }
    if (isHome(move.to()) || move.moveType() == MoveType.HOME_FINISH) {
      return FinishPriority.EXACT_FINISH;
    }
    if (isExit(move.to()) && !isExit(move.from())) {
      return FinishPriority.HOME_ENTRY;
    }
    if (isExit(move.to()) || isExit(move.from())) {
      return FinishPriority.HOME_ENTRY;
    }
    if (move.underThreatAtFrom()
        && (isSafe(move.to()) || isExit(move.to()) || isHome(move.to()) || move.threatCountAtTo() == 0)
        && isNearHomePawn(color, move.from())) {
      return FinishPriority.PROTECT_ADVANCED;
    }
    if (isSafe(move.to()) && !isSafe(move.from())) {
      return FinishPriority.SAFE_CELL;
    }
    if (move.capture()) {
      return FinishPriority.CAPTURE;
    }
    if (anyExactFinish) {
      // Non-finish while finish is available → lowest strategic priority
      return FinishPriority.OTHER;
    }
    return FinishPriority.BOARD_EXPANSION;
  }

  public boolean isNearHomePawn(LudoColor color, int from) {
    if (color == null) {
      return false;
    }
    if (isExit(from) || isHome(from)) {
      return true;
    }
    return BotBoardMath.isNearHome(color, from);
  }

  public boolean hasExactFinish(List<MoveCandidate> candidates) {
    if (candidates == null) {
      return false;
    }
    for (MoveCandidate c : candidates) {
      if (c != null && (isHome(c.to()) || c.moveType() == MoveType.HOME_FINISH)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isSafe(int pos) {
    return com.ludo.backend.game.BoardConstants.isSafe(pos);
  }
}
