package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * HARD-bot decision brain:
 * Opponent Analysis → Pawn Value → Danger Map → Current Score → Future → Final.
 */
@Service
public class BotDecisionEngine {

  private static final Logger log = LoggerFactory.getLogger(BotDecisionEngine.class);

  private final AIScoreConfig config;
  private final DangerMapConfig dangerConfig;
  private final FutureSimulatorConfig futureConfig;
  private final MoveCandidateFactory candidateFactory;
  private final MoveScoreCalculator scoreCalculator;
  private final BotMoveHistoryStore historyStore;
  private final ThreatCache threatCache;
  private final ThreatAnalyzer threatAnalyzer;
  private final FutureSimulator futureSimulator;
  private final PawnValueEngine pawnValueEngine;
  private final LeaderDetectionEngine leaderDetectionEngine;
  private final AdaptiveDifficultyEngine adaptiveDifficultyEngine;
  private final BotPersonalityEngine personalityEngine;
  private final EndGameEngine endGameEngine;
  private final MonteCarloEngine monteCarloEngine;
  private final HumanBehaviorEngine humanBehaviorEngine;

  public BotDecisionEngine(
      AIScoreConfig config,
      DangerMapConfig dangerConfig,
      FutureSimulatorConfig futureConfig,
      MoveCandidateFactory candidateFactory,
      MoveScoreCalculator scoreCalculator,
      BotMoveHistoryStore historyStore,
      ThreatCache threatCache,
      ThreatAnalyzer threatAnalyzer,
      FutureSimulator futureSimulator,
      PawnValueEngine pawnValueEngine,
      LeaderDetectionEngine leaderDetectionEngine,
      AdaptiveDifficultyEngine adaptiveDifficultyEngine,
      BotPersonalityEngine personalityEngine,
      EndGameEngine endGameEngine,
      MonteCarloEngine monteCarloEngine,
      HumanBehaviorEngine humanBehaviorEngine
  ) {
    this.config = config;
    this.dangerConfig = dangerConfig;
    this.futureConfig = futureConfig;
    this.candidateFactory = candidateFactory;
    this.scoreCalculator = scoreCalculator;
    this.historyStore = historyStore;
    this.threatCache = threatCache;
    this.threatAnalyzer = threatAnalyzer;
    this.futureSimulator = futureSimulator;
    this.pawnValueEngine = pawnValueEngine;
    this.leaderDetectionEngine = leaderDetectionEngine;
    this.adaptiveDifficultyEngine = adaptiveDifficultyEngine;
    this.personalityEngine = personalityEngine;
    this.endGameEngine = endGameEngine;
    this.monteCarloEngine = monteCarloEngine;
    this.humanBehaviorEngine = humanBehaviorEngine;
  }

  public boolean usesProductionScoring(BotDifficultyLike difficulty) {
    return config.enabled() && difficulty != null && difficulty.isHard();
  }

  public int[] decide(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      LudoColor color,
      List<Integer> ownPositions,
      List<int[]> legalMoves,
      BotMatchAnalysis analysis
  ) {
    long t0 = System.nanoTime();
    if (legalMoves == null || legalMoves.isEmpty()) {
      return null;
    }

    DangerMap dangerMap =
        threatCache.getOrBuild(roomId, snap, botSeat, color, analysis);

    OpponentAnalysisReport opponents =
        leaderDetectionEngine.analyze(roomId, snap, botSeat, analysis, dangerMap);

    // Prefer engine leader seat for candidate factory / cache consistency
    BotMatchAnalysis effective = analysis;
    if (opponents.enabled()
        && opponents.currentLeaderSeat() >= 0
        && analysis != null
        && opponents.currentLeaderSeat() != analysis.leaderSeat) {
      effective =
          new BotMatchAnalysis(
              analysis.mode,
              analysis.phase,
              analysis.difficulty,
              analysis.botSeat,
              analysis.humanCount,
              analysis.botCount,
              analysis.playerCount,
              analysis.isBot,
              opponents.currentLeaderSeat(),
              analysis.seatProgress,
              analysis.finishedPawns,
              analysis.activePawns,
              analysis.tableProgress,
              opponents.botBehind(),
              opponents.botLeading(),
              analysis.allowAggressiveLeaderHunt);
    }

    PawnValueReport pawnValues =
        pawnValueEngine.evaluate(
            roomId, snap, botSeat, color, ownPositions, effective, dangerMap);

    DifficultyProfile adaptive =
        adaptiveDifficultyEngine.evaluate(
            roomId,
            snap,
            botSeat,
            effective.difficulty,
            effective,
            opponents,
            dangerMap);

    PersonalityProfile personality =
        personalityEngine.evaluate(
            roomId, botSeat, effective.difficulty, effective, adaptive);

    BoardAnalysisCache cache =
        BoardAnalysisCache.build(snap, botSeat, color, ownPositions, effective);
    List<MoveCandidate> candidates =
        candidateFactory.buildAll(
            legalMoves, snap.getDiceList(), cache, pawnValues, opponents);
    if (candidates.isEmpty()) {
      return legalMoves.get(0);
    }

    EndGameProfile endGame =
        endGameEngine.evaluate(
            effective.difficulty,
            effective,
            cache,
            ownPositions,
            color,
            candidates,
            dangerMap,
            opponents,
            adaptive,
            personality);

    if (endGame.active()) {
      personality = personalityEngine.applyEndGameConvergence(personality, endGame);
    }

    BehaviorProfile behavior =
        humanBehaviorEngine.evaluate(roomId, effective.difficulty, effective, opponents);

    futureSimulator.beginTurn();
    long deadline = t0 + futureConfig.maxTimeNs();

    List<ScoredDecision> decisions = new ArrayList<>(candidates.size());
    int bestCurrent = Integer.MIN_VALUE;

    for (MoveCandidate c : candidates) {
      MoveScore current =
          scoreCalculator.score(
              c,
              cache,
              effective,
              roomId,
              historyStore,
              dangerMap,
              pawnValues,
              opponents,
              adaptive,
              personality,
              endGame,
              behavior);
      bestCurrent = Math.max(bestCurrent, current.total());
      decisions.add(new ScoredDecision(c, current, 0));
    }

    if (futureSimulator.enabled()) {
      for (ScoredDecision d : decisions) {
        if (System.nanoTime() >= deadline) {
          break;
        }
        SimulationResult sim =
            futureSimulator.simulate(
                d.candidate,
                snap,
                effective,
                d.current.total(),
                bestCurrent,
                deadline,
                pawnValues,
                opponents,
                adaptive,
                personality,
                endGame,
                behavior);
        d.future = sim.futureTotal();
        d.sim = sim;
      }
    }

    decisions.sort(
        Comparator.comparingInt(ScoredDecision::finalScore)
            .reversed()
            .thenComparing(
                (a, b) -> {
                  PawnPriority pa = pawnValues.get(a.candidate.pawnIndex());
                  PawnPriority pb = pawnValues.get(b.candidate.pawnIndex());
                  int da =
                      dangerMap != null ? dangerMap.dangerAt(a.candidate.to()) : 0;
                  int db =
                      dangerMap != null ? dangerMap.dangerAt(b.candidate.to()) : 0;
                  return -pawnValueEngine
                      .decision()
                      .tieBreakDelta(a.candidate, pa, da, b.candidate, pb, db);
                }));

    // Final layer: Monte Carlo (failsafe → Future Simulation ranking)
    ScoredDecision chosen = null;
    if (monteCarloEngine != null && monteCarloEngine.enabled()) {
      chosen =
          monteCarloEngine.select(
              decisions,
              snap,
              effective,
              effective.difficulty,
              dangerMap,
              pawnValues,
              opponents,
              endGame,
              personality,
              t0);
    }
    if (chosen == null) {
      chosen = pickWithSmartRandomness(decisions);
    }

    if (log.isDebugEnabled()) {
      logDebug(
          decisions,
          chosen,
          dangerMap,
          cache,
          pawnValues,
          opponents,
          adaptive,
          personality,
          endGame,
          behavior);
    }

    boolean escaped =
        chosen.candidate.underThreatAtFrom()
            && (dangerMap == null || dangerMap.dangerAt(chosen.candidate.to()) < 40);
    boolean reachedSafe =
        com.ludo.backend.game.BoardConstants.isSafe(chosen.candidate.to())
            || com.ludo.backend.game.BoardConstants.isHome(chosen.candidate.to());
    pawnValueEngine.recordMoveOutcome(roomId, chosen.candidate, escaped, reachedSafe);
    if (chosen.candidate.capture()) {
      leaderDetectionEngine.recordCapture(roomId, botSeat, chosen.candidate.victimSeat());
      adaptiveDifficultyEngine
          .performance()
          .recordCapture(roomId, botSeat, true);
    }
    if (escaped) {
      adaptiveDifficultyEngine.performance().recordEscape(roomId, botSeat, true);
    }
    historyStore.record(roomId, chosen.candidate.pawnIndex(), config.historySize());
    threatCache.invalidate(roomId);
    pawnValueEngine.invalidate(roomId);
    leaderDetectionEngine.invalidate(roomId, botSeat);
    adaptiveDifficultyEngine.invalidate(roomId, botSeat);

    long elapsedUs = (System.nanoTime() - t0) / 1_000L;
    if (elapsedUs > 5_000L && log.isDebugEnabled()) {
      log.debug("BotDecisionEngine analysis room={} {}µs (budget 5000µs)", roomId, elapsedUs);
    }

    return chosen.candidate.rawMove();
  }

  ScoredDecision pickWithSmartRandomness(List<ScoredDecision> sortedDesc) {
    if (sortedDesc.size() == 1) {
      return sortedDesc.get(0);
    }
    ScoredDecision a = sortedDesc.get(0);
    ScoredDecision b = sortedDesc.get(1);
    if (Math.abs(a.finalScore() - b.finalScore()) < config.randomnessThreshold()) {
      return ThreadLocalRandom.current().nextBoolean() ? a : b;
    }
    return a;
  }

  private void logDebug(
      List<ScoredDecision> decisions,
      ScoredDecision chosen,
      DangerMap map,
      BoardAnalysisCache cache,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame,
      BehaviorProfile behavior
  ) {
    StringBuilder sb = new StringBuilder(1000);
    sb.append("Bot Turn Behavior+EndGame+Personality+Adaptive+Current+Future:\n");
    if (behavior != null && behavior.enabled()) {
      sb.append("  ").append(behavior.debugLine()).append('\n');
    }
    if (endGame != null && endGame.active()) {
      sb.append("  ").append(endGame.debugHeader()).append('\n');
      for (EndGameDecision ed : endGame.decisions()) {
        boolean sel =
            chosen != null
                && ed.move() != null
                && ed.move().pawnIndex() == chosen.candidate.pawnIndex()
                && ed.move().to() == chosen.candidate.to();
        sb.append("  ").append(ed.debugLine(sel)).append('\n');
      }
    }
    if (personality != null && personality.enabled()) {
      sb.append("  ").append(personality.debugLine()).append('\n');
    }
    if (adaptive != null && adaptive.enabled()) {
      sb.append("  ").append(adaptive.debugLine()).append('\n');
    }
    if (opponents != null && opponents.enabled()) {
      for (OpponentProfile p : opponents.ranking().byLeaderScore()) {
        sb.append("  ").append(p.debugLine()).append('\n');
      }
      sb.append("  Current Leader ")
          .append(opponents.currentLeaderSeat())
          .append(" Target ")
          .append(opponents.primaryTargetSeat())
          .append('\n');
    }
    if (pawnValues != null && pawnValues.enabled()) {
      for (PawnPriority p : pawnValues.ranked()) {
        sb.append("  ").append(p.debugLine()).append('\n');
      }
    }
    for (ScoredDecision d : decisions) {
      MoveCandidate c = d.candidate;
      int prog = com.ludo.backend.bot.BotBoardMath.pawnProgress(cache.color(), c.from());
      DangerReport r =
          threatAnalyzer.reportForMove(c, map, prog, cache.bestOwnProgress());
      boolean selected = d == chosen;
      sb.append("  Move Pawn ")
          .append(c.pawnIndex())
          .append(" Value ")
          .append(c.pawnValue())
          .append(" Current ")
          .append(d.current.total())
          .append(" Future ")
          .append(d.future)
          .append(" DangerDest ")
          .append(r.destinationDanger())
          .append(" Final ")
          .append(d.finalScore())
          .append(selected ? " Selected YES" : "")
          .append('\n');
    }
    log.debug(sb.toString());
  }

  static final class ScoredDecision {
    final MoveCandidate candidate;
    final MoveScore current;
    int future;
    SimulationResult sim;

    ScoredDecision(MoveCandidate candidate, MoveScore current, int future) {
      this.candidate = candidate;
      this.current = current;
      this.future = future;
    }

    int finalScore() {
      return current.total() + future;
    }
  }

  public interface BotDifficultyLike {
    boolean isHard();
  }
}
