package com.ludo.backend.bot.ai;

/** Endgame risk classification for a candidate move. */
public enum EndGameRisk {
  VERY_SAFE,
  SAFE,
  BALANCED,
  RISKY,
  VERY_RISKY;

  public String shortLabel() {
    return switch (this) {
      case VERY_SAFE -> "Very Low";
      case SAFE -> "Low";
      case BALANCED -> "Medium";
      case RISKY -> "High";
      case VERY_RISKY -> "Very High";
    };
  }
}
