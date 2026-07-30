package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Configuration for the Future Move Simulation Engine. */
@Component
public class FutureSimulatorConfig {

  private final boolean enabled;
  private final int depth;
  private final boolean cache;
  private final boolean pruning;
  private final long maxTimeNs;
  private final int pruneMargin;
  private final int homeBonus;
  private final int safeBonus;
  private final int escapeBonus;
  private final int captureBonus;
  private final int riskPenalty;
  private final int loseAdvancedPenalty;
  private final int leaderImprovePenalty;
  private final int slowLeaderBonus;
  private final int trapPenalty;
  private final int blockBonus;
  private final int homePathBonus;

  public FutureSimulatorConfig(
      @Value("${ludo.bot.future.enabled:true}") boolean enabled,
      @Value("${ludo.bot.future.depth:3}") int depth,
      @Value("${ludo.bot.future.cache:true}") boolean cache,
      @Value("${ludo.bot.future.pruning:true}") boolean pruning,
      @Value("${ludo.bot.future.maxTimeMs:5}") int maxTimeMs,
      @Value("${ludo.bot.future.pruneMargin:120}") int pruneMargin,
      @Value("${ludo.bot.future.homeBonus:150}") int homeBonus,
      @Value("${ludo.bot.future.safeBonus:80}") int safeBonus,
      @Value("${ludo.bot.future.escapeBonus:60}") int escapeBonus,
      @Value("${ludo.bot.future.captureBonus:40}") int captureBonus,
      @Value("${ludo.bot.future.riskPenalty:80}") int riskPenalty,
      @Value("${ludo.bot.future.loseAdvancedPenalty:150}") int loseAdvancedPenalty,
      @Value("${ludo.bot.future.leaderImprovePenalty:40}") int leaderImprovePenalty,
      @Value("${ludo.bot.future.slowLeaderBonus:50}") int slowLeaderBonus,
      @Value("${ludo.bot.future.trapPenalty:90}") int trapPenalty,
      @Value("${ludo.bot.future.blockBonus:30}") int blockBonus,
      @Value("${ludo.bot.future.homePathBonus:45}") int homePathBonus
  ) {
    this.enabled = enabled;
    this.depth = Math.max(1, Math.min(3, depth));
    this.cache = cache;
    this.pruning = pruning;
    this.maxTimeNs = Math.max(1, maxTimeMs) * 1_000_000L;
    this.pruneMargin = pruneMargin;
    this.homeBonus = homeBonus;
    this.safeBonus = safeBonus;
    this.escapeBonus = escapeBonus;
    this.captureBonus = captureBonus;
    this.riskPenalty = riskPenalty;
    this.loseAdvancedPenalty = loseAdvancedPenalty;
    this.leaderImprovePenalty = leaderImprovePenalty;
    this.slowLeaderBonus = slowLeaderBonus;
    this.trapPenalty = trapPenalty;
    this.blockBonus = blockBonus;
    this.homePathBonus = homePathBonus;
  }

  public boolean enabled() {
    return enabled;
  }

  public int depth() {
    return depth;
  }

  public boolean cache() {
    return cache;
  }

  public boolean pruning() {
    return pruning;
  }

  public long maxTimeNs() {
    return maxTimeNs;
  }

  public int pruneMargin() {
    return pruneMargin;
  }

  public int homeBonus() {
    return homeBonus;
  }

  public int safeBonus() {
    return safeBonus;
  }

  public int escapeBonus() {
    return escapeBonus;
  }

  public int captureBonus() {
    return captureBonus;
  }

  public int riskPenalty() {
    return riskPenalty;
  }

  public int loseAdvancedPenalty() {
    return loseAdvancedPenalty;
  }

  public int leaderImprovePenalty() {
    return leaderImprovePenalty;
  }

  public int slowLeaderBonus() {
    return slowLeaderBonus;
  }

  public int trapPenalty() {
    return trapPenalty;
  }

  public int blockBonus() {
    return blockBonus;
  }

  public int homePathBonus() {
    return homePathBonus;
  }
}
