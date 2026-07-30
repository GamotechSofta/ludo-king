package com.ludo.backend.bot.ai;

/** Strategic importance band derived from pawn value. */
public enum PawnImportance {
  HIGHEST,
  HIGH,
  MEDIUM,
  LOW,
  LOWEST;

  public static PawnImportance fromValue(int value) {
    if (value >= 180) {
      return HIGHEST;
    }
    if (value >= 120) {
      return HIGH;
    }
    if (value >= 70) {
      return MEDIUM;
    }
    if (value >= 30) {
      return LOW;
    }
    return LOWEST;
  }
}
