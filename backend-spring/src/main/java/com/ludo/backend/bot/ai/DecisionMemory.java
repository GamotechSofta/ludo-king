package com.ludo.backend.bot.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Ring buffer of recent human decisions (match-scoped). */
public final class DecisionMemory {

  private final int capacity;
  private final Deque<BehaviorEvent> events = new ArrayDeque<>();

  public DecisionMemory(int capacity) {
    this.capacity = Math.max(1, capacity);
  }

  public synchronized void add(BehaviorEvent event) {
    if (event == null) {
      return;
    }
    events.addLast(event);
    while (events.size() > capacity) {
      events.removeFirst();
    }
  }

  public synchronized List<BehaviorEvent> snapshot() {
    return new ArrayList<>(events);
  }

  public synchronized int size() {
    return events.size();
  }

  public synchronized void clear() {
    events.clear();
  }
}
