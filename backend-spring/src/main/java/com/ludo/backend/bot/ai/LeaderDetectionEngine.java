package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Leader Detection & Opponent Analysis Engine (HARD only).
 *
 * <p>Each HARD bot independently analyses all seats — bots never cooperate.
 */
@Component
public class LeaderDetectionEngine {

  private static final Logger log = LoggerFactory.getLogger(LeaderDetectionEngine.class);

  private final OpponentAnalysisConfig config;
  private final OpponentAnalyzer analyzer;
  private final TargetSelector targetSelector;
  private final OpponentHistory history;

  private final Map<String, Cached> cache = new HashMap<>();

  public LeaderDetectionEngine(
      OpponentAnalysisConfig config,
      OpponentAnalyzer analyzer,
      TargetSelector targetSelector,
      OpponentHistory history
  ) {
    this.config = config;
    this.analyzer = analyzer;
    this.targetSelector = targetSelector;
    this.history = history;
  }

  public boolean enabled() {
    return config.enabled();
  }

  public OpponentAnalysisConfig config() {
    return config;
  }

  public OpponentHistory history() {
    return history;
  }

  public OpponentAnalysisReport analyze(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis match,
      DangerMap dangerMap
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled() || snap == null) {
      return OpponentAnalysisReport.disabled(botSeat);
    }

    String fp = fingerprint(snap, botSeat, match);
    Cached hit = cache.get(cacheKey(roomId, botSeat));
    if (hit != null && fp.equals(hit.fingerprint)) {
      return hit.report;
    }

    List<OpponentProfile> raw = analyzer.analyzeAll(roomId, snap, botSeat, match, dangerMap);
    List<OpponentProfile> selected = targetSelector.select(raw, botSeat, match);
    ThreatRanking ranking = new ThreatRanking(selected);

    OpponentProfile leader = ranking.currentLeader();
    // Tie-break similar scores by winning probability (already in ThreatRanking)
    int leaderSeat = leader != null ? leader.seat() : (match != null ? match.leaderSeat : -1);
    int targetSeat = targetSelector.attackPrioritySeat(selected, botSeat);

    boolean behind = match != null && match.botBehind;
    boolean leading = match != null && match.botIsLeader;
    // Prefer engine leader for behind/leading vs that seat
    if (leader != null) {
      leading = leader.seat() == botSeat;
      OpponentProfile self = null;
      for (OpponentProfile p : selected) {
        if (p.seat() == botSeat) {
          self = p;
          break;
        }
      }
      if (self != null && leader.seat() != botSeat) {
        behind =
            self.leaderScore().total() + config.similarScoreMargin() < leader.leaderScore().total();
      }
    }

    OpponentAnalysisReport report =
        new OpponentAnalysisReport(
            selected, ranking, leaderSeat, targetSeat, botSeat, true, behind, leading);
    cache.put(cacheKey(roomId, botSeat), new Cached(fp, report));

    if (log.isDebugEnabled()) {
      StringBuilder sb = new StringBuilder(320);
      sb.append("Opponent Analysis botSeat=").append(botSeat).append('\n');
      for (OpponentProfile p : ranking.byLeaderScore()) {
        sb.append("  ").append(p.debugLine()).append('\n');
        if (p.leaderScore() != null) {
          for (String r : p.leaderScore().reasons()) {
            sb.append("    ").append(r).append('\n');
          }
        }
      }
      sb.append("  Current Leader Seat ")
          .append(leaderSeat)
          .append(" Target ")
          .append(targetSeat)
          .append('\n');
      log.debug(sb.toString());
    }

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 2_000L && log.isDebugEnabled()) {
      log.debug("LeaderDetectionEngine {}µs (budget 2000µs)", us);
    }
    return report;
  }

  public void recordCapture(String roomId, int attackerSeat, int victimSeat) {
    history.record(roomId, attackerSeat, OpponentHistory.EventType.CAPTURE);
    history.record(roomId, attackerSeat, OpponentHistory.EventType.AGGRESSIVE);
  }

  public void recordBotMove(String roomId, MoveCandidate move, boolean captured) {
    if (roomId == null || move == null) {
      return;
    }
    // Bot's own style tracking is optional; capture events on victim seats handled separately
    if (captured) {
      history.record(roomId, move.victimSeat(), OpponentHistory.EventType.ESCAPE);
    }
    if (com.ludo.backend.game.BoardConstants.isHome(move.to())
        || com.ludo.backend.game.BoardConstants.isExit(move.to())) {
      history.record(roomId, -1, OpponentHistory.EventType.HOME_ENTRY);
    }
  }

  public void invalidate(String roomId, int botSeat) {
    cache.remove(cacheKey(roomId, botSeat));
  }

  private static String cacheKey(String roomId, int botSeat) {
    return (roomId == null ? "_" : roomId) + "#" + botSeat;
  }

  private static String fingerprint(GameSnapshot snap, int botSeat, BotMatchAnalysis match) {
    StringBuilder sb = new StringBuilder(96);
    sb.append(botSeat).append('|');
    if (match != null) {
      sb.append(match.phase).append('|');
    }
    if (snap.getTokenPositions() != null) {
      sb.append(snap.getTokenPositions());
    }
    if (snap.getIsBot() != null) {
      sb.append(Arrays.toString(snap.getIsBot()));
    }
    return sb.toString();
  }

  private record Cached(String fingerprint, OpponentAnalysisReport report) {}
}
