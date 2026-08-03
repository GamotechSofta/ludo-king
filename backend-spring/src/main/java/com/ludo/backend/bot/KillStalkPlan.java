package com.ludo.backend.bot;

/**
 * A hunt the bot is committed to: it closes the gap to one human token over several
 * turns instead of rolling the exact capture face straight away.
 */
public record KillStalkPlan(
    int hunterPlayerIndex,
    int hunterTokenIndex,
    int targetPlayerIndex,
    int targetTokenIndex,
    int plannedRounds,
    int roundsSpent) {

  public KillStalkPlan(
      int hunterPlayerIndex,
      int hunterTokenIndex,
      int targetPlayerIndex,
      int targetTokenIndex,
      int plannedRounds) {
    this(hunterPlayerIndex, hunterTokenIndex, targetPlayerIndex, targetTokenIndex, plannedRounds, 0);
  }

  public KillStalkPlan withRoundsSpent(int nextRoundsSpent) {
    return new KillStalkPlan(
        hunterPlayerIndex,
        hunterTokenIndex,
        targetPlayerIndex,
        targetTokenIndex,
        plannedRounds,
        nextRoundsSpent);
  }
}
