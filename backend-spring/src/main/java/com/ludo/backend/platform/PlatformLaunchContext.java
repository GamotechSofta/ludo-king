package com.ludo.backend.platform;

import java.io.Serializable;

/** Platform launch context bound to the HTTP session for the match lifetime. */
public record PlatformLaunchContext(
    String userId,
    String gameId,
    String sessionId,
    String token,
    String returnUrl,
    String displayName
) implements Serializable {
  public static final String SESSION_KEY = "AAKDA_PLATFORM_LAUNCH";
}
