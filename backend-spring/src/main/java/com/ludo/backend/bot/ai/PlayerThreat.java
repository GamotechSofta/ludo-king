package com.ludo.backend.bot.ai;

/** Discrete threat band for an opponent. */
public enum PlayerThreat {
  VERY_LOW,
  LOW,
  MEDIUM,
  HIGH,
  CRITICAL;

  public static PlayerThreat fromScore(int score0to100) {
    int s = Math.max(0, Math.min(100, score0to100));
    if (s <= 20) {
      return VERY_LOW;
    }
    if (s <= 40) {
      return LOW;
    }
    if (s <= 60) {
      return MEDIUM;
    }
    if (s <= 80) {
      return HIGH;
    }
    return CRITICAL;
  }
}
