package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.room.BotDifficulty;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Dynamic Bot Personality Engine (HARD only).
 *
 * <p>Assigns a unique style per bot seat and applies weight modifiers without
 * changing Ludo rules.
 */
@Component
public class BotPersonalityEngine {

  private static final Logger log = LoggerFactory.getLogger(BotPersonalityEngine.class);

  private final PersonalityConfig config;
  private final PersonalitySelector selector;
  private final PersonalityHistory history;
  private final DecisionModifier decisionModifier;

  public BotPersonalityEngine(
      PersonalityConfig config,
      PersonalitySelector selector,
      PersonalityHistory history,
      DecisionModifier decisionModifier
  ) {
    this.config = config;
    this.selector = selector;
    this.history = history;
    this.decisionModifier = decisionModifier;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public DecisionModifier modifier() {
    return decisionModifier;
  }

  /**
   * Resolve (assign once) + evolve + variance for this turn.
   */
  public PersonalityProfile evaluate(
      String roomId,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      DifficultyProfile adaptive
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || analysis == null
        || analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER) {
      return PersonalityProfile.disabled();
    }

    BotPersonality type = history.getAssigned(roomId, botSeat);
    if (type == null) {
      type = selector.select();
      history.assign(roomId, botSeat, type);
      if (log.isDebugEnabled()) {
        log.debug(
            "Assigned personality room={} seat={} -> {}",
            roomId,
            botSeat,
            type.displayName());
      }
    }

    BehaviorWeights base = BehaviorWeights.forType(type);
    String evoLabel = "";
    BehaviorWeights evolved = base;

    if (config.evolution()) {
      Evolution e = evolve(type, base, analysis, adaptive);
      evolved = e.weights();
      evoLabel = e.label();
      if (!evoLabel.isEmpty()) {
        history.recordEvolution(roomId, botSeat, evoLabel);
      }
    }

    // Anti-predictability: ±variance each turn
    double[] noise = new double[9];
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    for (int i = 0; i < noise.length; i++) {
      noise[i] = rng.nextDouble();
    }
    BehaviorWeights effective = evolved.withVariance(config.randomVariance(), noise);

    PersonalityProfile profile =
        new PersonalityProfile(type, base, effective, evoLabel, true);

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 1_000L && log.isDebugEnabled()) {
      log.debug("BotPersonalityEngine {}µs (budget 1000µs)", us);
    }
    return profile;
  }

  public void clear(String roomId, int botSeat) {
    history.clear(roomId, botSeat);
  }

  /**
   * When End Game Master is active, blend personality further toward finish strategy
   * without replacing the archetype.
   */
  public PersonalityProfile applyEndGameConvergence(
      PersonalityProfile profile, EndGameProfile endGame
  ) {
    if (profile == null || !profile.enabled() || endGame == null || !endGame.active()) {
      return profile;
    }
    double t = endGame.personalityFinishBlend();
    BehaviorWeights finish =
        switch (profile.personality()) {
          case AGGRESSIVE ->
              // Controlled Aggressive
              new BehaviorWeights(0.95, 1.35, 1.20, 1.15, 0.95, 1.30, 1.05, 1.00, 0.85);
          case DEFENSIVE ->
              new BehaviorWeights(0.65, 1.40, 1.40, 1.40, 0.70, 1.20, 0.75, 1.10, 0.85);
          case SPEED_RUNNER ->
              new BehaviorWeights(0.60, 1.55, 1.20, 1.15, 0.80, 1.40, 0.80, 0.90, 0.80);
          case OPPORTUNIST ->
              new BehaviorWeights(0.85, 1.35, 1.20, 1.20, 0.85, 1.35, 1.10, 1.00, 0.85);
          default ->
              new BehaviorWeights(0.75, 1.40, 1.20, 1.15, 0.85, 1.30, 0.90, 1.00, 0.85);
        };
    BehaviorWeights blended = profile.weights().blend(finish, t);
    String label =
        switch (profile.personality()) {
          case AGGRESSIVE -> "Controlled Aggressive";
          case SPEED_RUNNER -> "Finish Sprint";
          default -> "Finish Focused";
        };
    return new PersonalityProfile(
        profile.personality(), profile.baseWeights(), blended, label, true);
  }

  private Evolution evolve(
      BotPersonality type,
      BehaviorWeights base,
      BotMatchAnalysis analysis,
      DifficultyProfile adaptive
  ) {
    BotGamePhase phase = analysis != null ? analysis.phase : BotGamePhase.MID;
    boolean behind =
        (analysis != null && analysis.botBehind)
            || (adaptive != null
                && adaptive.enabled()
                && (adaptive.status() == BotStatus.BEHIND
                    || adaptive.status() == BotStatus.CRITICAL));
    boolean leading =
        (analysis != null && analysis.botIsLeader)
            || (adaptive != null && adaptive.enabled() && adaptive.status() == BotStatus.LEADING);
    boolean end = phase == BotGamePhase.END;
    boolean fourth =
        adaptive != null && adaptive.enabled() && adaptive.reduceSideCaptures();

    // Near victory → all personalities converge toward finish
    if (end || fourth || (leading && end)) {
      BehaviorWeights finish =
          new BehaviorWeights(0.75, 1.40, 1.15, 1.10, 0.85, 1.25, 0.90, 1.00, 0.85);
      return new Evolution(base.blend(finish, 0.45), "Finish Focused");
    }

    if (type == BotPersonality.AGGRESSIVE && behind) {
      BehaviorWeights recovery =
          new BehaviorWeights(1.40, 1.10, 1.15, 1.20, 1.15, 1.15, 1.30, 1.10, 1.05);
      return new Evolution(base.blend(recovery, 0.35), "Aggressive Recovery");
    }

    if (type == BotPersonality.DEFENSIVE && leading) {
      BehaviorWeights finish =
          new BehaviorWeights(0.70, 1.35, 1.30, 1.25, 0.75, 1.15, 0.80, 1.05, 0.90);
      return new Evolution(base.blend(finish, 0.30), "Finish Focused");
    }

    if (type == BotPersonality.SPEED_RUNNER && behind) {
      BehaviorWeights push =
          new BehaviorWeights(0.95, 1.50, 1.15, 1.15, 1.00, 1.35, 1.00, 1.00, 1.00);
      return new Evolution(base.blend(push, 0.25), "Speed Push");
    }

    if (type == BotPersonality.OPPORTUNIST
        && adaptive != null
        && adaptive.enabled()
        && adaptive.status() == BotStatus.CRITICAL) {
      BehaviorWeights hunt =
          new BehaviorWeights(1.20, 1.15, 1.15, 1.20, 0.95, 1.35, 1.30, 1.05, 1.00);
      return new Evolution(base.blend(hunt, 0.30), "Opportunity Hunt");
    }

    return new Evolution(base, "");
  }

  private record Evolution(BehaviorWeights weights, String label) {}
}
