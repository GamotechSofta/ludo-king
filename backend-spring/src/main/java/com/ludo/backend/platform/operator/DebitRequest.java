package com.ludo.backend.platform.operator;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Operator Balance (debit) request body (Integration Guide §5).
 * {@code amount} is a 2-decimal string on the wire (e.g. {@code "50.00"}).
 */
public record DebitRequest(
    @JsonProperty("txn_id") String txnId,
    @JsonProperty("txn_type") int txnType,
    String amount,
    @JsonProperty("user_id") String userId,
    @JsonProperty("game_id") String gameId,
    @JsonProperty("bet_id") String betId,
    String description,
    String ip
) {
  /** Guide debit marker. */
  public static final int TXN_TYPE_DEBIT = 0;
}
