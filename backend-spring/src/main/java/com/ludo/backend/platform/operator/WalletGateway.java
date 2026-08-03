package com.ludo.backend.platform.operator;

/**
 * Operator wallet HTTP contract (user detail + debit).
 * Implemented by {@link OperatorGatewayClient} when {@code ludo.wallet.mode=OPERATOR}.
 * Cashout publish is a separate publisher service (not part of this interface).
 */
public interface WalletGateway {

  /**
   * {@code GET /service/user/detail} with header {@code token}.
   * Failure / missing {@code user} means the session must be rejected (no retry).
   */
  UserDetailResponse fetchUserDetail(String token);

  /**
   * {@code POST /service/operator/user/balance/v2} with header {@code token}.
   * Accept only when {@link DebitResponse#status()} is {@code true} within the read timeout.
   */
  DebitResponse debit(String token, DebitRequest request);
}
