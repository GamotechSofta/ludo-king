package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.game.LudoColor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Estimates shortest / safest winning path after a candidate move.
 *
 * <p>Uses expected dice (~3.5) — never invents illegal dice faces.
 */
@Component
public class WinningPathCalculator {

  private static final double AVG_DIE = 3.5;

  /**
   * Expected turns to finish all unfinished pawns after applying {@code move}
   * (optimistic, independent dice model).
   */
  public double expectedTurnsAfter(
      LudoColor color, List<Integer> ownPositions, MoveCandidate move
  ) {
    if (color == null || ownPositions == null) {
      return 99;
    }
    double total = 0;
    int unfinished = 0;
    for (int i = 0; i < ownPositions.size(); i++) {
      int pos =
          ownPositions.get(i) == null
              ? com.ludo.backend.game.BoardConstants.JAIL
              : ownPositions.get(i);
      if (move != null && i == move.pawnIndex()) {
        pos = move.to();
      }
      double t = expectedTurnsForPawn(color, pos);
      if (t > 0) {
        unfinished++;
      }
      total += t;
    }
    // Victory: every pawn finished
    if (unfinished == 0) {
      return 0;
    }
    return total;
  }

  public double expectedTurnsForPawn(LudoColor color, int pos) {
    if (isHome(pos)) {
      return 0;
    }
    if (isJail(pos)) {
      return 6.0 + BotBoardMath.MAX_PAWN_PROGRESS / AVG_DIE;
    }
    int rem = BotBoardMath.remainingDistance(color, pos);
    if (rem == Integer.MAX_VALUE) {
      return 10;
    }
    return rem / AVG_DIE;
  }

  /**
   * 0–100 heuristic win probability from expected turns vs opponent pressure.
   */
  public int winningProbability(
      double expectedTurns,
      EndGameRisk risk,
      boolean botLeading,
      boolean botBehind,
      int maxOppFinished,
      int botFinished,
      boolean exactFinish
  ) {
    if (exactFinish) {
      return Math.min(99, 88 + botFinished * 2);
    }
    double base = 78.0 - expectedTurns * 3.5;
    if (botLeading) {
      base += 8;
    }
    if (botBehind) {
      base -= 10;
    }
    base += (botFinished - maxOppFinished) * 6.0;
    base +=
        switch (risk) {
          case VERY_SAFE -> 8;
          case SAFE -> 5;
          case BALANCED -> 0;
          case RISKY -> -8;
          case VERY_RISKY -> -16;
        };
    return (int) Math.max(5, Math.min(97, Math.round(base)));
  }

  public int safePathBonus(int to) {
    if (isHome(to)) {
      return 40;
    }
    if (isExit(to)) {
      return 30;
    }
    if (isSafe(to)) {
      return 20;
    }
    return 0;
  }
}
