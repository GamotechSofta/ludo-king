package com.ludo.backend.bot.ai;

import java.util.List;

/** Converts dice scores into selection weights / probabilities. */
public final class DiceProbability {

  private DiceProbability() {}

  /**
   * Softmax-ish positive weights from scores. Never zero for a legal face —
   * floor keeps weak dice selectable (anti-cheat look).
   */
  public static void assignWeights(List<DiceCandidate> candidates, boolean weighted) {
    if (candidates == null || candidates.isEmpty()) {
      return;
    }
    if (!weighted) {
      for (DiceCandidate c : candidates) {
        c.setWeight(1.0);
      }
      normalize(candidates);
      return;
    }
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (DiceCandidate c : candidates) {
      min = Math.min(min, c.scoreTotal());
      max = Math.max(max, c.scoreTotal());
    }
    // shift so weakest is small but positive; amplify spread
    double floor = 8.0;
    double range = Math.max(1, max - min);
    for (DiceCandidate c : candidates) {
      double shifted = (c.scoreTotal() - min) + floor;
      // Slight extra emphasis on better scores without hard-max picking
      double emphasis = 1.0 + (c.scoreTotal() - min) / (range * 2.0);
      c.setWeight(shifted * emphasis);
    }
    normalize(candidates);
  }

  public static void normalize(List<DiceCandidate> candidates) {
    double sum = 0;
    for (DiceCandidate c : candidates) {
      sum += c.weight();
    }
    if (sum <= 0) {
      double eq = 1.0 / candidates.size();
      for (DiceCandidate c : candidates) {
        c.setWeight(1.0);
        c.setProbability(eq);
      }
      return;
    }
    for (DiceCandidate c : candidates) {
      c.setProbability(c.weight() / sum);
    }
  }

  /** Weighted random pick; {@code rng} in [0,1). */
  public static DiceCandidate pick(List<DiceCandidate> candidates, double rng01) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    double r = Math.max(0.0, Math.min(0.999999, rng01));
    double acc = 0;
    for (DiceCandidate c : candidates) {
      acc += c.probability();
      if (r < acc) {
        return c;
      }
    }
    return candidates.get(candidates.size() - 1);
  }
}
