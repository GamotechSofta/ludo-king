package com.ludo.backend.bot.superior;

public final class CandidateMove {
  public final int tokenIndex;
  public final int fromProgress;
  public final int toProgress;

  public CandidateMove(int tokenIndex, int fromProgress, int toProgress) {
    this.tokenIndex = tokenIndex;
    this.fromProgress = fromProgress;
    this.toProgress = toProgress;
  }
}
