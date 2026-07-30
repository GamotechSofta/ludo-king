package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot Monte Carlo Decision Engine. */
@Component
public class MonteCarloConfig {

  private final boolean enabled;
  private final int depth;
  private final int maxSimulations;
  private final long maxTimeNs;
  private final boolean pruning;
  private final boolean cache;
  private final int pruneMargin;

  public MonteCarloConfig(
      @Value("${ludo.bot.monteCarlo.enabled:true}") boolean enabled,
      @Value("${ludo.bot.monteCarlo.depth:4}") int depth,
      @Value("${ludo.bot.monteCarlo.maxSimulations:50}") int maxSimulations,
      @Value("${ludo.bot.monteCarlo.maxTimeMs:5}") int maxTimeMs,
      @Value("${ludo.bot.monteCarlo.pruning:true}") boolean pruning,
      @Value("${ludo.bot.monteCarlo.cache:true}") boolean cache,
      @Value("${ludo.bot.monteCarlo.pruneMargin:140}") int pruneMargin
  ) {
    this.enabled = enabled;
    this.depth = Math.max(2, Math.min(4, depth));
    this.maxSimulations = Math.max(8, Math.min(120, maxSimulations));
    this.maxTimeNs = Math.max(1, maxTimeMs) * 1_000_000L;
    this.pruning = pruning;
    this.cache = cache;
    this.pruneMargin = Math.max(40, pruneMargin);
  }

  public boolean enabled() {
    return enabled;
  }

  public int depth() {
    return depth;
  }

  public int maxSimulations() {
    return maxSimulations;
  }

  public long maxTimeNs() {
    return maxTimeNs;
  }

  public boolean pruning() {
    return pruning;
  }

  public boolean cache() {
    return cache;
  }

  public int pruneMargin() {
    return pruneMargin;
  }
}
