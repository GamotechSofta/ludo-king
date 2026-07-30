package com.ludo.backend.bot;

/**
 * Dynamic aggression = probability of using a <em>legal</em> capture die when one
 * exists. Never invents illegal faces — only biases among legal opportunities.
 */
final class BotAggressionPolicy {

  private BotAggressionPolicy() {}

  /**
   * @param vsHuman true when the best available capture target is a human
   */
  static double captureAssistProbability(BotMatchAnalysis analysis, boolean vsHuman) {
    if (analysis == null || !analysis.hardDynamic()) {
      return 0.0;
    }
    BotAiMode mode = analysis.mode;
    BotGamePhase phase = analysis.phase;

    if (mode == BotAiMode.MODE_1) {
      if (phase == BotGamePhase.EARLY) {
        return 0.20;
      }
      if (phase == BotGamePhase.MID) {
        return 0.50;
      }
      return 0.70;
    }

    if (mode == BotAiMode.MODE_2) {
      if (phase == BotGamePhase.EARLY) {
        return vsHuman ? 0.20 : 0.15;
      }
      if (phase == BotGamePhase.MID) {
        return vsHuman ? 0.40 : 0.20;
      }
      return vsHuman ? 0.55 : 0.25;
    }

    if (mode == BotAiMode.MODE_3) {
      if (phase == BotGamePhase.EARLY) {
        return vsHuman ? 0.20 : 0.15;
      }
      if (phase == BotGamePhase.MID) {
        return vsHuman ? 0.35 : 0.20;
      }
      return vsHuman ? 0.50 : 0.25;
    }

    if (mode == BotAiMode.MODE_4) {
      if (phase == BotGamePhase.EARLY) {
        return 0.15;
      }
      if (phase == BotGamePhase.MID) {
        return 0.25;
      }
      return 0.40;
    }

    return 0.0;
  }

  /** Soft scoring multiplier for capture aggression (strategy, not dice). */
  static double captureScoreMultiplier(BotMatchAnalysis analysis, boolean vsHuman) {
    double p = captureAssistProbability(analysis, vsHuman);
    // Map 0.15–0.70 → ~0.7–1.4 scoring weight
    return 0.55 + p;
  }
}
