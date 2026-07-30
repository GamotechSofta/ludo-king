package com.ludo.backend.bot.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Immutable opponent-analysis result for one HARD bot decide() call. */
public final class OpponentAnalysisReport {

  private final Map<Integer, OpponentProfile> bySeat;
  private final List<OpponentProfile> profiles;
  private final ThreatRanking ranking;
  private final int currentLeaderSeat;
  private final int primaryTargetSeat;
  private final int botSeat;
  private final boolean enabled;
  private final boolean botBehind;
  private final boolean botLeading;

  public OpponentAnalysisReport(
      List<OpponentProfile> profiles,
      ThreatRanking ranking,
      int currentLeaderSeat,
      int primaryTargetSeat,
      int botSeat,
      boolean enabled,
      boolean botBehind,
      boolean botLeading
  ) {
    this.enabled = enabled;
    this.profiles = profiles == null ? List.of() : List.copyOf(profiles);
    this.ranking = ranking;
    this.currentLeaderSeat = currentLeaderSeat;
    this.primaryTargetSeat = primaryTargetSeat;
    this.botSeat = botSeat;
    this.botBehind = botBehind;
    this.botLeading = botLeading;
    Map<Integer, OpponentProfile> map = new HashMap<>();
    for (OpponentProfile p : this.profiles) {
      map.put(p.seat(), p);
    }
    this.bySeat = Collections.unmodifiableMap(map);
  }

  public static OpponentAnalysisReport disabled(int botSeat) {
    return new OpponentAnalysisReport(
        List.of(), new ThreatRanking(List.of()), -1, -1, botSeat, false, false, false);
  }

  public boolean enabled() {
    return enabled;
  }

  public OpponentProfile get(int seat) {
    return bySeat.get(seat);
  }

  public List<OpponentProfile> profiles() {
    return profiles;
  }

  public ThreatRanking ranking() {
    return ranking;
  }

  public int currentLeaderSeat() {
    return currentLeaderSeat;
  }

  public int primaryTargetSeat() {
    return primaryTargetSeat;
  }

  public int botSeat() {
    return botSeat;
  }

  public boolean botBehind() {
    return botBehind;
  }

  public boolean botLeading() {
    return botLeading;
  }

  public boolean isLeader(int seat) {
    return enabled && seat == currentLeaderSeat;
  }

  public boolean isPreferredTarget(int seat) {
    OpponentProfile p = bySeat.get(seat);
    return p != null && p.preferredTarget();
  }

  public boolean shouldIgnore(int seat) {
    OpponentProfile p = bySeat.get(seat);
    return p != null && p.ignoreForAttack();
  }
}
