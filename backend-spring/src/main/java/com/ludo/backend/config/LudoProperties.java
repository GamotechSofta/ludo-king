package com.ludo.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ludo")
public record LudoProperties(
    String clientUrl,
    String sessionSecret,
    boolean oauthEnabled,
    OAuthProperties google,
    OAuthProperties github
) {
  public record OAuthProperties(String clientId, String clientSecret) {
    public boolean isEnabled() {
      return clientId != null && !clientId.isBlank()
          && clientSecret != null && !clientSecret.isBlank();
    }
  }
}
