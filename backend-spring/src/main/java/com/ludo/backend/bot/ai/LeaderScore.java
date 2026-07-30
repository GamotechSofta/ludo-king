package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.List;

/** Breakdown of a player's leader score. */
public final class LeaderScore {

  private final int total;
  private final List<String> reasons;

  public LeaderScore(int total, List<String> reasons) {
    this.total = total;
    this.reasons = reasons == null ? List.of() : List.copyOf(reasons);
  }

  public static LeaderScore of(int total, String... reasons) {
    List<String> list = new ArrayList<>(reasons.length);
    for (String r : reasons) {
      if (r != null && !r.isBlank()) {
        list.add(r);
      }
    }
    return new LeaderScore(total, list);
  }

  public int total() {
    return total;
  }

  public List<String> reasons() {
    return reasons;
  }
}
