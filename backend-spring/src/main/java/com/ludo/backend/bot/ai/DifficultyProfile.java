package com.ludo.backend.bot.ai;

/**
 * Live adaptive knobs applied to scoring, dice assist, and future simulation.
 * Aggression is 0–100 (Very Defensive → Very Aggressive).
 */
public final class DifficultyProfile {

  private final BotStatus status;
  private final AdaptiveStrategy strategy;
  private final int aggression;
  private final double diceAssistRate;
  private final int captureWeightDelta;
  private final int safeWeightDelta;
  private final int escapeWeightDelta;
  private final int homeWeightDelta;
  private final int protectWeightDelta;
  private final int riskWeightDelta;
  private final int futureDepthBoost;
  private final double futureScoreMult;
  private final boolean reduceSideCaptures;
  private final String reason;
  private final boolean enabled;

  public DifficultyProfile(
      BotStatus status,
      AdaptiveStrategy strategy,
      int aggression,
      double diceAssistRate,
      int captureWeightDelta,
      int safeWeightDelta,
      int escapeWeightDelta,
      int homeWeightDelta,
      int protectWeightDelta,
      int riskWeightDelta,
      int futureDepthBoost,
      double futureScoreMult,
      boolean reduceSideCaptures,
      String reason,
      boolean enabled
  ) {
    this.status = status;
    this.strategy = strategy;
    this.aggression = Math.max(0, Math.min(100, aggression));
    this.diceAssistRate = Math.max(0.0, Math.min(0.45, diceAssistRate));
    this.captureWeightDelta = captureWeightDelta;
    this.safeWeightDelta = safeWeightDelta;
    this.escapeWeightDelta = escapeWeightDelta;
    this.homeWeightDelta = homeWeightDelta;
    this.protectWeightDelta = protectWeightDelta;
    this.riskWeightDelta = riskWeightDelta;
    this.futureDepthBoost = futureDepthBoost;
    this.futureScoreMult = futureScoreMult;
    this.reduceSideCaptures = reduceSideCaptures;
    this.reason = reason == null ? "" : reason;
    this.enabled = enabled;
  }

  public static DifficultyProfile disabled() {
    return new DifficultyProfile(
        BotStatus.BALANCED,
        AdaptiveStrategy.BOARD_CONTROL,
        50,
        0.0,
        0,
        0,
        0,
        0,
        0,
        0,
        0,
        1.0,
        false,
        "disabled",
        false);
  }

  public BotStatus status() {
    return status;
  }

  public AdaptiveStrategy strategy() {
    return strategy;
  }

  public int aggression() {
    return aggression;
  }

  public double diceAssistRate() {
    return diceAssistRate;
  }

  public int captureWeightDelta() {
    return captureWeightDelta;
  }

  public int safeWeightDelta() {
    return safeWeightDelta;
  }

  public int escapeWeightDelta() {
    return escapeWeightDelta;
  }

  public int homeWeightDelta() {
    return homeWeightDelta;
  }

  public int protectWeightDelta() {
    return protectWeightDelta;
  }

  public int riskWeightDelta() {
    return riskWeightDelta;
  }

  public int futureDepthBoost() {
    return futureDepthBoost;
  }

  public double futureScoreMult() {
    return futureScoreMult;
  }

  public boolean reduceSideCaptures() {
    return reduceSideCaptures;
  }

  public String reason() {
    return reason;
  }

  public boolean enabled() {
    return enabled;
  }

  public String debugLine() {
    return "Status "
        + status
        + " Strategy "
        + strategy
        + " Aggression "
        + aggression
        + " Dice Assist "
        + Math.round(diceAssistRate * 100)
        + "% CaptureΔ "
        + captureWeightDelta
        + " SafeΔ "
        + safeWeightDelta
        + " EscapeΔ "
        + escapeWeightDelta
        + " HomeΔ "
        + homeWeightDelta
        + (reason.isEmpty() ? "" : " (" + reason + ")");
  }
}
