package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Recent adaptive status history per bot seat (strategy-switch smoothing). */
@Component
public class DifficultyHistory {

  private final AdaptiveConfig config;
  private final ConcurrentHashMap<String, Deque<BotStatus>> byKey = new ConcurrentHashMap<>();

  public DifficultyHistory(AdaptiveConfig config) {
    this.config = config;
  }

  public void record(String roomId, int botSeat, BotStatus status) {
    if (status == null) {
      return;
    }
    Deque<BotStatus> q = byKey.computeIfAbsent(key(roomId, botSeat), k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(status);
      while (q.size() > config.historySize()) {
        q.removeFirst();
      }
    }
  }

  /** Prefer sticking with previous status unless new one repeats or is more urgent. */
  public BotStatus smooth(String roomId, int botSeat, BotStatus incoming) {
    if (!config.strategySwitch() || incoming == null) {
      return incoming;
    }
    Deque<BotStatus> q = byKey.get(key(roomId, botSeat));
    if (q == null || q.isEmpty()) {
      return incoming;
    }
    BotStatus last;
    synchronized (q) {
      last = q.peekLast();
    }
    if (last == null || last == incoming) {
      return incoming;
    }
    // Allow immediate upgrade to CRITICAL / immediate drop from CRITICAL when recovered
    if (incoming == BotStatus.CRITICAL || last == BotStatus.CRITICAL) {
      return incoming;
    }
    // Require two samples to flip LEADING ↔ BEHIND
    long sameIncoming = 0;
    synchronized (q) {
      for (BotStatus s : q) {
        if (s == incoming) {
          sameIncoming++;
        }
      }
    }
    if (sameIncoming >= 1) {
      return incoming;
    }
    return last;
  }

  public void clear(String roomId, int botSeat) {
    byKey.remove(key(roomId, botSeat));
  }

  private static String key(String roomId, int botSeat) {
    return (roomId == null ? "_" : roomId) + "#" + botSeat;
  }
}
