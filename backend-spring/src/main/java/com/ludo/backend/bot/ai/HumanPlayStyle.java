package com.ludo.backend.bot.ai;

/** Observed human play style for the current match only. */
public enum HumanPlayStyle {
  AGGRESSIVE,
  DEFENSIVE,
  SPEED_RUNNER,
  RISK_TAKER,
  SAFE_PLAYER,
  BALANCED;

  public String displayName() {
    return switch (this) {
      case SPEED_RUNNER -> "Speed Runner";
      case RISK_TAKER -> "Risk Taker";
      case SAFE_PLAYER -> "Safe Player";
      default -> name().charAt(0) + name().substring(1).toLowerCase().replace('_', ' ');
    };
  }
}
