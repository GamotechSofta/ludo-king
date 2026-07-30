package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Runs one bounded playout from a root move — reuses SimulationEngine / FutureAnalyzer.
 *
 * <p>Never invents illegal dice. Opponent seats are evaluated independently (no bot collusion).
 */
@Component
public class DecisionEvaluator {

  private final SimulationEngine simulationEngine;
  private final FutureAnalyzer futureAnalyzer;
  private final WinningPathCalculator pathCalculator;
  private final DecisionCache cache;
  private final MonteCarloConfig config;

  public DecisionEvaluator(
      SimulationEngine simulationEngine,
      FutureAnalyzer futureAnalyzer,
      WinningPathCalculator pathCalculator,
      DecisionCache cache,
      MonteCarloConfig config
  ) {
    this.simulationEngine = simulationEngine;
    this.futureAnalyzer = futureAnalyzer;
    this.pathCalculator = pathCalculator;
    this.cache = cache;
    this.config = config;
  }

  /**
   * @return playout score, or {@code null} if budget expired before start
   */
  public PlayoutResult playout(
      SimulationNode node,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      EndGameProfile endGame,
      PersonalityProfile personality,
      int depth,
      SearchBudget budget
  ) {
    if (node == null || node.move() == null || snap == null || budget == null || !budget.tryConsume()) {
      return null;
    }

    int leader =
        opponents != null && opponents.enabled()
            ? opponents.currentLeaderSeat()
            : (analysis != null ? analysis.leaderSeat : -1);
    int botSeat = analysis != null ? analysis.botSeat : 0;
    SimulationBoard base = SimulationBoard.fromSnapshot(snap, botSeat, leader);

    boolean escaped = simulationEngine.wasEscaping(node.move(), base);
    SimulationBoard board = base.deepCopy();
    int captured = simulationEngine.applyRoot(board, node.move());

    String cacheKey =
        config.cache()
            ? board.fingerprint() + "|d" + depth + "|r" + node.move().pawnIndex() + ":" + node.move().to()
            : null;
    if (cacheKey != null) {
      Double cached = cache.get(cacheKey);
      if (cached != null) {
        double win = estimateWinProb(board, analysis, endGame, node);
        double risk = riskPenalty(node, endGame);
        return new PlayoutResult(cached, win, risk);
      }
    }

    SimulationScore rootScore =
        futureAnalyzer.analyzeRoot(
            board, node.move(), captured, escaped, analysis, pawnValues, opponents);
    double total = rootScore.total();

    int effectiveDepth = depth;
    if (endGame != null && endGame.active()) {
      effectiveDepth = Math.min(4, Math.max(depth, endGame.futureDepth()));
    }

    ThreadLocalRandom rng = ThreadLocalRandom.current();
    // Expand 2–4 future turns: each depth = all non-bot seats independently + bot sample reply
    for (int ply = 0; ply < effectiveDepth && !budget.expired(); ply++) {
      // Opponents independently — never cooperate with other bots
      for (int seat = 0; seat < board.seatCount(); seat++) {
        if (seat == botSeat || budget.expired()) {
          continue;
        }
        int die = 1 + rng.nextInt(6); // fair 1–6 only
        board.setCurrentSeat(seat);
        int cap = simulationEngine.applyOpponentDie(board, die);
        if (cap == botSeat) {
          total -= 55; // bot pawn captured
        }
      }
      if (budget.expired()) {
        break;
      }
      // Bot reply sample — fair die, never forced kill face
      int botDie = 1 + rng.nextInt(6);
      board.setCurrentSeat(botSeat);
      simulationEngine.applyBotReplyDie(board, botDie);

      SimulationScore state =
          futureAnalyzer.analyzeState(board, analysis, pawnValues, opponents);
      total += state.total() * (0.55 / (ply + 1));
    }

    // Personality as soft weight only
    if (personality != null && personality.enabled()) {
      total *= (0.92 + 0.08 * personality.weights().future());
    }

    double win = estimateWinProb(board, analysis, endGame, node);
    double risk = riskPenalty(node, endGame);

    if (cacheKey != null) {
      cache.put(cacheKey, total);
    }
    return new PlayoutResult(total, win, risk);
  }

  private double estimateWinProb(
      SimulationBoard board,
      BotMatchAnalysis analysis,
      EndGameProfile endGame,
      SimulationNode node
  ) {
    int bot = board.botSeat();
    LudoColor color = board.color(bot);
    int finished = 0;
    int remSum = 0;
    int unfinished = 0;
    for (int p = 0; p < board.pawnCount(bot); p++) {
      int pos = board.token(bot, p);
      if (isHome(pos)) {
        finished++;
        continue;
      }
      unfinished++;
      if (isJail(pos)) {
        remSum += BotBoardMath.MAX_PAWN_PROGRESS + 20;
      } else {
        int rem = BotBoardMath.remainingDistance(color, pos);
        remSum += rem == Integer.MAX_VALUE ? 40 : rem;
      }
    }
    if (unfinished == 0) {
      return 96;
    }
    double expectedTurns = remSum / 3.5;
    EndGameRisk risk = node.risk();
    boolean leading = analysis != null && analysis.botIsLeader;
    boolean behind = analysis != null && analysis.botBehind;
    int maxOpp = 0;
    if (analysis != null && analysis.finishedPawns != null) {
      for (int i = 0; i < analysis.finishedPawns.length; i++) {
        if (i != bot) {
          maxOpp = Math.max(maxOpp, analysis.finishedPawns[i]);
        }
      }
    }
    boolean exact = isHome(node.move().to());
    int wp =
        pathCalculator.winningProbability(
            expectedTurns, risk, leading, behind, maxOpp, finished, exact);
    if (endGame != null && endGame.active() && exact) {
      wp = Math.min(99, wp + 6);
    }
    return wp;
  }

  private static double riskPenalty(SimulationNode node, EndGameProfile endGame) {
    double base =
        switch (node.risk()) {
          case VERY_SAFE -> 0;
          case SAFE -> 5;
          case BALANCED -> 15;
          case RISKY -> 45;
          case VERY_RISKY -> 90;
        };
    if (endGame != null && endGame.active() && endGame.botLeading()) {
      base *= 1.15;
    }
    if (endGame != null && endGame.active() && endGame.botBehind()) {
      base *= 0.85;
    }
    return base;
  }

  public record PlayoutResult(double simScore, double winProb, double riskPenalty) {}
}
