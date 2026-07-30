package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.List;

/** Accumulated future-prediction score with reason lines. */
public final class SimulationScore {

  private int total;
  private final List<String> reasons = new ArrayList<>(8);

  public void add(String reason, int delta) {
    if (delta == 0) {
      return;
    }
    reasons.add((delta >= 0 ? "+" : "") + delta + " " + reason);
    total += delta;
  }

  public int total() {
    return total;
  }

  public List<String> reasons() {
    return reasons;
  }

  @Override
  public String toString() {
    return total + " " + reasons;
  }
}
