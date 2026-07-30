package com.ludo.backend.bot.ai;

/** Aggregated Monte Carlo decision stats for logging / selection. */
public final class DecisionStatistics {

  private final SimulationNode node;
  private final double expectedValue;
  private final int simulations;
  private final double averageScore;
  private final int winProbability;
  private final EndGameRisk risk;
  private final boolean selected;
  private final boolean fallback;

  public DecisionStatistics(
      SimulationNode node,
      double expectedValue,
      boolean selected,
      boolean fallback
  ) {
    this.node = node;
    this.expectedValue = expectedValue;
    this.simulations = node != null ? node.visits() : 0;
    this.averageScore = node != null ? node.averageSimScore() : 0;
    this.winProbability = node != null ? (int) Math.round(node.averageWinProb()) : 0;
    this.risk = node != null ? node.risk() : EndGameRisk.BALANCED;
    this.selected = selected;
    this.fallback = fallback;
  }

  public SimulationNode node() {
    return node;
  }

  public double expectedValue() {
    return expectedValue;
  }

  public int simulations() {
    return simulations;
  }

  public boolean selected() {
    return selected;
  }

  public boolean fallback() {
    return fallback;
  }

  public String debugLine() {
    if (node == null || node.move() == null) {
      return "Decision none";
    }
    return "Decision Pawn "
        + node.move().pawnIndex()
        + " Simulations "
        + simulations
        + " Average Score "
        + Math.round(averageScore)
        + " Win Probability "
        + winProbability
        + "% Risk "
        + risk.shortLabel()
        + " Expected Value "
        + Math.round(expectedValue)
        + (selected ? " Selected YES" : "")
        + (fallback ? " Fallback" : "");
  }
}
