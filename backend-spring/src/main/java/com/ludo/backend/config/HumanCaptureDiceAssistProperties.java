package com.ludo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.human-capture-dice-assist")
public record HumanCaptureDiceAssistProperties(
    /** Master switch — boost exact capture die for human vs bot within 1–6 steps. */
    boolean enabled,
    /** Percent chance (0–100) to roll the exact capture value when reachable. Never 100. */
    int assistChancePct
) {
  public HumanCaptureDiceAssistProperties {
    if (assistChancePct < 1) {
      assistChancePct = 60;
    } else if (assistChancePct > 99) {
      assistChancePct = 99;
    }
  }
}
