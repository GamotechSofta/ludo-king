package com.ludo.backend.platform.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Operator Gateway HTTP settings (Integration Guide §4–§5).
 * Bound only; no client bean in Phase 1.
 */
@ConfigurationProperties(prefix = "ludo.operator")
public record OperatorProperties(
    String baseUrl,
    String userDetailPath,
    String balancePath,
    int connectTimeoutMs,
    int readTimeoutMs
) {
  public OperatorProperties {
    if (baseUrl == null) {
      baseUrl = "";
    }
    if (userDetailPath == null || userDetailPath.isBlank()) {
      userDetailPath = "/service/user/detail";
    }
    if (balancePath == null || balancePath.isBlank()) {
      balancePath = "/service/operator/user/balance/v2";
    }
    if (connectTimeoutMs <= 0) {
      connectTimeoutMs = 5_000;
    }
    if (readTimeoutMs <= 0) {
      readTimeoutMs = 5_000;
    }
  }

  public String base() {
    String b = baseUrl == null ? "" : baseUrl.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return b;
  }
}
