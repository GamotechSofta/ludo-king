package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Pawn Value & Strategic Priority Engine (HARD bot).
 *
 * <p>Evaluates every bot pawn once per decide() call and caches until the board fingerprint changes.
 */
@Component
public class PawnValueEngine {

  private static final Logger log = LoggerFactory.getLogger(PawnValueEngine.class);

  private final PawnValueConfig config;
  private final PawnValueCalculator calculator;
  private final PawnHistory history;
  private final PawnDecision decision;

  /** Per-room cache: fingerprint → priorities. */
  private final Map<String, Cached> cache = new HashMap<>();

  public PawnValueEngine(
      PawnValueConfig config,
      PawnValueCalculator calculator,
      PawnHistory history,
      PawnDecision decision
  ) {
    this.config = config;
    this.calculator = calculator;
    this.history = history;
    this.decision = decision;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public PawnDecision decision() {
    return decision;
  }

  public PawnValueConfig config() {
    return config;
  }

  /**
   * Evaluate all bot pawns. Reuses cache when board fingerprint unchanged.
   */
  public PawnValueReport evaluate(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      LudoColor color,
      List<Integer> ownPositions,
      BotMatchAnalysis analysis,
      DangerMap dangerMap
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled() || ownPositions == null || color == null) {
      return PawnValueReport.disabled(ownPositions == null ? 0 : ownPositions.size());
    }

    String fp = fingerprint(ownPositions, botSeat, analysis);
    Cached hit = cache.get(roomId);
    if (hit != null && fp.equals(hit.fingerprint)) {
      return hit.report;
    }

    int bestProgress = 0;
    int finished = 0;
    for (Integer p : ownPositions) {
      int pos = p == null ? JAIL : p;
      if (isHome(pos)) {
        finished++;
      } else {
        bestProgress = Math.max(bestProgress, BotBoardMath.pawnProgress(color, pos));
      }
    }

    int startTile = color.startTile();
    List<PawnPriority> list = new ArrayList<>(ownPositions.size());
    for (int i = 0; i < ownPositions.size(); i++) {
      int pos = ownPositions.get(i) == null ? JAIL : ownPositions.get(i);
      int danger = 0;
      if (dangerMap != null && !isJail(pos) && !isHome(pos) && !isSafe(pos)) {
        danger = dangerMap.dangerAt(pos);
      }
      PawnState state =
          calculator.buildState(i, pos, color, bestProgress, danger, finished, startTile);
      PawnStatistics stats = history.stats(roomId, i);
      list.add(calculator.calculate(state, stats, analysis));
    }

    list.sort(Comparator.comparingInt(PawnPriority::value).reversed());
    PawnValueReport report = new PawnValueReport(list, true);
    cache.put(roomId, new Cached(fp, report));

    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder(256);
      sb.append("Pawn Values room=").append(roomId).append('\n');
      for (PawnPriority p : list) {
        sb.append("  ").append(p.debugLine()).append('\n');
      }
      log.debug(sb.toString());
    }

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 1_000L && log.isDebugEnabled()) {
      log.debug("PawnValueEngine {}µs (budget 1000µs)", us);
    }
    return report;
  }

  public void recordMoveOutcome(
      String roomId,
      MoveCandidate chosen,
      boolean escaped,
      boolean reachedSafe
  ) {
    if (!config.enabled() || chosen == null || roomId == null) {
      return;
    }
    int pawn = chosen.pawnIndex();
    history.record(roomId, pawn, PawnHistory.EventType.MOVED);
    if (escaped) {
      history.record(roomId, pawn, PawnHistory.EventType.ESCAPE);
    }
    if (reachedSafe) {
      history.record(roomId, pawn, PawnHistory.EventType.SAFE);
    }
    boolean waste =
        !chosen.capture()
            && !isHome(chosen.to())
            && !isSafe(chosen.to())
            && !chosen.underThreatAtFrom()
            && !chosen.createsBlock();
    if (waste) {
      history.record(roomId, pawn, PawnHistory.EventType.WASTE);
    }
  }

  public void recordCapture(String roomId, int pawnIndex) {
    if (config.enabled() && roomId != null) {
      history.record(roomId, pawnIndex, PawnHistory.EventType.CAPTURED);
    }
  }

  public void invalidate(String roomId) {
    if (roomId != null) {
      cache.remove(roomId);
    }
  }

  public int valueOrFallback(PawnValueReport report, int pawnIndex, LudoColor color, int pos) {
    if (report != null && report.enabled()) {
      PawnPriority p = report.get(pawnIndex);
      if (p != null) {
        return p.value();
      }
    }
    return BoardAnalysisCache.pawnValue(color, pos);
  }

  private static String fingerprint(
      List<Integer> own, int botSeat, BotMatchAnalysis analysis
  ) {
    StringBuilder sb = new StringBuilder(48);
    sb.append(botSeat).append('|');
    if (analysis != null) {
      sb.append(analysis.phase).append('|').append(analysis.leaderSeat).append('|');
    }
    sb.append(Arrays.toString(own.toArray()));
    return sb.toString();
  }

  private record Cached(String fingerprint, PawnValueReport report) {}
}
