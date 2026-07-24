package com.ludo.backend.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo")
public record LudoProperties(
    String clientUrl,
    String sessionSecret,
    boolean oauthEnabled,
    OAuthProperties google,
    OAuthProperties github
) {
  /** Comma-separated CLIENT_URL values (local + production). */
  public List<String> allowedClientOrigins() {
    String raw = clientUrl == null || clientUrl.isBlank()
        ? "http://localhost:3043"
        : clientUrl;
    return Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
        .toList();
  }

  public String primaryClientUrl() {
    return allowedClientOrigins().get(0);
  }

  public record OAuthProperties(String clientId, String clientSecret) {
    public boolean isEnabled() {
      return clientId != null && !clientId.isBlank()
          && clientSecret != null && !clientSecret.isBlank();
    }
  }
}
