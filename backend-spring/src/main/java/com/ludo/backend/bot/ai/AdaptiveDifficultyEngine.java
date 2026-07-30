package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Adaptive Difficulty Engine (HARD only).
 *
 * <p>Continuously switches aggression, scoring weights, dice-assist rate, and future
 * simulation emphasis from live match state — without breaking Ludo rules.
 */
@Component
public class AdaptiveDifficultyEngine {

  private static final Logger log = LoggerFactory.getLogger(AdaptiveDifficultyEngine.class);

  private final AdaptiveConfig config;
  private final MatchAnalyzer matchAnalyzer;
  private final AdaptiveAnalyzer adaptiveAnalyzer;
  private final StrategySelector strategySelector;
  private final PerformanceTracker performanceTracker;
  private final DifficultyHistory history;

  private final Map<String, Cached> cache = new HashMap<>();

  public AdaptiveDifficultyEngine(
      AdaptiveConfig config,
      MatchAnalyzer matchAnalyzer,
      AdaptiveAnalyzer adaptiveAnalyzer,
      StrategySelector strategySelector,
      PerformanceTracker performanceTracker,
      DifficultyHistory history
  ) {
    this.config = config;
    this.matchAnalyzer = matchAnalyzer;
    this.adaptiveAnalyzer = adaptiveAnalyzer;
    this.strategySelector = strategySelector;
    this.performanceTracker = performanceTracker;
    this.history = history;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public AdaptiveConfig config() {
    return config;
  }

  public PerformanceTracker performance() {
    return performanceTracker;
  }

  public DifficultyProfile evaluate(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      OpponentAnalysisReport opponents,
      DangerMap dangerMap
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || analysis == null
        || analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER) {
      return DifficultyProfile.disabled();
    }

    String fp = fingerprint(snap, botSeat, analysis, opponents);
    String ck = cacheKey(roomId, botSeat);
    Cached hit = cache.get(ck);
    if (hit != null && fp.equals(hit.fingerprint)) {
      return hit.profile;
    }

    MatchAnalyzer.MatchSnapshot ms =
        matchAnalyzer.analyze(snap, botSeat, analysis, opponents, dangerMap);
    BotStatus raw = adaptiveAnalyzer.detectStatus(ms, roomId);
    BotStatus status = history.smooth(roomId, botSeat, raw);
    history.record(roomId, botSeat, status);
    performanceTracker.recordTurn(roomId, botSeat, status);

    BotGamePhase phase = analysis.phase != null ? analysis.phase : BotGamePhase.MID;
    AdaptiveStrategy strategy = strategySelector.select(status, phase, ms, analysis);
    int aggression = adaptiveAnalyzer.baseAggression(status, analysis, phase);

    double perfBoost = 0;
    if (performanceTracker.escapeSuccessRate(roomId, botSeat) < 0.35
        && (status == BotStatus.BEHIND || status == BotStatus.CRITICAL)) {
      perfBoost += 0.03;
    }
    if (performanceTracker.captureSuccessRate(roomId, botSeat) < 0.35
        && status == BotStatus.CRITICAL) {
      perfBoost += 0.02;
    }

    DifficultyProfile profile =
        strategySelector.buildProfile(status, strategy, aggression, ms, analysis, perfBoost);

    cache.put(ck, new Cached(fp, profile));

    if (log.isDebugEnabled()) {
      log.debug("AdaptiveDifficulty {}", profile.debugLine());
    }

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 2_000L && log.isDebugEnabled()) {
      log.debug("AdaptiveDifficultyEngine {}µs (budget 2000µs)", us);
    }
    return profile;
  }

  public void invalidate(String roomId, int botSeat) {
    cache.remove(cacheKey(roomId, botSeat));
  }

  private static String cacheKey(String roomId, int botSeat) {
    return (roomId == null ? "_" : roomId) + "#" + botSeat;
  }

  private static String fingerprint(
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis analysis,
      OpponentAnalysisReport opponents
  ) {
    StringBuilder sb = new StringBuilder(64);
    sb.append(botSeat).append('|');
    if (analysis != null) {
      sb.append(analysis.phase).append('|').append(analysis.botBehind).append('|');
    }
    if (opponents != null) {
      sb.append(opponents.currentLeaderSeat()).append('|');
    }
    if (snap != null && snap.getTokenPositions() != null) {
      sb.append(snap.getTokenPositions().hashCode());
    }
    return sb.toString();
  }

  private record Cached(String fingerprint, DifficultyProfile profile) {}
}
