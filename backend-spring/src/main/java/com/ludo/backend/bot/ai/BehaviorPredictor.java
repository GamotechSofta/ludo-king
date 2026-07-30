package com.ludo.backend.bot.ai;

import org.springframework.stereotype.Component;

/**
 * Estimates the human's most likely next intent from visible history only.
 * Never predicts dice faces or hidden state.
 */
@Component
public class BehaviorPredictor {

  public record Prediction(String intent, double confidence, String detail) {
    public static Prediction none() {
      return new Prediction("unknown", 0, "");
    }
  }

  public Prediction predict(
      HumanPlayStyle style,
      PlayerStatistics stats,
      PatternDetector.Patterns patterns,
      double styleConfidence
  ) {
    if (stats == null || stats.moves() < 4 || patterns == null) {
      return Prediction.none();
    }

    String intent;
    String detail;
    double base =
        switch (style == null ? HumanPlayStyle.BALANCED : style) {
          case AGGRESSIVE -> {
            intent = "chase capture";
            detail = "Likely to chase capture";
            yield 0.55 + stats.captureRate() * 0.35;
          }
          case SPEED_RUNNER -> {
            intent = "advance home";
            detail = "Likely to prioritize home path";
            yield 0.55 + stats.homeRate() * 0.35;
          }
          case DEFENSIVE, SAFE_PLAYER -> {
            intent = "seek safety";
            detail = "Likely to move to safe cells";
            yield 0.55 + stats.safeRate() * 0.30;
          }
          case RISK_TAKER -> {
            intent = "risky advance";
            detail = "Likely to take risky shortcuts";
            yield 0.50 + stats.riskRate() * 0.35;
          }
          default -> {
            intent = "develop board";
            detail = "Likely balanced development";
            yield 0.45;
          }
        };

    if (patterns.favouriteStrategy() != null && !"unknown".equals(patterns.favouriteStrategy())) {
      detail = "Likely to " + patterns.favouriteStrategy();
    }

    // Blend style confidence; never claim certainty
    double conf = Math.min(0.92, base * 0.65 + styleConfidence * 0.35);
    return new Prediction(intent, conf, detail);
  }
}
