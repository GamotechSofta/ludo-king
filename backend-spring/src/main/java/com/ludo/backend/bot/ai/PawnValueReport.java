package com.ludo.backend.bot.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable report of all bot pawn priorities for one evaluation. */
public final class PawnValueReport {

  private final Map<Integer, PawnPriority> byIndex;
  private final List<PawnPriority> ranked;
  private final boolean enabled;

  public PawnValueReport(List<PawnPriority> ranked, boolean enabled) {
    this.enabled = enabled;
    this.ranked = ranked == null ? List.of() : List.copyOf(ranked);
    Map<Integer, PawnPriority> map = new HashMap<>();
    for (PawnPriority p : this.ranked) {
      map.put(p.pawnIndex(), p);
    }
    this.byIndex = Collections.unmodifiableMap(map);
  }

  public static PawnValueReport disabled(int pawnCount) {
    return new PawnValueReport(List.of(), false);
  }

  public boolean enabled() {
    return enabled;
  }

  public PawnPriority get(int pawnIndex) {
    return byIndex.get(pawnIndex);
  }

  public int value(int pawnIndex) {
    PawnPriority p = byIndex.get(pawnIndex);
    return p == null ? 0 : p.value();
  }

  public List<PawnPriority> ranked() {
    return ranked;
  }

  public PawnPriority leaderPawn() {
    for (PawnPriority p : ranked) {
      if (p.state() != null && p.state().leader()) {
        return p;
      }
    }
    return ranked.isEmpty() ? null : ranked.get(0);
  }

  public int lowestValueIndexExcluding(int exclude) {
    int bestIdx = -1;
    int bestVal = Integer.MAX_VALUE;
    for (PawnPriority p : ranked) {
      if (p.pawnIndex() == exclude || p.neverSacrifice()) {
        continue;
      }
      if (p.value() < bestVal) {
        bestVal = p.value();
        bestIdx = p.pawnIndex();
      }
    }
    return bestIdx;
  }
}
