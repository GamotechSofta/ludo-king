package com.ludo.backend.platform.operator;

/**
 * Ephemeral operator credentials for cashout publish.
 * Never persisted on {@code match_economy} — only Redis and/or HTTP session.
 */
public record OperatorTokenHold(
    String token,
    String operatorId,
    String ip,
    String httpSessionId
) {
}
