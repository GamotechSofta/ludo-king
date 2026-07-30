package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Picks a greedy reply move for a seat under a rolled die (opponent or bot reply).
 */
@Component
public class PredictionEngine {

  /**
   * Opponent: prefer capturing bot, then leader progress, then own progress.
   * Bot reply: prefer home/safe/escape over raw progress.
   */
  public SimulationMove pickGreedy(SimulationBoard board, int seat, int dice, boolean asOpponent) {
    List<SimulationMove> legal = board.legalMovesForDie(seat, dice);
    if (legal.isEmpty()) {
      return null;
    }
    SimulationMove best = legal.get(0);
    int bestScore = Integer.MIN_VALUE;
    int bot = board.botSeat();
    for (SimulationMove m : legal) {
      int score = 0;
      if (asOpponent) {
        VictimInfo v =
            BotBoardMath.findCaptureVictim(
                seat, m.to(), board.positionsMap(), board.seatColors(), board.isBotFlags());
        if (v != null && v.seat == bot) {
          score += 500;
          int rem = BotBoardMath.remainingDistance(board.color(bot), m.to());
          if (rem != Integer.MAX_VALUE && rem <= 20) {
            score += 200; // kill advanced bot
          }
        } else if (v != null) {
          score += 80;
        }
        score += Math.max(0, 60 - (BotBoardMath.remainingDistance(board.color(seat), m.to()) % 100));
      } else {
        if (isHome(m.to())) {
          score += 400;
        }
        if (isSafe(m.to())) {
          score += 120;
        }
        boolean fromThreat = isThreatenedByAny(board, seat, m.from());
        boolean toThreat = isThreatenedByAny(board, seat, m.to());
        if (fromThreat && !toThreat) {
          score += 150;
        }
        if (toThreat && !isSafe(m.to()) && !isHome(m.to())) {
          score -= 100;
        }
        score += Math.max(0, 40 - Math.min(40, BotBoardMath.remainingDistance(board.color(seat), m.to()) / 2));
      }
      if (score > bestScore) {
        bestScore = score;
        best = m;
      }
    }
    return best;
  }

  private static boolean isThreatenedByAny(SimulationBoard board, int defender, int pos) {
    if (!isMain(pos) || isSafe(pos) || isJail(pos) || isHome(pos)) {
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
          if (BotBoardMath.applySteps(board.color(s), from, d) == pos) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
