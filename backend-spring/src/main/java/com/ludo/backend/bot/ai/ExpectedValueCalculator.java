package com.ludo.backend.bot.ai;

/**
 * Expected value for a Monte Carlo root node.
 *
 * <p>EV = avgSim + current + winProb − riskPenalty (+ light personality/endgame weights)
 */
public final class ExpectedValueCalculator {

  public double expectedValue(SimulationNode node) {
    if (node == null) {
      return Double.NEGATIVE_INFINITY;
    }
    if (node.visits() == 0) {
      // No sims yet — fall back to prior (current + future)
      return node.priorScore();
    }
    return node.averageSimScore()
        + node.currentScore()
        + node.averageWinProb()
        - node.averageRiskPenalty();
  }

  public int expectedValueInt(SimulationNode node) {
    return (int) Math.round(expectedValue(node));
  }
}
