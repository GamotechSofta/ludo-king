package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import org.springframework.stereotype.Component;

/**
 * Scores a simulated board from the bot's perspective (future opportunities & risks).
 */
@Component
public class FutureAnalyzer {

  private final FutureSimulatorConfig config;

  public FutureAnalyzer(FutureSimulatorConfig config) {
    this.config = config;
  }

  /** Immediate root-move outcomes (home/safe/escape/capture/block). */
  public SimulationScore analyzeRoot(
      SimulationBoard afterRoot,
      MoveCandidate root,
      int capturedSeat,
      boolean escaped,
      BotMatchAnalysis analysis
  ) {
    return analyzeRoot(afterRoot, root, capturedSeat, escaped, analysis, null);
  }

  public SimulationScore analyzeRoot(
      SimulationBoard afterRoot,
      MoveCandidate root,
      int capturedSeat,
      boolean escaped,
      BotMatchAnalysis analysis,
      PawnValueReport pawnValues
  ) {
    return analyzeRoot(afterRoot, root, capturedSeat, escaped, analysis, pawnValues, null);
  }

  public SimulationScore analyzeRoot(
      SimulationBoard afterRoot,
      MoveCandidate root,
      int capturedSeat,
      boolean escaped,
      BotMatchAnalysis analysis,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    SimulationScore s = new SimulationScore();
    int bot = afterRoot.botSeat();
    int movingValue =
        pawnValues != null && pawnValues.enabled()
            ? Math.max(root.pawnValue(), pawnValues.value(root.pawnIndex()))
            : root.pawnValue();

    if (isHome(root.to())) {
      s.add("Future Home", config.homeBonus() + Math.min(40, movingValue / 5));
    } else if (isExit(root.to())) {
      s.add("Future Home Path", config.homePathBonus());
    }
    if (isSafe(root.to())) {
      s.add("Future Safe Cell", config.safeBonus() + (movingValue >= 120 ? 15 : 0));
    }
    if (escaped) {
      s.add("Future Escape", config.escapeBonus() + Math.min(50, movingValue / 4));
    }
    if (capturedSeat >= 0) {
      s.add("Future Capture", config.captureBonus());
      boolean leaderCap =
          capturedSeat == afterRoot.leaderSeat()
              || (opponents != null
                  && opponents.enabled()
                  && opponents.isLeader(capturedSeat));
      if (leaderCap) {
        s.add("Future Slow Leader", config.slowLeaderBonus());
      }
      if (opponents != null && opponents.enabled() && opponents.isPreferredTarget(capturedSeat)) {
        s.add("Future Target Hit", 25);
      }
    }

    if (isMain(root.to()) && !isSafe(root.to())) {
      int stack = 0;
      for (int p = 0; p < afterRoot.pawnCount(bot); p++) {
        if (afterRoot.token(bot, p) == root.to()) {
          stack++;
        }
      }
      if (stack >= 2) {
        s.add("Future Block", config.blockBonus());
      }
    }

    appendPhaseBias(s, afterRoot, root, capturedSeat, analysis);
    appendLandingSafety(s, afterRoot, root, movingValue);
    return s;
  }

  public SimulationScore analyzeState(SimulationBoard board, BotMatchAnalysis analysis) {
    return analyzeState(board, analysis, null, null);
  }

  public SimulationScore analyzeState(
      SimulationBoard board, BotMatchAnalysis analysis, PawnValueReport pawnValues
  ) {
    return analyzeState(board, analysis, pawnValues, null);
  }

  public SimulationScore analyzeState(
      SimulationBoard board,
      BotMatchAnalysis analysis,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    SimulationScore s = new SimulationScore();
    int bot = board.botSeat();
    LudoColor botColor = board.color(bot);

    int threatenedAdvanced = 0;
    int threatened = 0;
    for (int p = 0; p < board.pawnCount(bot); p++) {
      int pos = board.token(bot, p);
      if (!isMain(pos) || isSafe(pos)) {
        continue;
      }
      int enemies = countEnemyReach(board, bot, pos);
      if (enemies > 0) {
        threatened++;
        int rem = BotBoardMath.remainingDistance(botColor, pos);
        int pawnVal =
            pawnValues != null && pawnValues.enabled()
                ? pawnValues.value(p)
                : BoardAnalysisCache.pawnValue(botColor, pos);
        if (rem != Integer.MAX_VALUE && rem <= HOME_STEPS + 12) {
          threatenedAdvanced++;
        }
        if (pawnVal >= 120) {
          s.add("High-Value Exposed", -Math.min(100, pawnVal / 2));
        }
      }
      if (enemies >= 2) {
        s.add("Future Trap Risk", -config.trapPenalty() / 2);
      }
    }
    if (threatened > 0) {
      s.add("Future Risk", -config.riskPenalty() * Math.min(2, threatened));
    }
    if (threatenedAdvanced > 0) {
      s.add("Lose Advanced Risk", -config.loseAdvancedPenalty());
    }

    int leader = board.leaderSeat();
    if (opponents != null && opponents.enabled()) {
      leader = opponents.currentLeaderSeat();
      for (OpponentProfile p : opponents.profiles()) {
        if (p.seat() == bot) {
          continue;
        }
        if (p.futureLeaderRisk()) {
          s.add("Future Leader Rise", -20);
        }
        if (p.winningCritical()) {
          s.add("Critical Winner Pressure", -35);
        }
      }
    }
    if (leader >= 0 && leader != bot) {
      int leaderProg = board.progressTotal(leader);
      int botProg = board.progressTotal(bot);
      if (leaderProg > botProg + 30) {
        s.add("Leader Ahead", -config.leaderImprovePenalty());
      }
    }

    return s;
  }

  /** Compatibility wrapper used by older call sites. */
  public SimulationScore analyzeBoard(
      SimulationBoard board,
      MoveCandidate root,
      int capturedSeat,
      boolean escaped,
      BotMatchAnalysis analysis
  ) {
    SimulationScore s = analyzeRoot(board, root, capturedSeat, escaped, analysis, null);
    s.add("State", analyzeState(board, analysis, null).total());
    return s;
  }

  private void appendPhaseBias(
      SimulationScore s,
      SimulationBoard board,
      MoveCandidate root,
      int capturedSeat,
      BotMatchAnalysis analysis
  ) {
    if (analysis == null) {
      return;
    }
    if (analysis.phase == BotGamePhase.END) {
      if (isHome(root.to()) || isExit(root.to())) {
        s.add("Endgame Finish Bias", 40);
      }
      if (capturedSeat >= 0 && !isHome(root.to()) && board.finishedCount(board.botSeat()) >= 2) {
        s.add("Endgame Skip Side Capture", -20);
      }
    }
    if (analysis.phase == BotGamePhase.EARLY) {
      int active = 0;
      int bot = board.botSeat();
      for (int p = 0; p < board.pawnCount(bot); p++) {
        int pos = board.token(bot, p);
        if (!isJail(pos) && !isHome(pos)) {
          active++;
        }
      }
      if (active >= 2 && active <= 3) {
        s.add("Opening Board Control", 25);
      }
    }
  }

  private void appendLandingSafety(
      SimulationScore s, SimulationBoard board, MoveCandidate root, int movingValue
  ) {
    int bot = board.botSeat();
    int destDanger =
        ThreatAnalyzer.dangerFromEnemyCount(countEnemyReach(board, bot, root.to()));
    if (isSafe(root.to()) || isExit(root.to()) || isHome(root.to())) {
      destDanger = 0;
    }
    if (destDanger >= 70) {
      int pen = config.riskPenalty() + Math.min(80, movingValue / 3);
      s.add("Future Dangerous Landing", -pen);
    } else if (destDanger == 0 && !isJail(root.from())) {
      s.add("Future Safe Route", config.safeBonus() / 4);
    }
  }

  private static int countEnemyReach(SimulationBoard board, int botSeat, int pos) {
    if (!isMain(pos) || isSafe(pos)) {
      return 0;
    }
    int seats = 0;
    for (int s = 0; s < board.seatCount(); s++) {
      if (s == botSeat) {
        continue;
      }
      LudoColor c = board.color(s);
      boolean can = false;
      for (int p = 0; p < board.pawnCount(s); p++) {
        int from = board.token(s, p);
        if (!isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (BotBoardMath.applySteps(c, from, d) == pos) {
            can = true;
            break;
          }
        }
        if (can) {
          break;
        }
      }
      if (can) {
        seats++;
      }
    }
    return seats;
  }
}
