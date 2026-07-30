package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot Adaptive Difficulty Engine. */
@Component
public class AdaptiveConfig {

  private final boolean enabled;
  private final double maxAssist;
  private final boolean futureWeight;
  private final boolean dynamicScoring;
  private final boolean strategySwitch;
  private final int criticalGap;
  private final int behindGap;
  private final int historySize;

  public AdaptiveConfig(
      @Value("${ludo.bot.adaptive.enabled:true}") boolean enabled,
      @Value("${ludo.bot.adaptive.maxAssist:0.45}") double maxAssist,
      @Value("${ludo.bot.adaptive.futureWeight:true}") boolean futureWeight,
      @Value("${ludo.bot.adaptive.dynamicScoring:true}") boolean dynamicScoring,
      @Value("${ludo.bot.adaptive.strategySwitch:true}") boolean strategySwitch,
      @Value("${ludo.bot.adaptive.criticalGap:80}") int criticalGap,
      @Value("${ludo.bot.adaptive.behindGap:35}") int behindGap,
      @Value("${ludo.bot.adaptive.historySize:12}") int historySize
  ) {
    this.enabled = enabled;
    this.maxAssist = Math.max(0.0, Math.min(0.45, maxAssist));
    this.futureWeight = futureWeight;
    this.dynamicScoring = dynamicScoring;
    this.strategySwitch = strategySwitch;
    this.criticalGap = Math.max(1, criticalGap);
    this.behindGap = Math.max(1, behindGap);
    this.historySize = Math.max(1, historySize);
  }

  public boolean enabled() {
    return enabled;
  }

  public double maxAssist() {
    return maxAssist;
  }

  public boolean futureWeight() {
    return futureWeight;
  }

  public boolean dynamicScoring() {
    return dynamicScoring;
  }

  public boolean strategySwitch() {
    return strategySwitch;
  }

  public int criticalGap() {
    return criticalGap;
  }

  public int behindGap() {
    return behindGap;
  }

  public int historySize() {
    return historySize;
  }
}
