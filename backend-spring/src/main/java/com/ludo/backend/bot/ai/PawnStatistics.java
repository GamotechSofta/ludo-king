package com.ludo.backend.bot.ai;

/** Rolling counters for one pawn (from {@link PawnHistory}). */
public final class PawnStatistics {

  private final int timesMoved;
  private final int timesCaptured;
  private final int timesReachedSafe;
  private final int timesEscapedDanger;
  private final int wasteStreak;

  public PawnStatistics(
      int timesMoved,
      int timesCaptured,
      int timesReachedSafe,
      int timesEscapedDanger,
      int wasteStreak
  ) {
    this.timesMoved = timesMoved;
    this.timesCaptured = timesCaptured;
    this.timesReachedSafe = timesReachedSafe;
    this.timesEscapedDanger = timesEscapedDanger;
    this.wasteStreak = wasteStreak;
  }

  public static PawnStatistics empty() {
    return new PawnStatistics(0, 0, 0, 0, 0);
  }

  public int timesMoved() {
    return timesMoved;
  }

  public int timesCaptured() {
    return timesCaptured;
  }

  public int timesReachedSafe() {
    return timesReachedSafe;
  }

  public int timesEscapedDanger() {
    return timesEscapedDanger;
  }

  public int wasteStreak() {
    return wasteStreak;
  }
}
