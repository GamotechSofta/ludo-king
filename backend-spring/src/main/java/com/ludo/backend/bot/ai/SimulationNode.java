package com.ludo.backend.bot.ai;

/**
 * Root search node for one legal move — accumulates Monte Carlo statistics.
 */
public final class SimulationNode {

  private final MoveCandidate move;
  private final int currentScore;
  private final int futureScore;
  private final EndGameRisk risk;
  private int visits;
  private double scoreSum;
  private double winProbSum;
  private double riskPenaltySum;
  private boolean pruned;
  private String pruneReason;

  public SimulationNode(
      MoveCandidate move, int currentScore, int futureScore, EndGameRisk risk
  ) {
    this.move = move;
    this.currentScore = currentScore;
    this.futureScore = futureScore;
    this.risk = risk == null ? EndGameRisk.BALANCED : risk;
    this.visits = 0;
    this.scoreSum = 0;
    this.winProbSum = 0;
    this.riskPenaltySum = 0;
    this.pruned = false;
    this.pruneReason = "";
  }

  public MoveCandidate move() {
    return move;
  }

  public int currentScore() {
    return currentScore;
  }

  public int futureScore() {
    return futureScore;
  }

  public EndGameRisk risk() {
    return risk;
  }

  public int visits() {
    return visits;
  }

  public boolean pruned() {
    return pruned;
  }

  public String pruneReason() {
    return pruneReason;
  }

  public void markPruned(String reason) {
    this.pruned = true;
    this.pruneReason = reason == null ? "pruned" : reason;
  }

  public void record(double simScore, double winProb, double riskPenalty) {
    visits++;
    scoreSum += simScore;
    winProbSum += winProb;
    riskPenaltySum += riskPenalty;
  }

  public double averageSimScore() {
    return visits == 0 ? 0 : scoreSum / visits;
  }

  public double averageWinProb() {
    return visits == 0 ? 50 : winProbSum / visits;
  }

  public double averageRiskPenalty() {
    return visits == 0 ? 0 : riskPenaltySum / visits;
  }

  public int priorScore() {
    return currentScore + futureScore;
  }
}
