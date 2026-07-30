package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for the Pawn Value & Strategic Priority Engine (HARD only). */
@Component
public class PawnValueConfig {

  private final boolean enabled;
  private final boolean sacrificeEnabled;
  private final int jailValue;
  private final int justReleasedValue;
  private final int earlyValue;
  private final int midValue;
  private final int advancedValue;
  private final int nearHomeValue;
  private final int finalStretchValue;
  private final int homePawnBonus;
  private final int safeCellBonus;
  private final int leaderPawnBonus;
  private final int advancedPawnBonus;
  private final int homePathBonus;
  private final int dangerUrgencyFactor;
  private final int wasteTurnPenalty;
  private final int historySize;
  private final int similarValueMargin;

  public PawnValueConfig(
      @Value("${ludo.bot.pawnValue.enabled:true}") boolean enabled,
      @Value("${ludo.bot.sacrifice.enabled:true}") boolean sacrificeEnabled,
      @Value("${ludo.bot.pawnValue.jail:10}") int jailValue,
      @Value("${ludo.bot.pawnValue.justReleased:25}") int justReleasedValue,
      @Value("${ludo.bot.pawnValue.early:40}") int earlyValue,
      @Value("${ludo.bot.pawnValue.mid:70}") int midValue,
      @Value("${ludo.bot.pawnValue.advanced:100}") int advancedValue,
      @Value("${ludo.bot.pawnValue.nearHome:150}") int nearHomeValue,
      @Value("${ludo.bot.pawnValue.finalStretch:200}") int finalStretchValue,
      @Value("${ludo.bot.homePawnBonus:200}") int homePawnBonus,
      @Value("${ludo.bot.safeCellBonus:25}") int safeCellBonus,
      @Value("${ludo.bot.leaderPawnBonus:40}") int leaderPawnBonus,
      @Value("${ludo.bot.advancedPawnBonus:30}") int advancedPawnBonus,
      @Value("${ludo.bot.pawnValue.homePathBonus:35}") int homePathBonus,
      @Value("${ludo.bot.pawnValue.dangerUrgencyFactor:1}") int dangerUrgencyFactor,
      @Value("${ludo.bot.pawnValue.wasteTurnPenalty:8}") int wasteTurnPenalty,
      @Value("${ludo.bot.pawnValue.historySize:10}") int historySize,
      @Value("${ludo.bot.pawnValue.similarValueMargin:15}") int similarValueMargin
  ) {
    this.enabled = enabled;
    this.sacrificeEnabled = sacrificeEnabled;
    this.jailValue = jailValue;
    this.justReleasedValue = justReleasedValue;
    this.earlyValue = earlyValue;
    this.midValue = midValue;
    this.advancedValue = advancedValue;
    this.nearHomeValue = nearHomeValue;
    this.finalStretchValue = finalStretchValue;
    this.homePawnBonus = homePawnBonus;
    this.safeCellBonus = safeCellBonus;
    this.leaderPawnBonus = leaderPawnBonus;
    this.advancedPawnBonus = advancedPawnBonus;
    this.homePathBonus = homePathBonus;
    this.dangerUrgencyFactor = dangerUrgencyFactor;
    this.wasteTurnPenalty = wasteTurnPenalty;
    this.historySize = Math.max(1, historySize);
    this.similarValueMargin = similarValueMargin;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean sacrificeEnabled() {
    return sacrificeEnabled;
  }

  public int jailValue() {
    return jailValue;
  }

  public int justReleasedValue() {
    return justReleasedValue;
  }

  public int earlyValue() {
    return earlyValue;
  }

  public int midValue() {
    return midValue;
  }

  public int advancedValue() {
    return advancedValue;
  }

  public int nearHomeValue() {
    return nearHomeValue;
  }

  public int finalStretchValue() {
    return finalStretchValue;
  }

  public int homePawnBonus() {
    return homePawnBonus;
  }

  public int safeCellBonus() {
    return safeCellBonus;
  }

  public int leaderPawnBonus() {
    return leaderPawnBonus;
  }

  public int advancedPawnBonus() {
    return advancedPawnBonus;
  }

  public int homePathBonus() {
    return homePathBonus;
  }

  public int dangerUrgencyFactor() {
    return dangerUrgencyFactor;
  }

  public int wasteTurnPenalty() {
    return wasteTurnPenalty;
  }

  public int historySize() {
    return historySize;
  }

  public int similarValueMargin() {
    return similarValueMargin;
  }
}
