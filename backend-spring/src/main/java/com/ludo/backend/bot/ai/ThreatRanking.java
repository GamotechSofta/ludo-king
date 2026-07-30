package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Ranked opponents by threat / leader score for one analysis pass. */
public final class ThreatRanking {

  private final List<OpponentProfile> byLeaderScore;
  private final List<OpponentProfile> byThreat;

  public ThreatRanking(List<OpponentProfile> profiles) {
    List<OpponentProfile> copy = profiles == null ? List.of() : new ArrayList<>(profiles);
    List<OpponentProfile> leaders = new ArrayList<>(copy);
    leaders.sort(
        Comparator.comparingInt((OpponentProfile p) -> p.leaderScore().total())
            .thenComparingDouble(OpponentProfile::winningProbability)
            .reversed());
    List<OpponentProfile> threats = new ArrayList<>(copy);
    threats.sort(
        Comparator.comparingInt(OpponentProfile::threatScore)
            .thenComparingDouble(OpponentProfile::winningProbability)
            .reversed());
    this.byLeaderScore = Collections.unmodifiableList(leaders);
    this.byThreat = Collections.unmodifiableList(threats);
  }

  public List<OpponentProfile> byLeaderScore() {
    return byLeaderScore;
  }

  public List<OpponentProfile> byThreat() {
    return byThreat;
  }

  public OpponentProfile currentLeader() {
    return byLeaderScore.isEmpty() ? null : byLeaderScore.get(0);
  }

  public OpponentProfile highestThreat() {
    return byThreat.isEmpty() ? null : byThreat.get(0);
  }
}
