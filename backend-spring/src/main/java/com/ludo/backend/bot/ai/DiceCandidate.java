package com.ludo.backend.bot.ai;

import java.util.List;

/** One evaluated die face (1–6) with score and selection weight. */
public final class DiceCandidate {

  private final int dice;
  private final DiceScore score;
  private double weight;
  private double probability;

  public DiceCandidate(int dice, DiceScore score) {
    this.dice = dice;
    this.score = score != null ? score : new DiceScore();
  }

  public int dice() {
    return dice;
  }

  public DiceScore score() {
    return score;
  }

  public int scoreTotal() {
    return score.total();
  }

  public List<String> reasons() {
    return score.reasons();
  }

  public double weight() {
    return weight;
  }

  public void setWeight(double weight) {
    this.weight = Math.max(0.0, weight);
  }

  public double probability() {
    return probability;
  }

  public void setProbability(double probability) {
    this.probability = probability;
  }
}
