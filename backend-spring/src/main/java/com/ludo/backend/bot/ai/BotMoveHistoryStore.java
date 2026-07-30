package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-room bot move history (last N pawn indices) for anti-repetition scoring.
 */
@Component
public class BotMoveHistoryStore {

  private final ConcurrentHashMap<String, Deque<Integer>> byRoom = new ConcurrentHashMap<>();

  public void record(String roomId, int pawnIndex, int maxSize) {
    if (roomId == null) {
      return;
    }
    Deque<Integer> q = byRoom.computeIfAbsent(roomId, k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(pawnIndex);
      while (q.size() > Math.max(1, maxSize)) {
        q.removeFirst();
      }
    }
  }

  /** Count of consecutive trailing moves of the same pawn. */
  public int consecutiveSamePawn(String roomId, int pawnIndex) {
    Deque<Integer> q = byRoom.get(roomId);
    if (q == null || q.isEmpty()) {
      return 0;
    }
    synchronized (q) {
      int n = 0;
      Iterator<Integer> it = q.descendingIterator();
      while (it.hasNext()) {
        if (it.next() != pawnIndex) {
          break;
        }
        n++;
      }
      return n;
    }
  }

  /** True if the last move (if any) was the same pawn. */
  public boolean lastWasPawn(String roomId, int pawnIndex) {
    Deque<Integer> q = byRoom.get(roomId);
    if (q == null || q.isEmpty()) {
      return false;
    }
    synchronized (q) {
      Integer last = q.peekLast();
      return last != null && last == pawnIndex;
    }
  }

  public int turnCount(String roomId) {
    Deque<Integer> q = byRoom.get(roomId);
    if (q == null) {
      return 0;
    }
    synchronized (q) {
      return q.size();
    }
  }

  public void clear(String roomId) {
    if (roomId != null) {
      byRoom.remove(roomId);
    }
  }
}
