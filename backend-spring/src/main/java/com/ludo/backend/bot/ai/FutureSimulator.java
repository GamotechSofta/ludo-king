package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import org.springframework.stereotype.Component;

/**
 * Production Future Move Simulation Engine — predicts 2–3 plies ahead for HARD bots.
 *
 * <p>Final decision uses: Current Score + Future Score (Danger already folded into current).
 */
@Component
public class FutureSimulator {

  private final FutureSimulatorConfig config;
  private final SimulationEngine simulationEngine;
  private final FutureAnalyzer futureAnalyzer;
  private final SimulationCache cache;
  private final DecisionModifier decisionModifier;
  private final HumanBehaviorEngine humanBehaviorEngine;

  public FutureSimulator(
      FutureSimulatorConfig config,
      SimulationEngine simulationEngine,
      FutureAnalyzer futureAnalyzer,
      SimulationCache cache,
      DecisionModifier decisionModifier,
      HumanBehaviorEngine humanBehaviorEngine
  ) {
    this.config = config;
    this.simulationEngine = simulationEngine;
    this.futureAnalyzer = futureAnalyzer;
    this.cache = cache;
    this.decisionModifier = decisionModifier;
    this.humanBehaviorEngine = humanBehaviorEngine;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public void beginTurn() {
    if (config.cache()) {
      cache.clear();
    }
  }

  /**
   * Expected future score for one legal root move. Time-bounded and pruned.
   */
  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos
  ) {
    return simulate(root, snap, analysis, currentScore, bestCurrentSoFar, deadlineNanos, null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues
  ) {
    return simulate(
        root, snap, analysis, currentScore, bestCurrentSoFar, deadlineNanos, pawnValues, null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    return simulate(
        root,
        snap,
        analysis,
        currentScore,
        bestCurrentSoFar,
        deadlineNanos,
        pawnValues,
        opponents,
        null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive
  ) {
    return simulate(
        root,
        snap,
        analysis,
        currentScore,
        bestCurrentSoFar,
        deadlineNanos,
        pawnValues,
        opponents,
        adaptive,
        null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality
  ) {
    return simulate(
        root,
        snap,
        analysis,
        currentScore,
        bestCurrentSoFar,
        deadlineNanos,
        pawnValues,
        opponents,
        adaptive,
        personality,
        null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    return simulate(
        root,
        snap,
        analysis,
        currentScore,
        bestCurrentSoFar,
        deadlineNanos,
        pawnValues,
        opponents,
        adaptive,
        personality,
        endGame,
        null);
  }

  public SimulationResult simulate(
      MoveCandidate root,
      GameSnapshot snap,
      BotMatchAnalysis analysis,
      int currentScore,
      int bestCurrentSoFar,
      long deadlineNanos,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame,
      BehaviorProfile behavior
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled() || root == null || snap == null) {
      return SimulationResult.empty(root);
    }

    if (config.pruning()
        && bestCurrentSoFar != Integer.MIN_VALUE
        && currentScore + config.pruneMargin() < bestCurrentSoFar) {
      return new SimulationResult(root, new SimulationScore(), 0, true, System.nanoTime() - t0);
    }

    int leader =
        opponents != null && opponents.enabled()
            ? opponents.currentLeaderSeat()
            : (analysis != null ? analysis.leaderSeat : -1);
    int botSeat = analysis != null ? analysis.botSeat : 0;
    SimulationBoard base = SimulationBoard.fromSnapshot(snap, botSeat, leader);

    boolean escaped = simulationEngine.wasEscaping(root, base);
    SimulationBoard afterRoot = base.deepCopy();
    int rootCapture = simulationEngine.applyRoot(afterRoot, root);

    int depth = effectiveDepth(analysis, adaptive, endGame);
    String cacheKey =
        config.cache()
            ? afterRoot.fingerprint()
                + "|d"
                + depth
                + "|p"
                + root.pawnIndex()
                + ":"
                + root.to()
                + (adaptive != null && adaptive.enabled() ? "|a" + adaptive.status() : "")
                + (personality != null && personality.enabled()
                    ? "|pers" + personality.personality()
                    : "")
                + (endGame != null && endGame.active() ? "|eg" : "")
                + (behavior != null && behavior.influential() ? "|bh" + behavior.style() : "")
            : null;
    if (cacheKey != null) {
      SimulationScore cached = cache.get(cacheKey);
      if (cached != null) {
        return new SimulationResult(root, cached, 0, false, System.nanoTime() - t0);
      }
    }

    SimulationScore immediate =
        futureAnalyzer.analyzeRoot(
            afterRoot, root, rootCapture, escaped, analysis, pawnValues, opponents);
    SimulationScore afterRootState =
        futureAnalyzer.analyzeState(afterRoot, analysis, pawnValues, opponents);
    double expected = immediate.total() + afterRootState.total();
    int futures = 0;

    if (depth >= 2 && System.nanoTime() < deadlineNanos) {
      double weight = 1.0 / 6.0;
      for (int die = 1; die <= 6; die++) {
        if (System.nanoTime() >= deadlineNanos) {
          break;
        }
        SimulationBoard b = afterRoot.deepCopy();
        int beforeBotProg = b.progressTotal(b.botSeat());
        int[] beforeTokens = copyBotTokens(b);
        int captured = simulationEngine.applyOpponentDie(b, die);
        SimulationScore branch =
            futureAnalyzer.analyzeState(b, analysis, pawnValues, opponents);
        if (captured == b.botSeat() || b.progressTotal(b.botSeat()) + 15 < beforeBotProg) {
          int lostValue = lostPawnValue(beforeTokens, b, pawnValues, root);
          branch.add("Human Capture Bot", -lostValue);
        }
        expected += branch.total() * weight;
        futures++;

        if (depth >= 3 && System.nanoTime() < deadlineNanos) {
          double sub = weight / 3.0;
          for (int botDie : new int[] {2, 4, 6}) {
            if (System.nanoTime() >= deadlineNanos) {
              break;
            }
            SimulationBoard b2 = b.deepCopy();
            simulationEngine.applyBotReplyDie(b2, botDie);
            SimulationScore deep =
                futureAnalyzer.analyzeState(b2, analysis, pawnValues, opponents);
            expected += deep.total() * sub;
            futures++;
          }
        }
      }
    }

    double adaptiveMult =
        adaptive != null && adaptive.enabled() ? adaptive.futureScoreMult() : 1.0;
    double personalityMult =
        decisionModifier != null ? decisionModifier.futureMultiplier(personality) : 1.0;
    double behaviorMult =
        humanBehaviorEngine != null ? humanBehaviorEngine.futureMultiplier(behavior) : 1.0;
    double mult = adaptiveMult * personalityMult * behaviorMult;
    SimulationScore aggregate = new SimulationScore();
    int rounded = (int) Math.round(expected * mult);
    aggregate.add("Future EV", rounded);

    if (cacheKey != null) {
      cache.put(cacheKey, aggregate);
    }
    return new SimulationResult(root, aggregate, futures, false, System.nanoTime() - t0);
  }

  private int lostPawnValue(
      int[] beforeTokens,
      SimulationBoard after,
      PawnValueReport pawnValues,
      MoveCandidate root
  ) {
    int bot = after.botSeat();
    int maxLost = config.loseAdvancedPenalty();
    for (int p = 0; p < beforeTokens.length; p++) {
      int before = beforeTokens[p];
      int now = after.token(bot, p);
      if (before != now
          && com.ludo.backend.game.BoardConstants.isJail(now)
          && !com.ludo.backend.game.BoardConstants.isJail(before)) {
        int v =
            pawnValues != null && pawnValues.enabled()
                ? Math.max(root != null && root.pawnIndex() == p ? root.pawnValue() : 0,
                    pawnValues.value(p))
                : (root != null ? root.pawnValue() : 80);
        if (v <= 0) {
          v = 80;
        }
        maxLost = Math.max(maxLost, Math.min(220, 40 + v));
      }
    }
    return maxLost;
  }

  private static int[] copyBotTokens(SimulationBoard b) {
    int n = b.pawnCount(b.botSeat());
    int[] t = new int[n];
    for (int i = 0; i < n; i++) {
      t[i] = b.token(b.botSeat(), i);
    }
    return t;
  }

  private int effectiveDepth(
      BotMatchAnalysis analysis, DifficultyProfile adaptive, EndGameProfile endGame
  ) {
    int d = config.depth();
    if (adaptive != null && adaptive.enabled()) {
      d = Math.min(4, d + Math.max(0, adaptive.futureDepthBoost()));
    }
    if (endGame != null && endGame.active()) {
      d = Math.min(4, Math.max(d, endGame.futureDepth()));
    } else {
      d = Math.min(3, d);
    }
    if (analysis != null && analysis.phase == BotGamePhase.EARLY) {
      return Math.min(d, 2);
    }
    return d;
  }
}
