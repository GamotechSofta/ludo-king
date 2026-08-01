package com.ludo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.human-jail-exit-assist")
public record HumanJailExitAssistProperties(
    /** Master switch — force a six when a bot is in range of the human jail/start. */
    boolean enabled,
    /**
     * Max forced jail-exit sixes while bot remains in range (1, else up to 2).
     * Resets when no bot is in range.
     */
    int maxExits
) {
  public HumanJailExitAssistProperties {
    if (maxExits < 1) {
      maxExits = 1;
    } else if (maxExits > 2) {
      maxExits = 2;
    }
  }
}
