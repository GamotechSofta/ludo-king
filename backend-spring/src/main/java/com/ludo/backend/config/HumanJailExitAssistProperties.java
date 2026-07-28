package com.ludo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.human-jail-exit-assist")
public record HumanJailExitAssistProperties(
    /** Master switch — smart six boost when all jailed and opponent near start path. */
    boolean enabled,
    /** Percent chance (0–100) to roll a six when the threat condition is met. Never 100. */
    int assistChancePct
) {
  public HumanJailExitAssistProperties {
    if (assistChancePct < 1) {
      assistChancePct = 70;
    } else if (assistChancePct > 99) {
      assistChancePct = 99;
    }
  }
}
