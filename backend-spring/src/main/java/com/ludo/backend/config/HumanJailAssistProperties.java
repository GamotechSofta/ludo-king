package com.ludo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.human-jail-assist")
public record HumanJailAssistProperties(
    /** Master switch — when false, dice is always fair random. */
    boolean enabled,
    /** Ramp length reference (attempts 1–2 stay fair; 3+ ramp toward {@link #maxSixProbability()}). */
    int maxAssistAttempts,
    /** Number of full-fair rolls before the six probability starts increasing. */
    int normalRollsBeforeBoost,
    /** Added to P(6) for each boost level after the fair rolls. */
    double boostStep,
    /** Upper cap for P(6) — never guarantees a six. */
    double maxSixProbability
) {
  public HumanJailAssistProperties {
    if (maxAssistAttempts < 1) {
      maxAssistAttempts = 6;
    }
    if (normalRollsBeforeBoost < 0) {
      normalRollsBeforeBoost = 2;
    }
    if (boostStep < 0) {
      boostStep = 0.05;
    }
    if (maxSixProbability <= FAIR || maxSixProbability > 1.0) {
      maxSixProbability = 0.45;
    }
  }

  private static final double FAIR = 1.0 / 6.0;
}
