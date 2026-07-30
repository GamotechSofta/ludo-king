package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import org.springframework.stereotype.Component;

/** Detects when End Game Master Strategy should activate. */
@Component
public class EndGameAnalyzer {

  private final EndGameConfig config;

  public EndGameAnalyzer(EndGameConfig config) {
    this.config = config;
  }

  public record Activation(boolean active, String reason, int remainingMoves, double raceRemaining) {}

  public Activation detect(
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      BoardAnalysisCache cache,
      List<Integer> ownPositions,
      LudoColor color
  ) {
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || analysis == null
        || analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER) {
      return new Activation(false, "", Integer.MAX_VALUE, 1.0);
    }

    int botFinished =
        analysis.finishedPawns != null && analysis.botSeat < analysis.finishedPawns.length
            ? analysis.finishedPawns[analysis.botSeat]
            : (cache != null ? cache.finishedCount() : BotBoardMath.countHome(ownPositions));

    int maxOppFinished = 0;
    if (analysis.finishedPawns != null) {
      for (int i = 0; i < analysis.finishedPawns.length; i++) {
        if (i == analysis.botSeat) {
          continue;
        }
        maxOppFinished = Math.max(maxOppFinished, analysis.finishedPawns[i]);
      }
    }

    boolean onHomePath = false;
    if (ownPositions != null) {
      for (Integer p : ownPositions) {
        int pos = p == null ? com.ludo.backend.game.BoardConstants.JAIL : p;
        if (isExit(pos) && !isHome(pos)) {
          onHomePath = true;
          break;
        }
      }
    }

    double raceRemaining = 1.0 - Math.max(0, Math.min(1.0, analysis.tableProgress));
    int remainingMoves = estimateRemainingMoves(color, ownPositions);

    if (botFinished >= 2) {
      return new Activation(true, botFinished + " Finished Pawns", remainingMoves, raceRemaining);
    }
    if (maxOppFinished >= 3) {
      return new Activation(
          true, "Opponent " + maxOppFinished + " Finished Pawns", remainingMoves, raceRemaining);
    }
    if (onHomePath) {
      return new Activation(true, "Pawn on Home Path", remainingMoves, raceRemaining);
    }
    if (raceRemaining < config.raceRemainingThreshold()) {
      return new Activation(
          true,
          "Race Remaining " + Math.round(raceRemaining * 100) + "%",
          remainingMoves,
          raceRemaining);
    }
    if (remainingMoves <= config.activationRemainingMoves()) {
      return new Activation(
          true, "Remaining Moves " + remainingMoves, remainingMoves, raceRemaining);
    }
    if (analysis.phase == BotGamePhase.END) {
      return new Activation(true, "End Phase", remainingMoves, raceRemaining);
    }
    return new Activation(false, "", remainingMoves, raceRemaining);
  }

  /** Optimistic sum of expected turns for unfinished pawns (avg die ≈ 3.5). */
  public int estimateRemainingMoves(LudoColor color, List<Integer> ownPositions) {
    if (color == null || ownPositions == null) {
      return Integer.MAX_VALUE / 4;
    }
    double turns = 0;
    for (Integer p : ownPositions) {
      int pos = p == null ? com.ludo.backend.game.BoardConstants.JAIL : p;
      if (isHome(pos)) {
        continue;
      }
      if (com.ludo.backend.game.BoardConstants.isJail(pos)) {
        // Need six to exit + path; coarse estimate
        turns += 6.0 + BotBoardMath.MAX_PAWN_PROGRESS / 3.5;
        continue;
      }
      int rem = BotBoardMath.remainingDistance(color, pos);
      if (rem == Integer.MAX_VALUE) {
        turns += 8;
      } else {
        turns += rem / 3.5;
      }
    }
    return (int) Math.ceil(Math.max(0, turns));
  }
}
