package com.ludo.backend.platform;

import java.io.Serializable;

/**
 * Platform launch context bound to the HTTP session for the match lifetime.
 * Player {@code token} lives only here (never Mongo). {@code operatorId} / {@code balance}
 * are populated when {@code wallet.mode=OPERATOR}; null for legacy wallet launches.
 */
public record PlatformLaunchContext(
    String userId,
    String gameId,
    String sessionId,
    String token,
    String returnUrl,
    String displayName,
    String operatorId,
    Double balance
) implements Serializable {
  public static final String SESSION_KEY = "PLATFORM_LAUNCH";
}
