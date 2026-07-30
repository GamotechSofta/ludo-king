package com.ludo.backend.bot.ai;

/** High-level adaptive strategy for the current turn. */
public enum AdaptiveStrategy {
  EXPANSION,
  BOARD_CONTROL,
  FINISH,
  RECOVERY,
  DEFENSIVE
}
