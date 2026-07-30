package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Last-N turn history per room/pawn for adaptive pawn valuation.
 */
@Component
public class PawnHistory {

  public enum EventType {
    MOVED,
    CAPTURED,
    SAFE,
    ESCAPE,
    WASTE
  }

  private final PawnValueConfig config;
  private final ConcurrentHashMap<String, Map<Integer, Deque<EventType>>> byRoom =
      new ConcurrentHashMap<>();

  public PawnHistory(PawnValueConfig config) {
    this.config = config;
  }

  public void record(String roomId, int pawnIndex, EventType type) {
    if (roomId == null || type == null) {
      return;
    }
    Map<Integer, Deque<EventType>> room =
        byRoom.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>());
    Deque<EventType> q = room.computeIfAbsent(pawnIndex, k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(type);
      while (q.size() > config.historySize()) {
        q.removeFirst();
      }
    }
  }

  public PawnStatistics stats(String roomId, int pawnIndex) {
    Map<Integer, Deque<EventType>> room = byRoom.get(roomId);
    if (room == null) {
      return PawnStatistics.empty();
    }
    Deque<EventType> q = room.get(pawnIndex);
    if (q == null || q.isEmpty()) {
      return PawnStatistics.empty();
    }
    synchronized (q) {
      int moved = 0;
      int captured = 0;
      int safe = 0;
      int escape = 0;
      int wasteStreak = 0;
      boolean countingWaste = true;
      for (EventType e : q) {
        switch (e) {
          case MOVED -> moved++;
          case CAPTURED -> captured++;
          case SAFE -> safe++;
          case ESCAPE -> escape++;
          case WASTE -> {
            /* counted in streak below */
          }
        }
      }
      var it = q.descendingIterator();
      while (it.hasNext() && countingWaste) {
        EventType e = it.next();
        if (e == EventType.WASTE) {
          wasteStreak++;
        } else {
          countingWaste = false;
        }
      }
      return new PawnStatistics(moved, captured, safe, escape, wasteStreak);
    }
  }

  public void clear(String roomId) {
    if (roomId != null) {
      byRoom.remove(roomId);
    }
  }

  /** Test/helper: copy of event counts without mutating. */
  Map<Integer, Integer> moveCounts(String roomId) {
    Map<Integer, Integer> out = new HashMap<>();
    Map<Integer, Deque<EventType>> room = byRoom.get(roomId);
    if (room == null) {
      return out;
    }
    for (var e : room.entrySet()) {
      out.put(e.getKey(), e.getValue().size());
    }
    return out;
  }
}
