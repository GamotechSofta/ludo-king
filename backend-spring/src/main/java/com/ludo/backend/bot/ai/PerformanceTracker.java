package com.ludo.backend.bot.ai;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * In-match performance counters for adaptive tuning (not cross-match ML).
 */
@Component
public class PerformanceTracker {

  private final ConcurrentHashMap<String, Stats> byKey = new ConcurrentHashMap<>();

  public void recordCapture(String roomId, int botSeat, boolean success) {
    stats(roomId, botSeat).captures.incrementAndGet();
    if (success) {
      stats(roomId, botSeat).captureHits.incrementAndGet();
    }
  }

  public void recordEscape(String roomId, int botSeat, boolean success) {
    stats(roomId, botSeat).escapes.incrementAndGet();
    if (success) {
      stats(roomId, botSeat).escapeHits.incrementAndGet();
    }
  }

  public void recordFinished(String roomId, int botSeat) {
    stats(roomId, botSeat).finished.incrementAndGet();
  }

  public void recordTurn(String roomId, int botSeat, BotStatus status) {
    Stats s = stats(roomId, botSeat);
    s.turns.incrementAndGet();
    if (status == BotStatus.BEHIND || status == BotStatus.CRITICAL) {
      s.behindTurns.incrementAndGet();
    }
    if (status == BotStatus.LEADING) {
      s.leadTurns.incrementAndGet();
    }
  }

  public double captureSuccessRate(String roomId, int botSeat) {
    Stats s = stats(roomId, botSeat);
    int n = s.captures.get();
    return n == 0 ? 0.5 : s.captureHits.get() / (double) n;
  }

  public double escapeSuccessRate(String roomId, int botSeat) {
    Stats s = stats(roomId, botSeat);
    int n = s.escapes.get();
    return n == 0 ? 0.5 : s.escapeHits.get() / (double) n;
  }

  public int behindTurnRatioPct(String roomId, int botSeat) {
    Stats s = stats(roomId, botSeat);
    int t = s.turns.get();
    return t == 0 ? 0 : (s.behindTurns.get() * 100) / t;
  }

  public void clear(String roomId, int botSeat) {
    byKey.remove(key(roomId, botSeat));
  }

  private Stats stats(String roomId, int botSeat) {
    return byKey.computeIfAbsent(key(roomId, botSeat), k -> new Stats());
  }

  private static String key(String roomId, int botSeat) {
    return (roomId == null ? "_" : roomId) + "#" + botSeat;
  }

  private static final class Stats {
    final AtomicInteger turns = new AtomicInteger();
    final AtomicInteger behindTurns = new AtomicInteger();
    final AtomicInteger leadTurns = new AtomicInteger();
    final AtomicInteger captures = new AtomicInteger();
    final AtomicInteger captureHits = new AtomicInteger();
    final AtomicInteger escapes = new AtomicInteger();
    final AtomicInteger escapeHits = new AtomicInteger();
    final AtomicInteger finished = new AtomicInteger();
  }
}
