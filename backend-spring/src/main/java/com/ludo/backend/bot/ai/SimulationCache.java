package com.ludo.backend.bot.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** LRU-ish cache for repeated simulation fingerprints within one decide() call. */
@Component
public class SimulationCache {

  private static final int MAX = 256;

  private final ThreadLocal<Map<String, SimulationScore>> local =
      ThreadLocal.withInitial(
          () ->
              new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, SimulationScore> eldest) {
                  return size() > MAX;
                }
              });

  public SimulationScore get(String key) {
    return local.get().get(key);
  }

  public void put(String key, SimulationScore score) {
    if (key != null && score != null) {
      local.get().put(key, score);
    }
  }

  public void clear() {
    local.get().clear();
  }
}
