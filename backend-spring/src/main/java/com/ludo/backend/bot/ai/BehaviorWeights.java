package com.ludo.backend.bot.ai;

/**
 * Multipliers for AI decision weights (1.0 = neutral).
 * Positive deltas above 1 boost; below 1 reduce.
 */
public final class BehaviorWeights {

  private final double capture;
  private final double home;
  private final double safe;
  private final double escape;
  private final double risk;
  private final double future;
  private final double leaderTarget;
  private final double block;
  private final double opening;

  public BehaviorWeights(
      double capture,
      double home,
      double safe,
      double escape,
      double risk,
      double future,
      double leaderTarget,
      double block,
      double opening
  ) {
    this.capture = capture;
    this.home = home;
    this.safe = safe;
    this.escape = escape;
    this.risk = risk;
    this.future = future;
    this.leaderTarget = leaderTarget;
    this.block = block;
    this.opening = opening;
  }

  public static BehaviorWeights neutral() {
    return new BehaviorWeights(1, 1, 1, 1, 1, 1, 1, 1, 1);
  }

  public static BehaviorWeights forType(BotPersonality type) {
    return switch (type) {
      case AGGRESSIVE ->
          new BehaviorWeights(1.35, 0.90, 0.95, 0.90, 1.20, 1.00, 1.25, 1.15, 1.05);
      case DEFENSIVE ->
          new BehaviorWeights(0.75, 1.10, 1.35, 1.40, 0.80, 1.05, 0.85, 1.10, 0.95);
      case SPEED_RUNNER ->
          new BehaviorWeights(0.80, 1.45, 1.10, 1.05, 0.90, 1.30, 0.90, 0.95, 0.90);
      case OPPORTUNIST ->
          new BehaviorWeights(1.10, 1.05, 1.10, 1.10, 0.90, 1.25, 1.20, 1.05, 1.00);
      default -> neutral();
    };
  }

  public BehaviorWeights blend(BehaviorWeights other, double t) {
    if (other == null) {
      return this;
    }
    double a = Math.max(0, Math.min(1, t));
    double b = 1 - a;
    return new BehaviorWeights(
        capture * b + other.capture * a,
        home * b + other.home * a,
        safe * b + other.safe * a,
        escape * b + other.escape * a,
        risk * b + other.risk * a,
        future * b + other.future * a,
        leaderTarget * b + other.leaderTarget * a,
        block * b + other.block * a,
        opening * b + other.opening * a);
  }

  public BehaviorWeights withVariance(double variance, double[] noise01) {
    if (variance <= 0 || noise01 == null || noise01.length < 9) {
      return this;
    }
    return new BehaviorWeights(
        jitter(capture, variance, noise01[0]),
        jitter(home, variance, noise01[1]),
        jitter(safe, variance, noise01[2]),
        jitter(escape, variance, noise01[3]),
        jitter(risk, variance, noise01[4]),
        jitter(future, variance, noise01[5]),
        jitter(leaderTarget, variance, noise01[6]),
        jitter(block, variance, noise01[7]),
        jitter(opening, variance, noise01[8]));
  }

  private static double jitter(double base, double variance, double u01) {
    double delta = (u01 * 2.0 - 1.0) * variance;
    return Math.max(0.4, Math.min(1.8, base * (1.0 + delta)));
  }

  public double capture() {
    return capture;
  }

  public double home() {
    return home;
  }

  public double safe() {
    return safe;
  }

  public double escape() {
    return escape;
  }

  public double risk() {
    return risk;
  }

  public double future() {
    return future;
  }

  public double leaderTarget() {
    return leaderTarget;
  }

  public double block() {
    return block;
  }

  public double opening() {
    return opening;
  }

  /** Integer display weight centered at 100. */
  public int displayCapture() {
    return (int) Math.round(capture * 100);
  }

  public int displayEscape() {
    return (int) Math.round(escape * 100);
  }

  public int displayHome() {
    return (int) Math.round(home * 100);
  }
}
