package com.ludo.backend.platform.wallet;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo.wallet")
public record WalletProperties(
    boolean enabled,
    String baseUrl,
    String gameId,
    double entryFee,
    double winMultiplier,
    double winPayout,
    /** Comma-separated bet amounts, e.g. "10,20,50,100" */
    String betOptions
) {
  public WalletProperties {
    if (gameId == null || gameId.isBlank()) {
      gameId = "LUDO";
    }
    if (entryFee < 0) {
      entryFee = 0;
    }
    if (betOptions == null || betOptions.isBlank()) {
      betOptions = "10,20,50,100";
    }
  }

  public String base() {
    String b = baseUrl == null ? "" : baseUrl.trim();
    while (b.endsWith("/")) {
      b = b.substring(0, b.length() - 1);
    }
    return b;
  }

  /**
   * True when the configured host is Aakda's player frontend instead of its API.
   * That host serves the React SPA and answers wallet paths with an empty 200,
   * which looks like a flaky wallet rather than a misconfiguration.
   */
  public boolean wrongHost() {
    String host = host();
    return "aakda.in".equals(host) || "www.aakda.in".equals(host);
  }

  public String host() {
    String b = base();
    if (b.isBlank()) {
      return "";
    }
    try {
      String h = java.net.URI.create(b).getHost();
      return h == null ? "" : h.toLowerCase();
    } catch (IllegalArgumentException e) {
      return "";
    }
  }

  /** Live when wallet URL configured (bet amount chosen at join). */
  public boolean isLive() {
    return enabled && baseUrl != null && !baseUrl.isBlank();
  }

  public List<Double> betOptionList() {
    return Arrays.stream(betOptions.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> {
          try {
            return money(Double.parseDouble(s));
          } catch (NumberFormatException e) {
            return null;
          }
        })
        .filter(v -> v != null && v > 0)
        .distinct()
        .toList();
  }

  public static double money(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  /** Parse stakeTier: FREE→0, BET_10→10, "20"→20 */
  public static double parseStakeAmount(String stakeTier, double fallback) {
    if (stakeTier == null || stakeTier.isBlank() || "FREE".equalsIgnoreCase(stakeTier)) {
      return 0;
    }
    String t = stakeTier.trim().toUpperCase();
    if (t.startsWith("BET_")) {
      t = t.substring(4);
    }
    try {
      return money(Double.parseDouble(t));
    } catch (NumberFormatException e) {
      return money(fallback);
    }
  }

  public static String stakeTierForBet(double amount) {
    if (amount <= 0) {
      return "FREE";
    }
    long whole = Math.round(amount);
    if (Math.abs(amount - whole) < 0.001) {
      return "BET_" + whole;
    }
    return "BET_" + money(amount);
  }
}
