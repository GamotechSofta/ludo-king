package com.ludo.backend.bot.ai;

/**
 * Match-local profile for one human seat + bot adaptive response weights.
 * Influence is capped by {@link BehaviorConfig#maxInfluence()}.
 */
public final class BehaviorProfile {

  private final int humanSeat;
  private final HumanPlayStyle style;
  private final double styleConfidence;
  private final String styleReason;
  private final BehaviorPredictor.Prediction prediction;
  private final PatternDetector.Patterns patterns;
  private final PlayerStatistics stats;
  private final double escapeWeight;
  private final double protectWeight;
  private final double futureWeight;
  private final double homeWeight;
  private final double leaderWeight;
  private final double boardControlWeight;
  private final double safeWeight;
  private final String botResponse;
  private final boolean enabled;
  private final boolean influential;

  public BehaviorProfile(
      int humanSeat,
      HumanPlayStyle style,
      double styleConfidence,
      String styleReason,
      BehaviorPredictor.Prediction prediction,
      PatternDetector.Patterns patterns,
      PlayerStatistics stats,
      double escapeWeight,
      double protectWeight,
      double futureWeight,
      double homeWeight,
      double leaderWeight,
      double boardControlWeight,
      double safeWeight,
      String botResponse,
      boolean enabled,
      boolean influential
  ) {
    this.humanSeat = humanSeat;
    this.style = style == null ? HumanPlayStyle.BALANCED : style;
    this.styleConfidence = Math.max(0, Math.min(1, styleConfidence));
    this.styleReason = styleReason == null ? "" : styleReason;
    this.prediction = prediction == null ? BehaviorPredictor.Prediction.none() : prediction;
    this.patterns = patterns;
    this.stats = stats;
    this.escapeWeight = escapeWeight;
    this.protectWeight = protectWeight;
    this.futureWeight = futureWeight;
    this.homeWeight = homeWeight;
    this.leaderWeight = leaderWeight;
    this.boardControlWeight = boardControlWeight;
    this.safeWeight = safeWeight;
    this.botResponse = botResponse == null ? "" : botResponse;
    this.enabled = enabled;
    this.influential = influential;
  }

  public static BehaviorProfile disabled() {
    return new BehaviorProfile(
        -1,
        HumanPlayStyle.BALANCED,
        0,
        "",
        BehaviorPredictor.Prediction.none(),
        null,
        null,
        1,
        1,
        1,
        1,
        1,
        1,
        1,
        "",
        false,
        false);
  }

  public int humanSeat() {
    return humanSeat;
  }

  public HumanPlayStyle style() {
    return style;
  }

  public double styleConfidence() {
    return styleConfidence;
  }

  public int styleConfidencePct() {
    return (int) Math.round(styleConfidence * 100);
  }

  public BehaviorPredictor.Prediction prediction() {
    return prediction;
  }

  public boolean enabled() {
    return enabled;
  }

  /** True when confidence clears threshold — may apply score influence. */
  public boolean influential() {
    return influential;
  }

  public double escapeWeight() {
    return escapeWeight;
  }

  public double protectWeight() {
    return protectWeight;
  }

  public double futureWeight() {
    return futureWeight;
  }

  public double homeWeight() {
    return homeWeight;
  }

  public double leaderWeight() {
    return leaderWeight;
  }

  public double boardControlWeight() {
    return boardControlWeight;
  }

  public double safeWeight() {
    return safeWeight;
  }

  public String botResponse() {
    return botResponse;
  }

  public String debugLine() {
    return "Human Player "
        + humanSeat
        + " Detected Style "
        + style.displayName()
        + " Confidence "
        + styleConfidencePct()
        + "% Prediction "
        + prediction.detail()
        + " Bot Response "
        + botResponse;
  }
}
