package com.ludo.backend.bot.ai;

/** Scored evaluation of one {@link MoveCandidate}. */
public final class MoveEvaluation {

  private final MoveCandidate candidate;
  private final MoveScore score;

  public MoveEvaluation(MoveCandidate candidate, MoveScore score) {
    this.candidate = candidate;
    this.score = score;
  }

  public MoveCandidate candidate() {
    return candidate;
  }

  public MoveScore score() {
    return score;
  }

  public int total() {
    return score.total();
  }
}
