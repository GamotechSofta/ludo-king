package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Last-N dice faces per room/bot seat — anti-pattern + natural look. */
@Component
public class DiceHistory {

  private final SmartDiceConfig config;
  private final ConcurrentHashMap<String, Deque<Integer>> byKey = new ConcurrentHashMap<>();

  public DiceHistory(SmartDiceConfig config) {
    this.config = config;
  }

  public void record(String roomId, int botSeat, int dice) {
    if (roomId == null || dice < 1 || dice > 6) {
      return;
    }
    Deque<Integer> q = byKey.computeIfAbsent(key(roomId, botSeat), k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(dice);
      while (q.size() > config.historySize()) {
        q.removeFirst();
      }
    }
  }

  /** How many trailing faces equal {@code dice}. */
  public int trailingStreak(String roomId, int botSeat, int dice) {
    Deque<Integer> q = byKey.get(key(roomId, botSeat));
    if (q == null || q.isEmpty()) {
      return 0;
    }
    synchronized (q) {
      int n = 0;
      Iterator<Integer> it = q.descendingIterator();
      while (it.hasNext()) {
        if (it.next() != dice) {
          break;
        }
        n++;
      }
      return n;
    }
  }

  public void clear(String roomId, int botSeat) {
    byKey.remove(key(roomId, botSeat));
  }

  private static String key(String roomId, int botSeat) {
    return roomId + "#" + botSeat;
  }
}
