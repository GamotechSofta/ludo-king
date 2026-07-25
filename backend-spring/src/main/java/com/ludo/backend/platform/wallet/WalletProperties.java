package com.ludo.backend.platform.wallet;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.wallet")
public record WalletProperties(
    boolean enabled,
    String baseUrl,
    String gameId,
    double entryFee,
    double winMultiplier,
    double winPayout
) {
  public WalletProperties {
    if (gameId == null || gameId.isBlank()) {
      gameId = "LUDO";
    }
    if (entryFee < 0) {
      entryFee = 0;
    }
  }

  public String base() {
    String b = baseUrl == null ? "" : baseUrl.trim();
    return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
  }

  public boolean isLive() {
    return enabled && baseUrl != null && !baseUrl.isBlank() && entryFee > 0;
  }

  /** Round money to 2 decimal places. */
  public static double money(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
