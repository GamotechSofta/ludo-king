package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Tracks assigned personality + recent evolution labels per room/seat. */
@Component
public class PersonalityHistory {

  private final ConcurrentHashMap<String, BotPersonality> assigned = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Deque<String>> evolutionLog = new ConcurrentHashMap<>();

  public BotPersonality getAssigned(String roomId, int botSeat) {
    return assigned.get(key(roomId, botSeat));
  }

  public void assign(String roomId, int botSeat, BotPersonality personality) {
    if (personality != null) {
      assigned.put(key(roomId, botSeat), personality);
    }
  }

  public void recordEvolution(String roomId, int botSeat, String label) {
    if (label == null || label.isBlank()) {
      return;
    }
    Deque<String> q =
        evolutionLog.computeIfAbsent(key(roomId, botSeat), k -> new ArrayDeque<>());
    synchronized (q) {
      q.addLast(label);
      while (q.size() > 12) {
        q.removeFirst();
      }
    }
  }

  public void clear(String roomId, int botSeat) {
    String k = key(roomId, botSeat);
    assigned.remove(k);
    evolutionLog.remove(k);
  }

  private static String key(String roomId, int botSeat) {
    return (roomId == null ? "_" : roomId) + "#" + botSeat;
  }
}
