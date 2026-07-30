package com.ludo.backend.bot.ai;

/**
 * Outcome of simulating one root bot move (expected value over futures).
 */
public final class SimulationResult {

  private final MoveCandidate rootMove;
  private final SimulationScore futureScore;
  private final int futuresEvaluated;
  private final boolean pruned;
  private final long elapsedNanos;

  public SimulationResult(
      MoveCandidate rootMove,
      SimulationScore futureScore,
      int futuresEvaluated,
      boolean pruned,
      long elapsedNanos
  ) {
    this.rootMove = rootMove;
    this.futureScore = futureScore;
    this.futuresEvaluated = futuresEvaluated;
    this.pruned = pruned;
    this.elapsedNanos = elapsedNanos;
  }

  public MoveCandidate rootMove() {
    return rootMove;
  }

  public SimulationScore futureScore() {
    return futureScore;
  }

  public int futureTotal() {
    return futureScore != null ? futureScore.total() : 0;
  }

  public int futuresEvaluated() {
    return futuresEvaluated;
  }

  public boolean pruned() {
    return pruned;
  }

  public long elapsedNanos() {
    return elapsedNanos;
  }

  static SimulationResult empty(MoveCandidate move) {
    return new SimulationResult(move, new SimulationScore(), 0, true, 0L);
  }
}
