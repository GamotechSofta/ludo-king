package com.ludo.backend.bot.ai;

/**
 * Per-move endgame evaluation: winning path, risk, and score contribution.
 *
 * <p>Does not change legality — only AI weight deltas.
 */
public final class EndGameDecision {

  private final MoveCandidate move;
  private final FinishPriority priority;
  private final double expectedTurns;
  private final int winningProbability;
  private final EndGameRisk risk;
  private final int scoreDelta;
  private final String reason;

  public EndGameDecision(
      MoveCandidate move,
      FinishPriority priority,
      double expectedTurns,
      int winningProbability,
      EndGameRisk risk,
      int scoreDelta,
      String reason
  ) {
    this.move = move;
    this.priority = priority == null ? FinishPriority.OTHER : priority;
    this.expectedTurns = Math.max(0, expectedTurns);
    this.winningProbability = Math.max(0, Math.min(100, winningProbability));
    this.risk = risk == null ? EndGameRisk.BALANCED : risk;
    this.scoreDelta = scoreDelta;
    this.reason = reason == null ? "" : reason;
  }

  public MoveCandidate move() {
    return move;
  }

  public FinishPriority priority() {
    return priority;
  }

  public double expectedTurns() {
    return expectedTurns;
  }

  public int winningProbability() {
    return winningProbability;
  }

  public EndGameRisk risk() {
    return risk;
  }

  public int scoreDelta() {
    return scoreDelta;
  }

  public String reason() {
    return reason;
  }

  public String debugLine(boolean selected) {
    return "Move Pawn "
        + (move != null ? move.pawnIndex() : -1)
        + " Expected Turns "
        + Math.round(expectedTurns)
        + " Winning Probability "
        + winningProbability
        + "% Risk "
        + risk.shortLabel()
        + " Delta "
        + scoreDelta
        + (selected ? " Selected YES" : "");
  }
}
