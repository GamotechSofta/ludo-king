package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Last-N event history per room/seat for opponent style & capture tracking.
 */
@Component
public class OpponentHistory {

  public enum EventType {
    CAPTURE,
    ESCAPE,
    HOME_ENTRY,
    FINISHED,
    AGGRESSIVE,
    SAFE_MOVE
  }

  private final OpponentAnalysisConfig config;
  private final ConcurrentHashMap<String, Map<Integer, Deque<EventType>>> byRoom =
      new ConcurrentHashMap<>();

  public OpponentHistory(OpponentAnalysisConfig config) {
    this.config = config;
  }

  public void record(String roomId, int seat, EventType type) {
    if (!config.historyEnabled() || roomId == null || type == null) {
      return;
    }
    Map<Integer, Deque<EventType>> room =
        byRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
    Deque<EventType> q = room.computeIfAbsent(seat, k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(type);
      while (q.size() > config.historySize()) {
        q.removeFirst();
      }
    }
  }

  public int count(String roomId, int seat, EventType type) {
    Deque<EventType> q = queue(roomId, seat);
    if (q == null) {
      return 0;
    }
    synchronized (q) {
      int n = 0;
      for (EventType e : q) {
        if (e == type) {
          n++;
        }
      }
      return n;
    }
  }

  public int recentCaptures(String roomId, int seat) {
    return count(roomId, seat, EventType.CAPTURE);
  }

  public PlayStyle inferStyle(String roomId, int seat) {
    if (!config.historyEnabled()) {
      return PlayStyle.UNKNOWN;
    }
    int captures = count(roomId, seat, EventType.CAPTURE);
    int aggressive = count(roomId, seat, EventType.AGGRESSIVE);
    int safe = count(roomId, seat, EventType.SAFE_MOVE);
    int home = count(roomId, seat, EventType.HOME_ENTRY) + count(roomId, seat, EventType.FINISHED);
    int escape = count(roomId, seat, EventType.ESCAPE);
    int total = captures + aggressive + safe + home + escape;
    if (total < 3) {
      return PlayStyle.UNKNOWN;
    }
    if (captures >= Math.max(2, total / 3)) {
      return PlayStyle.CAPTURE_FOCUSED;
    }
    if (aggressive >= safe && aggressive >= home) {
      return PlayStyle.AGGRESSIVE;
    }
    if (home >= Math.max(2, total / 3)) {
      return PlayStyle.FAST_RUNNER;
    }
    if (safe + escape >= Math.max(2, total / 3)) {
      return PlayStyle.SAFE_PLAYER;
    }
    if (safe > aggressive) {
      return PlayStyle.DEFENSIVE;
    }
    return PlayStyle.UNKNOWN;
  }

  public void clear(String roomId) {
    if (roomId != null) {
      byRoom.remove(roomId);
    }
  }

  private Deque<EventType> queue(String roomId, int seat) {
    Map<Integer, Deque<EventType>> room = byRoom.get(roomId);
    if (room == null) {
      return null;
    }
    return room.get(seat);
  }
}
