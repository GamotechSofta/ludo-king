package com.ludo.backend.game;

import com.ludo.backend.config.HumanJailAssistProperties;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Slightly raises the chance of rolling a 6 for human players while all four
 * tokens remain in jail. Never guarantees a six; resets once a six is rolled or
 * a token leaves jail.
 */
@Component
public class HumanJailDiceAssist {

  static final double FAIR_SIX_PROBABILITY = 1.0 / 6.0;

  private final HumanJailAssistProperties props;

  public HumanJailDiceAssist(HumanJailAssistProperties props) {
    this.props = props;
  }

  /** For unit tests without Spring. */
  HumanJailDiceAssist(boolean enabled, int normalRollsBeforeBoost, double boostStep, double maxSixProbability) {
    this.props =
        new HumanJailAssistProperties(
            enabled,
            6,
            normalRollsBeforeBoost,
            boostStep,
            maxSixProbability);
  }

  public boolean isEnabled() {
    return props.enabled();
  }

  /**
   * @param priorFailedRolls consecutive non-6 rolls while all four tokens were jailed
   */
  public double sixProbability(int priorFailedRolls) {
    if (!props.enabled() || priorFailedRolls < props.normalRollsBeforeBoost()) {
      return FAIR_SIX_PROBABILITY;
    }
    int boostLevel = priorFailedRolls - props.normalRollsBeforeBoost() + 1;
    double boosted = FAIR_SIX_PROBABILITY + boostLevel * props.boostStep();
    return Math.min(props.maxSixProbability(), boosted);
  }

  /**
   * Roll 1–6 using weighted six probability for the current jail streak.
   *
   * @param priorFailedRolls consecutive non-6 rolls while all four tokens were jailed
   */
  public int rollDice(Random rng, int priorFailedRolls) {
    double pSix = sixProbability(priorFailedRolls);
    if (rng.nextDouble() < pSix) {
      return 6;
    }
    return rng.nextInt(5) + 1;
  }

  static boolean allTokensInJail(int[] tokenPositions) {
    if (tokenPositions == null || tokenPositions.length == 0) {
      return false;
    }
    for (int pos : tokenPositions) {
      if (!BoardConstants.isJail(pos)) {
        return false;
      }
    }
    return true;
  }
}
