package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for Leader Detection & Opponent Analysis (HARD only). */
@Component
public class OpponentAnalysisConfig {

  private final boolean leaderEnabled;
  private final boolean opponentAnalysis;
  private final boolean historyEnabled;
  private final boolean threatPrediction;
  private final boolean targetPriority;
  private final int finishedPawnPoints;
  private final int nearHomePoints;
  private final int advancedPoints;
  private final int safePawnPoints;
  private final int jailPawnPenalty;
  private final int capturedRecentlyPenalty;
  private final int historySize;
  private final int captureLeaderBonus;
  private final int slowLeaderBonus;
  private final int reduceLeaderBonus;
  private final int targetThreatBonus;
  private final int ignoreWeakPenalty;
  private final int criticalThreeHomeBonus;
  private final int behindAggressionBonus;
  private final int leadDefenseBonus;
  private final int similarScoreMargin;

  public OpponentAnalysisConfig(
      @Value("${ludo.bot.leader.enabled:true}") boolean leaderEnabled,
      @Value("${ludo.bot.opponentAnalysis:true}") boolean opponentAnalysis,
      @Value("${ludo.bot.history.enabled:true}") boolean historyEnabled,
      @Value("${ludo.bot.threatPrediction:true}") boolean threatPrediction,
      @Value("${ludo.bot.targetPriority:true}") boolean targetPriority,
      @Value("${ludo.bot.leader.finishedPoints:150}") int finishedPawnPoints,
      @Value("${ludo.bot.leader.nearHomePoints:120}") int nearHomePoints,
      @Value("${ludo.bot.leader.advancedPoints:80}") int advancedPoints,
      @Value("${ludo.bot.leader.safePawnPoints:20}") int safePawnPoints,
      @Value("${ludo.bot.leader.jailPenalty:20}") int jailPawnPenalty,
      @Value("${ludo.bot.leader.capturedPenalty:15}") int capturedRecentlyPenalty,
      @Value("${ludo.bot.history.size:20}") int historySize,
      @Value("${ludo.bot.leader.captureBonus:120}") int captureLeaderBonus,
      @Value("${ludo.bot.leader.slowBonus:70}") int slowLeaderBonus,
      @Value("${ludo.bot.leader.reduceBonus:50}") int reduceLeaderBonus,
      @Value("${ludo.bot.leader.threatTargetBonus:40}") int targetThreatBonus,
      @Value("${ludo.bot.leader.ignoreWeakPenalty:35}") int ignoreWeakPenalty,
      @Value("${ludo.bot.leader.criticalThreeHome:80}") int criticalThreeHomeBonus,
      @Value("${ludo.bot.leader.behindAggression:35}") int behindAggressionBonus,
      @Value("${ludo.bot.leader.leadDefense:25}") int leadDefenseBonus,
      @Value("${ludo.bot.leader.similarScoreMargin:30}") int similarScoreMargin
  ) {
    this.leaderEnabled = leaderEnabled;
    this.opponentAnalysis = opponentAnalysis;
    this.historyEnabled = historyEnabled;
    this.threatPrediction = threatPrediction;
    this.targetPriority = targetPriority;
    this.finishedPawnPoints = finishedPawnPoints;
    this.nearHomePoints = nearHomePoints;
    this.advancedPoints = advancedPoints;
    this.safePawnPoints = safePawnPoints;
    this.jailPawnPenalty = jailPawnPenalty;
    this.capturedRecentlyPenalty = capturedRecentlyPenalty;
    this.historySize = Math.max(1, historySize);
    this.captureLeaderBonus = captureLeaderBonus;
    this.slowLeaderBonus = slowLeaderBonus;
    this.reduceLeaderBonus = reduceLeaderBonus;
    this.targetThreatBonus = targetThreatBonus;
    this.ignoreWeakPenalty = ignoreWeakPenalty;
    this.criticalThreeHomeBonus = criticalThreeHomeBonus;
    this.behindAggressionBonus = behindAggressionBonus;
    this.leadDefenseBonus = leadDefenseBonus;
    this.similarScoreMargin = similarScoreMargin;
  }

  public boolean enabled() {
    return leaderEnabled && opponentAnalysis;
  }

  public boolean leaderEnabled() {
    return leaderEnabled;
  }

  public boolean opponentAnalysis() {
    return opponentAnalysis;
  }

  public boolean historyEnabled() {
    return historyEnabled;
  }

  public boolean threatPrediction() {
    return threatPrediction;
  }

  public boolean targetPriority() {
    return targetPriority;
  }

  public int finishedPawnPoints() {
    return finishedPawnPoints;
  }

  public int nearHomePoints() {
    return nearHomePoints;
  }

  public int advancedPoints() {
    return advancedPoints;
  }

  public int safePawnPoints() {
    return safePawnPoints;
  }

  public int jailPawnPenalty() {
    return jailPawnPenalty;
  }

  public int capturedRecentlyPenalty() {
    return capturedRecentlyPenalty;
  }

  public int historySize() {
    return historySize;
  }

  public int captureLeaderBonus() {
    return captureLeaderBonus;
  }

  public int slowLeaderBonus() {
    return slowLeaderBonus;
  }

  public int reduceLeaderBonus() {
    return reduceLeaderBonus;
  }

  public int targetThreatBonus() {
    return targetThreatBonus;
  }

  public int ignoreWeakPenalty() {
    return ignoreWeakPenalty;
  }

  public int criticalThreeHomeBonus() {
    return criticalThreeHomeBonus;
  }

  public int behindAggressionBonus() {
    return behindAggressionBonus;
  }

  public int leadDefenseBonus() {
    return leadDefenseBonus;
  }

  public int similarScoreMargin() {
    return similarScoreMargin;
  }
}
