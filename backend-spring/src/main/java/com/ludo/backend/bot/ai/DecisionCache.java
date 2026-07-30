package com.ludo.backend.bot.ai;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Fingerprint → average simulation score cache for one turn. */
@Component
public class DecisionCache {

  private final MonteCarloConfig config;
  private final ConcurrentHashMap<String, Double> map = new ConcurrentHashMap<>();
  private String boardKey = "";

  public DecisionCache(MonteCarloConfig config) {
    this.config = config;
  }

  public void beginTurn(String fingerprint) {
    String key = fingerprint == null ? "" : fingerprint;
    if (!key.equals(boardKey)) {
      map.clear();
      boardKey = key;
    } else if (!config.cache()) {
      map.clear();
    }
  }

  public void clear() {
    map.clear();
    boardKey = "";
  }

  public Double get(String key) {
    if (!config.cache() || key == null) {
      return null;
    }
    return map.get(key);
  }

  public void put(String key, double value) {
    if (!config.cache() || key == null) {
      return;
    }
    map.put(key, value);
  }
}
