package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import org.springframework.stereotype.Component;

/**
 * Applies root moves and rolls out opponent/bot plies on {@link SimulationBoard} copies.
 */
@Component
public class SimulationEngine {

  private final PredictionEngine predictionEngine;

  public SimulationEngine(PredictionEngine predictionEngine) {
    this.predictionEngine = predictionEngine;
  }

  /** @return capture victim seat, or -1 */
  public int applyRoot(SimulationBoard base, MoveCandidate root) {
    SimulationMove move =
        new SimulationMove(
            base.botSeat(), root.pawnIndex(), root.diceValue(), root.from(), root.to());
    int captured = base.applyMove(move);
    base.setCurrentSeat(nextOpponentSeat(base, base.botSeat()));
    return captured;
  }

  public boolean wasEscaping(MoveCandidate root, SimulationBoard before) {
    boolean fromThreat = threatened(before, before.botSeat(), root.from());
    boolean toThreat = threatened(before, before.botSeat(), root.to());
    return fromThreat && !toThreat;
  }

  public int applyOpponentDie(SimulationBoard board, int dice) {
    int seat = board.currentSeat();
    if (seat == board.botSeat()) {
      seat = nextOpponentSeat(board, board.botSeat());
      board.setCurrentSeat(seat);
    }
    SimulationMove reply = predictionEngine.pickGreedy(board, seat, dice, true);
    int captured = -1;
    if (reply != null) {
      captured = board.applyMove(reply);
    }
    board.setCurrentSeat(board.botSeat());
    return captured;
  }

  public void applyBotReplyDie(SimulationBoard board, int dice) {
    SimulationMove reply =
        predictionEngine.pickGreedy(board, board.botSeat(), dice, false);
    if (reply != null) {
      board.applyMove(reply);
    }
    board.setCurrentSeat(nextOpponentSeat(board, board.botSeat()));
  }

  static int nextOpponentSeat(SimulationBoard board, int botSeat) {
    int n = board.seatCount();
    for (int i = 1; i <= n; i++) {
      int s = (botSeat + i) % n;
      if (s != botSeat) {
        return s;
      }
    }
    return (botSeat + 1) % n;
  }

  private static boolean threatened(SimulationBoard board, int defender, int pos) {
    if (!isMain(pos) || isSafe(pos) || isJail(pos)) {
      return false;
    }
    for (int s = 0; s < board.seatCount(); s++) {
      if (s == defender) {
        continue;
      }
      for (int p = 0; p < board.pawnCount(s); p++) {
        int from = board.token(s, p);
        if (!isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (com.ludo.backend.bot.BotBoardMath.applySteps(board.color(s), from, d) == pos) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
