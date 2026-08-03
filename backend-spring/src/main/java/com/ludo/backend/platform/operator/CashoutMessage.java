package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RabbitMQ cashout (credit) message body (Integration Guide §6).
 * Token is carried in the message for the operator consumer — never persisted to Mongo.
 */
public record CashoutMessage(
    @JsonProperty("txn_id") String txnId,
    @JsonProperty("txn_ref_id") String txnRefId,
    @JsonProperty("txn_type") int txnType,
    String amount,
    @JsonProperty("user_id") String userId,
    @JsonProperty("game_id") String gameId,
    String description,
    String ip,
    String operatorId,
    String token
) {
  /** Guide credit marker. */
  public static final int TXN_TYPE_CREDIT = 1;
}
