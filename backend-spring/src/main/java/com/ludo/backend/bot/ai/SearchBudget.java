package com.ludo.backend.bot.ai;

/** Hard real-time search limits for one Monte Carlo decision. */
public final class SearchBudget {

  private final long deadlineNanos;
  private final int maxSimulations;
  private int usedSimulations;

  public SearchBudget(long deadlineNanos, int maxSimulations) {
    this.deadlineNanos = deadlineNanos;
    this.maxSimulations = Math.max(0, maxSimulations);
    this.usedSimulations = 0;
  }

  public static SearchBudget of(long startNanos, long maxTimeNs, int maxSims) {
    return new SearchBudget(startNanos + maxTimeNs, maxSims);
  }

  public boolean expired() {
    return System.nanoTime() >= deadlineNanos || usedSimulations >= maxSimulations;
  }

  public boolean tryConsume() {
    if (expired()) {
      return false;
    }
    usedSimulations++;
    return true;
  }

  public int used() {
    return usedSimulations;
  }

  public int remaining() {
    return Math.max(0, maxSimulations - usedSimulations);
  }

  public long deadlineNanos() {
    return deadlineNanos;
  }
}
