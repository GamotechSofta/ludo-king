package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Monte Carlo Decision Engine — final HARD-bot decision layer.
 *
 * <p>Combines Move Score + Future + Danger + EndGame + Personality priors, then
 * runs a bounded playout search. Falls back to prior ranking on budget failure.
 */
@Component
public class MonteCarloEngine {

  private static final Logger log = LoggerFactory.getLogger(MonteCarloEngine.class);

  private final MonteCarloConfig config;
  private final BranchPruner pruner;
  private final DecisionEvaluator evaluator;
  private final ExpectedValueCalculator evCalculator;
  private final DecisionCache cache;

  public MonteCarloEngine(
      MonteCarloConfig config,
      BranchPruner pruner,
      DecisionEvaluator evaluator,
      DecisionCache cache
  ) {
    this.config = config;
    this.pruner = pruner;
    this.evaluator = evaluator;
    this.evCalculator = new ExpectedValueCalculator();
    this.cache = cache;
  }

  public boolean enabled() {
    return config.enabled();
  }

  /**
   * Select best move among pre-scored decisions.
   *
   * @return chosen ScoredDecision, or {@code null} to signal fallback to Future Simulation pick
   */
  public BotDecisionEngine.ScoredDecision select(
      List<BotDecisionEngine.ScoredDecision> scored,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      BotDifficulty difficulty,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      EndGameProfile endGame,
      PersonalityProfile personality,
      long decisionStartNanos
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || scored == null
        || scored.isEmpty()
        || analysis == null
        || analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER) {
      return null;
    }

    try {
      if (snap != null) {
        int leader =
            opponents != null && opponents.enabled()
                ? opponents.currentLeaderSeat()
                : analysis.leaderSeat;
        SimulationBoard fp =
            SimulationBoard.fromSnapshot(snap, analysis.botSeat, leader);
        cache.beginTurn(fp.fingerprint());
      }

      boolean anyFinish = false;
      List<SimulationNode> nodes = new ArrayList<>(scored.size());
      for (BotDecisionEngine.ScoredDecision d : scored) {
        EndGameRisk risk = EndGameRisk.BALANCED;
        if (endGame != null && endGame.active()) {
          EndGameDecision ed = endGame.forMove(d.candidate);
          if (ed != null) {
            risk = ed.risk();
          }
        } else if (d.candidate != null && d.candidate.threatCountAtTo() >= 2) {
          risk = EndGameRisk.RISKY;
        }
        if (d.candidate != null
            && (com.ludo.backend.game.BoardConstants.isHome(d.candidate.to())
                || d.candidate.moveType() == MoveType.HOME_FINISH)) {
          anyFinish = true;
        }
        nodes.add(new SimulationNode(d.candidate, d.current.total(), d.future, risk));
      }

      pruner.prune(nodes, anyFinish, endGame, dangerMap);
      DecisionTree tree = new DecisionTree(nodes);
      List<SimulationNode> alive = tree.alive();
      if (alive.isEmpty()) {
        return null; // fallback
      }

      int simBudget = config.maxSimulations();
      if (endGame != null && endGame.active()) {
        // More accuracy in endgame — still capped
        simBudget = Math.min(120, (int) Math.round(simBudget * 1.25));
      }
      long remainingNs =
          Math.max(500_000L, config.maxTimeNs() - (System.nanoTime() - decisionStartNanos));
      SearchBudget budget = SearchBudget.of(System.nanoTime(), remainingNs, simBudget);

      int depth = config.depth();
      if (endGame != null && endGame.active()) {
        depth = Math.min(4, Math.max(depth, endGame.futureDepth()));
      }

      // Smart exploration: allocate visits proportional to prior score
      int[] quotas = allocateQuotas(alive, budget.remaining(), endGame);
      for (int i = 0; i < alive.size() && !budget.expired(); i++) {
        SimulationNode node = alive.get(i);
        int quota = quotas[i];
        // Bias finishing branches when endgame active
        if (endGame != null
            && endGame.active()
            && node.move() != null
            && com.ludo.backend.game.BoardConstants.isHome(node.move().to())) {
          quota = Math.min(budget.remaining(), quota + Math.max(2, quota / 3));
        }
        for (int s = 0; s < quota && !budget.expired(); s++) {
          DecisionEvaluator.PlayoutResult r =
              evaluator.playout(
                  node,
                  snap,
                  analysis,
                  pawnValues,
                  opponents,
                  endGame,
                  personality,
                  depth,
                  budget);
          if (r == null) {
            break;
          }
          node.record(r.simScore(), r.winProb(), r.riskPenalty());
        }
      }

      // If time remains, explore top priors a bit more
      alive.sort((a, b) -> Integer.compare(b.priorScore(), a.priorScore()));
      int guard = 0;
      while (!budget.expired() && budget.remaining() > 0 && guard++ < 64) {
        SimulationNode focus = alive.get(guard % Math.min(3, alive.size()));
        DecisionEvaluator.PlayoutResult r =
            evaluator.playout(
                focus,
                snap,
                analysis,
                pawnValues,
                opponents,
                endGame,
                personality,
                depth,
                budget);
        if (r == null) {
          break;
        }
        focus.record(r.simScore(), r.winProb(), r.riskPenalty());
      }

      SimulationNode best = tree.bestByExpectedValue(evCalculator);
      if (best == null || best.move() == null) {
        return null;
      }

      // Map back to ScoredDecision
      BotDecisionEngine.ScoredDecision chosen = null;
      for (BotDecisionEngine.ScoredDecision d : scored) {
        if (sameMove(d.candidate, best.move())) {
          chosen = d;
          break;
        }
      }
      if (chosen == null) {
        return null;
      }

      if (log.isDebugEnabled()) {
        StringBuilder sb = new StringBuilder(480);
        sb.append("MonteCarlo Decision Tree:\n");
        for (SimulationNode n : tree.roots()) {
          if (n.pruned()) {
            sb.append("  pruned ").append(n.pruneReason()).append('\n');
            continue;
          }
          boolean sel = n == best;
          DecisionStatistics stats =
              new DecisionStatistics(n, evCalculator.expectedValue(n), sel, false);
          sb.append("  ").append(stats.debugLine()).append('\n');
        }
        log.debug(sb.toString());
      }

      long us = (System.nanoTime() - t0) / 1_000L;
      if (us > 5_000L && log.isDebugEnabled()) {
        log.debug("MonteCarloEngine {}µs (budget 5000µs) sims={}", us, budget.used());
      }
      return chosen;
    } catch (RuntimeException ex) {
      if (log.isDebugEnabled()) {
        log.debug("MonteCarlo failsafe fallback: {}", ex.toString());
      }
      return null; // Future Simulation fallback
    }
  }

  /**
   * Softmax-ish quotas: stronger priors get more sims; weak get at least 1 if budget allows.
   */
  static int[] allocateQuotas(
      List<SimulationNode> alive, int budget, EndGameProfile endGame
  ) {
    int n = alive.size();
    int[] quotas = new int[n];
    if (n == 0 || budget <= 0) {
      return quotas;
    }
    double min = Double.POSITIVE_INFINITY;
    for (SimulationNode node : alive) {
      min = Math.min(min, node.priorScore());
    }
    double[] weights = new double[n];
    double sum = 0;
    for (int i = 0; i < n; i++) {
      double w = Math.max(1.0, alive.get(i).priorScore() - min + 10.0);
      // Endgame: boost finish / home-entry priors
      MoveCandidate m = alive.get(i).move();
      if (endGame != null
          && endGame.active()
          && m != null
          && (com.ludo.backend.game.BoardConstants.isHome(m.to())
              || com.ludo.backend.game.BoardConstants.isExit(m.to()))) {
        w *= 1.6;
      }
      weights[i] = w;
      sum += w;
    }
    int assigned = 0;
    for (int i = 0; i < n; i++) {
      quotas[i] = Math.max(1, (int) Math.floor(budget * (weights[i] / sum)));
      assigned += quotas[i];
    }
    // Fix rounding to not exceed budget
    int idx = 0;
    while (assigned > budget && assigned > 0) {
      if (quotas[idx % n] > 1) {
        quotas[idx % n]--;
        assigned--;
      }
      idx++;
      if (idx > n * 8) {
        break;
      }
    }
    while (assigned < budget) {
      quotas[ThreadLocalRandom.current().nextInt(n)]++;
      assigned++;
    }
    return quotas;
  }

  private static boolean sameMove(MoveCandidate a, MoveCandidate b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    return a.pawnIndex() == b.pawnIndex()
        && a.from() == b.from()
        && a.to() == b.to()
        && a.diceValue() == b.diceValue();
  }
}
