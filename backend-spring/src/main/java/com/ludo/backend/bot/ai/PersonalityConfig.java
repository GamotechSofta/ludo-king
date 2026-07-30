package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot Dynamic Personality Engine. */
@Component
public class PersonalityConfig {

  private final boolean enabled;
  private final String mode;
  private final String defaultPersonality;
  private final boolean evolution;
  private final double randomVariance;

  public PersonalityConfig(
      @Value("${ludo.bot.personality.enabled:true}") boolean enabled,
      @Value("${ludo.bot.personality.mode:random}") String mode,
      @Value("${ludo.bot.personality.default:Balanced}") String defaultPersonality,
      @Value("${ludo.bot.personality.evolution:true}") boolean evolution,
      @Value("${ludo.bot.personality.randomVariance:0.05}") double randomVariance
  ) {
    this.enabled = enabled;
    this.mode = mode == null ? "random" : mode.trim().toLowerCase();
    this.defaultPersonality = defaultPersonality == null ? "Balanced" : defaultPersonality.trim();
    this.evolution = evolution;
    this.randomVariance = Math.max(0.0, Math.min(0.15, randomVariance));
  }

  public boolean enabled() {
    return enabled;
  }

  public String mode() {
    return mode;
  }

  public boolean randomMode() {
    return "random".equals(mode);
  }

  public String defaultPersonality() {
    return defaultPersonality;
  }

  public boolean evolution() {
    return evolution;
  }

  public double randomVariance() {
    return randomVariance;
  }
}
