package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot Human Behavior Learning Engine. */
@Component
public class BehaviorConfig {

  private final boolean enabled;
  private final int history;
  private final double maxInfluence;
  private final double confidenceThreshold;

  public BehaviorConfig(
      @Value("${ludo.bot.behavior.enabled:true}") boolean enabled,
      @Value("${ludo.bot.behavior.history:30}") int history,
      @Value("${ludo.bot.behavior.maxInfluence:0.10}") double maxInfluence,
      @Value("${ludo.bot.behavior.confidenceThreshold:0.65}") double confidenceThreshold
  ) {
    this.enabled = enabled;
    this.history = Math.max(8, Math.min(60, history));
    this.maxInfluence = Math.max(0.0, Math.min(0.10, maxInfluence));
    this.confidenceThreshold = Math.max(0.40, Math.min(0.95, confidenceThreshold));
  }

  public boolean enabled() {
    return enabled;
  }

  public int history() {
    return history;
  }

  public double maxInfluence() {
    return maxInfluence;
  }

  public double confidenceThreshold() {
    return confidenceThreshold;
  }
}
