package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import org.springframework.stereotype.Component;

/**
 * Applies personality weight modifiers onto a {@link MoveScore}.
 * Only adjusts AI weights — never changes legality.
 */
@Component
public class DecisionModifier {

  private final AIScoreConfig scoreConfig;

  public DecisionModifier(AIScoreConfig scoreConfig) {
    this.scoreConfig = scoreConfig;
  }

  public void apply(MoveScore score, MoveCandidate move, PersonalityProfile profile) {
    if (score == null || move == null || profile == null || !profile.enabled()) {
      return;
    }
    BehaviorWeights w = profile.weights();

    if (move.capture()) {
      int delta = percentDelta(scoreConfig.captureBonus(), w.capture());
      if (move.victimIsLeader()) {
        delta += percentDelta(scoreConfig.captureLeaderBonus() / 2, w.leaderTarget());
      }
      if (delta != 0) {
        score.add("Personality Capture", delta);
      }
    }
    if (isHome(move.to()) || isExit(move.to())) {
      int delta = percentDelta(scoreConfig.homeBonus() / 2, w.home());
      if (delta != 0) {
        score.add("Personality Home", delta);
      }
    }
    if (isSafe(move.to())) {
      int delta = percentDelta(scoreConfig.safeBonus() / 2, w.safe());
      if (delta != 0) {
        score.add("Personality Safe", delta);
      }
    }
    if (move.underThreatAtFrom() && move.threatCountAtTo() == 0) {
      int delta = percentDelta(scoreConfig.escapeBonus() / 2, w.escape());
      if (delta != 0) {
        score.add("Personality Escape", delta);
      }
    }
    if (move.threatCountAtTo() > 0 && !isSafe(move.to()) && !isHome(move.to())) {
      // risk mult >1 means more willing to take risk → less penalty
      int basePen = scoreConfig.riskPenalty() / 2;
      int delta = (int) Math.round(basePen * (1.0 - w.risk()));
      if (delta != 0) {
        score.add("Personality Risk", delta);
      }
    }
    if (move.createsBlock()) {
      int delta = percentDelta(scoreConfig.blockBonus() / 2, w.block());
      if (delta != 0) {
        score.add("Personality Block", delta);
      }
    }
    if (isJail(move.from()) && move.diceValue() == 6) {
      int delta = percentDelta(scoreConfig.openPawnBonus() / 2, w.opening());
      if (delta != 0) {
        score.add("Personality Opening", delta);
      }
    }
  }

  /** Future EV multiplier from personality. */
  public double futureMultiplier(PersonalityProfile profile) {
    if (profile == null || !profile.enabled()) {
      return 1.0;
    }
    return profile.weights().future();
  }

  /**
   * Extra die-face score bias for Smart Dice (non-kill). Positive favors that outcome.
   */
  public int diceOutcomeBias(PersonalityProfile profile, String outcomeKey) {
    if (profile == null || !profile.enabled() || outcomeKey == null) {
      return 0;
    }
    BehaviorWeights w = profile.weights();
    return switch (outcomeKey) {
      case "home" -> (int) Math.round(40 * (w.home() - 1.0));
      case "safe" -> (int) Math.round(30 * (w.safe() - 1.0));
      case "escape" -> (int) Math.round(35 * (w.escape() - 1.0));
      case "open" -> (int) Math.round(20 * (w.opening() - 1.0));
      default -> 0;
    };
  }

  private static int percentDelta(int base, double mult) {
    if (base == 0) {
      return 0;
    }
    return (int) Math.round(base * (mult - 1.0));
  }
}
