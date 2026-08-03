package com.ludo.backend.platform.operator;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cashout queue settings (Integration Guide §6).
 * Beans are declared in {@link RabbitCashoutConfig} when {@code wallet.mode=OPERATOR}.
 */
@ConfigurationProperties(prefix = "ludo.rabbit-cashout")
public record RabbitCashoutProperties(
    String brokerUri,
    String exchange,
    String exchangeType,
    String delayedType,
    String queue,
    String routingKey,
    /** Max wait for broker publisher confirm (ms). */
    int confirmTimeoutMs,
    /** Operator token hold TTL in Redis (seconds). Default 24h. */
    int holdTtlSeconds
) {
  public RabbitCashoutProperties {
    if (brokerUri == null) {
      brokerUri = "";
    }
    if (exchange == null || exchange.isBlank()) {
      exchange = "games_cashout";
    }
    if (exchangeType == null || exchangeType.isBlank()) {
      exchangeType = "x-delayed-message";
    }
    if (delayedType == null || delayedType.isBlank()) {
      delayedType = "direct";
    }
    if (queue == null || queue.isBlank()) {
      queue = "games_cashout";
    }
    if (routingKey == null || routingKey.isBlank()) {
      routingKey = queue;
    }
    if (confirmTimeoutMs <= 0) {
      confirmTimeoutMs = 5_000;
    }
    if (holdTtlSeconds <= 0) {
      holdTtlSeconds = 86_400;
    }
  }

  public boolean isConfigured() {
    return brokerUri != null && !brokerUri.isBlank();
  }
}
