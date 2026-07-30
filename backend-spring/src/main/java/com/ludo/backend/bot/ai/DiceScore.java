package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accumulated score for one candidate die face. */
public final class DiceScore {

  private int total;
  private final List<String> reasons = new ArrayList<>(6);

  public void add(String reason, int delta) {
    if (delta == 0) {
      return;
    }
    reasons.add(reason);
    total += delta;
  }

  public int total() {
    return total;
  }

  public List<String> reasons() {
    return Collections.unmodifiableList(reasons);
  }

  @Override
  public String toString() {
    return total + " " + reasons;
  }
}
