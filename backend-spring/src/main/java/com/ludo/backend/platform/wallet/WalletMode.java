package com.ludo.backend.platform.wallet;

/**
 * Selects which wallet transport is used.
 * Default {@link #LEGACY} preserves the existing external {@code /api/wallet/*} path.
 */
public enum WalletMode {
  /** Legacy external wallet HTTP client ({@link LegacyWalletClient}). */
  LEGACY,
  /** Operator Gateway user-detail/debit HTTP + RabbitMQ cashout publish. */
  OPERATOR
}
