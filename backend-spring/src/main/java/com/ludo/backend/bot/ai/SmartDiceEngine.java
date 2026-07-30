package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Smart Dice Assistance Engine (HARD only, non-kill).
 *
 * <p>May bias face probabilities toward strategically useful legal dice.
 * Never invents illegal faces. Never scores kills — {@code BotKillDiceAssist} owns that.
 */
@Component
public class SmartDiceEngine {

  private static final Logger log = LoggerFactory.getLogger(SmartDiceEngine.class);

  private final SmartDiceConfig config;
  private final DiceEvaluator evaluator;
  private final DiceStrategy strategy;
  private final DiceHistory history;

  public SmartDiceEngine(
      SmartDiceConfig config,
      DiceEvaluator evaluator,
      DiceStrategy strategy,
      DiceHistory history
  ) {
    this.config = config;
    this.evaluator = evaluator;
    this.strategy = strategy;
    this.history = history;
  }

  public boolean enabled() {
    return config.enabled();
  }

  /**
   * @return assisted face 1–6, or {@code null} for fair engine RNG
   */
  public Integer maybePick(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      DiceEvaluator.MoveLegality legality,
      Random rng
  ) {
    return maybePick(roomId, snap, botSeat, difficulty, analysis, legality, rng, null);
  }

  public Integer maybePick(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      DiceEvaluator.MoveLegality legality,
      Random rng,
      DifficultyProfile adaptive
  ) {
    return maybePick(roomId, snap, botSeat, difficulty, analysis, legality, rng, adaptive, null);
  }

  public Integer maybePick(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      DiceEvaluator.MoveLegality legality,
      Random rng,
      DifficultyProfile adaptive,
      PersonalityProfile personality
  ) {
    return maybePick(
        roomId, snap, botSeat, difficulty, analysis, legality, rng, adaptive, personality, null);
  }

  public Integer maybePick(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      DiceEvaluator.MoveLegality legality,
      Random rng,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || snap == null
        || analysis == null
        || legality == null) {
      return null;
    }
    if (analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER) {
      return null;
    }

    Random r = rng != null ? rng : ThreadLocalRandom.current();
    double assistRate = strategy.assistRate(analysis, adaptive);
    if (assistRate <= 0.0 || r.nextDouble() >= assistRate) {
      return null; // fair roll
    }

    List<DiceCandidate> candidates =
        evaluator.evaluateAll(
            snap, botSeat, analysis, legality, history, roomId, personality, endGame);
    // Only keep faces that have at least one legal move OR allow natural miss (all 1-6 always candidates)
    // Faces with no legal move still exist for display fairness but down-scored already.

    DiceProbability.assignWeights(candidates, config.weighted());

    // Anti-cheat: sometimes pick a weak face
    DiceCandidate chosen;
    if (r.nextDouble() < config.badDiceChance()) {
      chosen = pickWeak(candidates, r);
    } else {
      chosen = DiceProbability.pick(candidates, r.nextDouble());
    }
    if (chosen == null) {
      return null;
    }

    int face = chosen.dice();
    if (face < 1 || face > 6) {
      return null;
    }

    history.record(roomId, botSeat, face);

    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder(320);
      sb.append("SmartDice room=").append(roomId).append(" seat=").append(botSeat).append('\n');
      List<DiceCandidate> sorted = new ArrayList<>(candidates);
      sorted.sort(Comparator.comparingInt(DiceCandidate::scoreTotal).reversed());
      for (DiceCandidate c : sorted) {
        sb.append("  Dice ")
            .append(c.dice())
            .append(" Score ")
            .append(c.scoreTotal())
            .append(" Reason ")
            .append(c.reasons())
            .append(" Probability ")
            .append(Math.round(c.probability() * 100))
            .append("%\n");
      }
      sb.append("  Selected Dice ").append(face).append(" assistRate=").append(assistRate);
      log.debug(sb.toString());
    }

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 1_000L && log.isDebugEnabled()) {
      log.debug("SmartDiceEngine {}µs (budget 1000µs)", us);
    }
    return face;
  }

  /** Intentionally pick from the bottom half of scores (human-like bad luck). */
  private static DiceCandidate pickWeak(List<DiceCandidate> candidates, Random r) {
    List<DiceCandidate> sorted = new ArrayList<>(candidates);
    sorted.sort(Comparator.comparingInt(DiceCandidate::scoreTotal));
    int n = Math.max(1, sorted.size() / 2);
    return sorted.get(r.nextInt(n));
  }
}
