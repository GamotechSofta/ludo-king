package com.ludo.backend.bot.ai;

/** Qualitative danger band for a board cell (0–100 mapped). */
public enum ThreatLevel {
  SAFE,       // 0–20
  LOW,        // 21–40
  MEDIUM,     // 41–60
  HIGH,       // 61–80
  CRITICAL;   // 81–100

  public static ThreatLevel fromScore(int danger0to100) {
    int d = Math.max(0, Math.min(100, danger0to100));
    if (d <= 20) {
      return SAFE;
    }
    if (d <= 40) {
      return LOW;
    }
    if (d <= 60) {
      return MEDIUM;
    }
    if (d <= 80) {
      return HIGH;
    }
    return CRITICAL;
  }
}
