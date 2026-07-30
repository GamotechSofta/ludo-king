package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Accumulated numerical score with human-readable reasons. */
public final class MoveScore {

  private int total;
  private final List<ScoreReason> reasons = new ArrayList<>(12);

  public void add(String label, int delta) {
    if (delta == 0) {
      return;
    }
    reasons.add(new ScoreReason(label, delta));
    total += delta;
  }

  public int total() {
    return total;
  }

  public List<ScoreReason> reasons() {
    return Collections.unmodifiableList(reasons);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder(64);
    for (ScoreReason r : reasons) {
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(r);
    }
    sb.append(" => ").append(total);
    return sb.toString();
  }
}
