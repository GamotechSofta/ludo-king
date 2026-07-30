package com.ludo.backend.bot.ai;

/**
 * Single scored reason line for debug / logging.
 *
 * <p>Example: {@code +100 Safe Cell}
 */
public final class ScoreReason {

  private final String label;
  private final int delta;

  public ScoreReason(String label, int delta) {
    this.label = label == null ? "" : label;
    this.delta = delta;
  }

  public String label() {
    return label;
  }

  public int delta() {
    return delta;
  }

  @Override
  public String toString() {
    String sign = delta >= 0 ? "+" : "";
    return sign + delta + " " + label;
  }
}
