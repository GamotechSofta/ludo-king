package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tunable weights for the Dynamic Move Scoring Engine.
 *
 * <p>Reusable by future AI modules — inject this bean rather than hardcoding.
 */
@Component
public class AIScoreConfig {

  private final boolean enabled;
  private final int homeBonus;
  private final int homeStretchBonus;
  private final int closerToHomeBonus;
  private final int safeBonus;
  private final int becomesSafeBonus;
  private final int escapeBonus;
  private final int openPawnBonus;
  private final int captureBonus;
  private final int captureLeaderBonus;
  private final int captureNearHomeBonus;
  private final int captureJustOutBonus;
  private final int protectAdvancedBonus;
  private final int advanceStrongBonus;
  private final int boardControlBonus;
  private final int samePawnRepeatPenalty;
  private final int blockBonus;
  private final int blockProtectsBonus;
  private final int riskPenalty;
  private final int multiThreatPenalty;
  private final int obviousDangerPenalty;
  private final int leaveSafePenalty;
  private final int uselessMovePenalty;
  private final int continuousRepeatPenalty;
  private final int slowLeaderBonus;
  private final int endgameFourthBonus;
  private final int randomnessThreshold;
  private final int openingTurnWindow;
  private final int historySize;
  private final int continuousRepeatLimit;

  public AIScoreConfig(
      @Value("${ludo.bot.scoring.enabled:true}") boolean enabled,
      @Value("${ludo.bot.homeBonus:150}") int homeBonus,
      @Value("${ludo.bot.homeStretchBonus:120}") int homeStretchBonus,
      @Value("${ludo.bot.closerToHomeBonus:40}") int closerToHomeBonus,
      @Value("${ludo.bot.safeBonus:100}") int safeBonus,
      @Value("${ludo.bot.becomesSafeBonus:70}") int becomesSafeBonus,
      @Value("${ludo.bot.escapeBonus:90}") int escapeBonus,
      @Value("${ludo.bot.openPawnBonus:70}") int openPawnBonus,
      @Value("${ludo.bot.captureBonus:60}") int captureBonus,
      @Value("${ludo.bot.captureLeaderBonus:100}") int captureLeaderBonus,
      @Value("${ludo.bot.captureNearHomeBonus:90}") int captureNearHomeBonus,
      @Value("${ludo.bot.captureJustOutBonus:10}") int captureJustOutBonus,
      @Value("${ludo.bot.protectAdvancedBonus:80}") int protectAdvancedBonus,
      @Value("${ludo.bot.advanceStrongBonus:60}") int advanceStrongBonus,
      @Value("${ludo.bot.boardControlBonus:40}") int boardControlBonus,
      @Value("${ludo.bot.samePawnRepeatPenalty:30}") int samePawnRepeatPenalty,
      @Value("${ludo.bot.blockBonus:50}") int blockBonus,
      @Value("${ludo.bot.blockProtectsBonus:80}") int blockProtectsBonus,
      @Value("${ludo.bot.riskPenalty:80}") int riskPenalty,
      @Value("${ludo.bot.multiThreatPenalty:120}") int multiThreatPenalty,
      @Value("${ludo.bot.obviousDangerPenalty:150}") int obviousDangerPenalty,
      @Value("${ludo.bot.leaveSafePenalty:70}") int leaveSafePenalty,
      @Value("${ludo.bot.uselessMovePenalty:40}") int uselessMovePenalty,
      @Value("${ludo.bot.continuousRepeatPenalty:30}") int continuousRepeatPenalty,
      @Value("${ludo.bot.slowLeaderBonus:70}") int slowLeaderBonus,
      @Value("${ludo.bot.endgameFourthBonus:100}") int endgameFourthBonus,
      @Value("${ludo.bot.randomnessThreshold:10}") int randomnessThreshold,
      @Value("${ludo.bot.scoring.opening-turn-window:6}") int openingTurnWindow,
      @Value("${ludo.bot.scoring.history-size:10}") int historySize,
      @Value("${ludo.bot.scoring.continuous-repeat-limit:5}") int continuousRepeatLimit
  ) {
    this.enabled = enabled;
    this.homeBonus = homeBonus;
    this.homeStretchBonus = homeStretchBonus;
    this.closerToHomeBonus = closerToHomeBonus;
    this.safeBonus = safeBonus;
    this.becomesSafeBonus = becomesSafeBonus;
    this.escapeBonus = escapeBonus;
    this.openPawnBonus = openPawnBonus;
    this.captureBonus = captureBonus;
    this.captureLeaderBonus = captureLeaderBonus;
    this.captureNearHomeBonus = captureNearHomeBonus;
    this.captureJustOutBonus = captureJustOutBonus;
    this.protectAdvancedBonus = protectAdvancedBonus;
    this.advanceStrongBonus = advanceStrongBonus;
    this.boardControlBonus = boardControlBonus;
    this.samePawnRepeatPenalty = samePawnRepeatPenalty;
    this.blockBonus = blockBonus;
    this.blockProtectsBonus = blockProtectsBonus;
    this.riskPenalty = riskPenalty;
    this.multiThreatPenalty = multiThreatPenalty;
    this.obviousDangerPenalty = obviousDangerPenalty;
    this.leaveSafePenalty = leaveSafePenalty;
    this.uselessMovePenalty = uselessMovePenalty;
    this.continuousRepeatPenalty = continuousRepeatPenalty;
    this.slowLeaderBonus = slowLeaderBonus;
    this.endgameFourthBonus = endgameFourthBonus;
    this.randomnessThreshold = randomnessThreshold;
    this.openingTurnWindow = openingTurnWindow;
    this.historySize = historySize;
    this.continuousRepeatLimit = continuousRepeatLimit;
  }

  public boolean enabled() {
    return enabled;
  }

  public int homeBonus() {
    return homeBonus;
  }

  public int homeStretchBonus() {
    return homeStretchBonus;
  }

  public int closerToHomeBonus() {
    return closerToHomeBonus;
  }

  public int safeBonus() {
    return safeBonus;
  }

  public int becomesSafeBonus() {
    return becomesSafeBonus;
  }

  public int escapeBonus() {
    return escapeBonus;
  }

  public int openPawnBonus() {
    return openPawnBonus;
  }

  public int captureBonus() {
    return captureBonus;
  }

  public int captureLeaderBonus() {
    return captureLeaderBonus;
  }

  public int captureNearHomeBonus() {
    return captureNearHomeBonus;
  }

  public int captureJustOutBonus() {
    return captureJustOutBonus;
  }

  public int protectAdvancedBonus() {
    return protectAdvancedBonus;
  }

  public int advanceStrongBonus() {
    return advanceStrongBonus;
  }

  public int boardControlBonus() {
    return boardControlBonus;
  }

  public int samePawnRepeatPenalty() {
    return samePawnRepeatPenalty;
  }

  public int blockBonus() {
    return blockBonus;
  }

  public int blockProtectsBonus() {
    return blockProtectsBonus;
  }

  public int riskPenalty() {
    return riskPenalty;
  }

  public int multiThreatPenalty() {
    return multiThreatPenalty;
  }

  public int obviousDangerPenalty() {
    return obviousDangerPenalty;
  }

  public int leaveSafePenalty() {
    return leaveSafePenalty;
  }

  public int uselessMovePenalty() {
    return uselessMovePenalty;
  }

  public int continuousRepeatPenalty() {
    return continuousRepeatPenalty;
  }

  public int slowLeaderBonus() {
    return slowLeaderBonus;
  }

  public int endgameFourthBonus() {
    return endgameFourthBonus;
  }

  public int randomnessThreshold() {
    return randomnessThreshold;
  }

  public int openingTurnWindow() {
    return openingTurnWindow;
  }

  public int historySize() {
    return historySize;
  }

  public int continuousRepeatLimit() {
    return continuousRepeatLimit;
  }
}
