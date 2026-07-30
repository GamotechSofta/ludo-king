package com.ludo.backend.bot.ai;

import org.springframework.stereotype.Component;

/** Classifies human play style from match-local statistics. */
@Component
public class BehaviorAnalyzer {

  public record Classification(HumanPlayStyle style, double confidence, String reason) {}

  public Classification classify(PlayerStatistics stats, PatternDetector.Patterns patterns) {
    if (stats == null || stats.moves() < 4) {
      return new Classification(HumanPlayStyle.BALANCED, 0.35, "insufficient sample");
    }

    double agg =
        stats.captureRate() * 0.55 + stats.riskRate() * 0.35 + (patterns.capturePreference() ? 0.15 : 0);
    double def =
        stats.safeRate() * 0.45 + stats.escapeRate() * 0.40 + (patterns.safePreference() ? 0.15 : 0);
    double speed = stats.homeRate() * 0.70 + (patterns.homePreference() ? 0.25 : 0);
    double risk = stats.riskRate() * 0.75 + (1.0 - stats.safeRate()) * 0.20;
    double safe = stats.safeRate() * 0.55 + (1.0 - stats.riskRate()) * 0.30 + stats.escapeRate() * 0.15;

    HumanPlayStyle style = HumanPlayStyle.BALANCED;
    double best = 0.42;
    String reason = "balanced mix";

    if (agg >= best && agg >= def && agg >= speed) {
      style = HumanPlayStyle.AGGRESSIVE;
      best = agg;
      reason = "high capture / chase tendency";
    }
    if (speed > best) {
      style = HumanPlayStyle.SPEED_RUNNER;
      best = speed;
      reason = "home race priority";
    }
    if (def > best) {
      style = HumanPlayStyle.DEFENSIVE;
      best = def;
      reason = "safe / escape preference";
    }
    if (risk > best + 0.05 && risk >= 0.48) {
      style = HumanPlayStyle.RISK_TAKER;
      best = risk;
      reason = "frequent unsafe advances";
    }
    if (safe > best + 0.05 && safe >= 0.50 && style != HumanPlayStyle.DEFENSIVE) {
      style = HumanPlayStyle.SAFE_PLAYER;
      best = safe;
      reason = "safe-cell preference";
    }

    // Confidence grows with sample size and score separation
    double sample = Math.min(1.0, stats.moves() / 12.0);
    double conf = Math.min(0.95, 0.40 + best * 0.45 + sample * 0.20);
    return new Classification(style, conf, reason);
  }
}
