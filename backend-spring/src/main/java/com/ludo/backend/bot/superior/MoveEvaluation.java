package com.ludo.backend.bot.superior;

public final class MoveEvaluation {
  public final CandidateMove move;
  public final double immediateReward;
  public final double progressReward;
  public final double safetyReward;
  public final double attackReward;
  public final double strategicReward;
  public final double riskPenalty;
  public final double futureValue;
  public final double finalScore;
  public final String reason;

  public MoveEvaluation(
      CandidateMove move,
      double immediateReward,
      double progressReward,
      double safetyReward,
      double attackReward,
      double strategicReward,
      double riskPenalty,
      double futureValue,
      double finalScore,
      String reason) {
    this.move = move;
    this.immediateReward = immediateReward;
    this.progressReward = progressReward;
    this.safetyReward = safetyReward;
    this.attackReward = attackReward;
    this.strategicReward = strategicReward;
    this.riskPenalty = riskPenalty;
    this.futureValue = futureValue;
    this.finalScore = finalScore;
    this.reason = reason;
  }
}
