package com.ludo.backend.bot.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/** Discards clearly hopeless branches before spending simulation budget. */
@Component
public class BranchPruner {

  private final MonteCarloConfig config;

  public BranchPruner(MonteCarloConfig config) {
    this.config = config;
  }

  public void prune(
      List<SimulationNode> nodes,
      boolean anyExactFinish,
      EndGameProfile endGame,
      DangerMap dangerMap
  ) {
    if (!config.pruning() || nodes == null || nodes.isEmpty()) {
      return;
    }
    int bestPrior = Integer.MIN_VALUE;
    for (SimulationNode n : nodes) {
      bestPrior = Math.max(bestPrior, n.priorScore());
    }

    for (SimulationNode n : nodes) {
      MoveCandidate m = n.move();
      if (m == null) {
        n.markPruned("null move");
        continue;
      }
      // Never delay guaranteed finish
      if (anyExactFinish
          && !com.ludo.backend.game.BoardConstants.isHome(m.to())
          && m.moveType() != MoveType.HOME_FINISH) {
        n.markPruned("non-finish while exact finish available");
        continue;
      }
      // Far behind best prior
      if (bestPrior != Integer.MIN_VALUE
          && n.priorScore() + config.pruneMargin() < bestPrior) {
        n.markPruned("clearly worse prior");
        continue;
      }
      // Very high risk into danger without home/safe
      int danger = dangerMap != null ? dangerMap.dangerAt(m.to()) : m.threatCountAtTo() * 40;
      boolean safeDest =
          com.ludo.backend.game.BoardConstants.isHome(m.to())
              || com.ludo.backend.game.BoardConstants.isExit(m.to())
              || com.ludo.backend.game.BoardConstants.isSafe(m.to());
      if (!safeDest && (danger >= 120 || n.risk() == EndGameRisk.VERY_RISKY)) {
        // Keep if endgame behind and capture of leader — otherwise prune
        boolean rescue =
            endGame != null
                && endGame.active()
                && endGame.botBehind()
                && m.capture()
                && m.victimIsLeader();
        if (!rescue) {
          n.markPruned("very high risk");
        }
      }
    }
  }
}
