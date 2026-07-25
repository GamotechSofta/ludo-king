package com.ludo.backend.realtime;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Shared quick-match waiting list across backend instances.
 * Key: ludo:queue:{maxPlayers}|{tier} → Redis list of "userId|username|enqueuedAt"
 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisMatchQueue {

  private static final String KEY_PREFIX = "ludo:queue:";
  private static final long ENTRY_TTL_HOURS = 1;

  private final StringRedisTemplate redis;

  public RedisMatchQueue(StringRedisTemplate redis) {
    this.redis = redis;
  }

  public record Entry(String userId, String username, Instant enqueuedAt) {
    String encode() {
      return userId + "|" + username + "|" + enqueuedAt.toEpochMilli();
    }

    static Entry decode(String raw) {
      String[] parts = raw.split("\\|", 3);
      if (parts.length < 3) {
        return null;
      }
      return new Entry(
          parts[0],
          parts[1],
          Instant.ofEpochMilli(Long.parseLong(parts[2]))
      );
    }
  }

  private String key(int maxPlayers, String tier) {
    return KEY_PREFIX + maxPlayers + "|" + tier;
  }

  public void enqueue(int maxPlayers, String tier, String userId, String username) {
    removeFromAll(userId);
    String k = key(maxPlayers, tier);
    redis.opsForList().rightPush(k, new Entry(userId, username, Instant.now()).encode());
    redis.expire(k, ENTRY_TTL_HOURS, TimeUnit.HOURS);
  }

  public void removeFromAll(String userId) {
    // Scan known small key space via pattern
    var keys = redis.keys(KEY_PREFIX + "*");
    if (keys == null) {
      return;
    }
    for (String k : keys) {
      List<String> values = redis.opsForList().range(k, 0, -1);
      if (values == null) {
        continue;
      }
      for (String v : values) {
        if (v.startsWith(userId + "|")) {
          redis.opsForList().remove(k, 0, v);
        }
      }
    }
  }

  public List<Entry> tryTake(int maxPlayers, String tier, int count) {
    String k = key(maxPlayers, tier);
    List<Entry> taken = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String raw = redis.opsForList().leftPop(k);
      if (raw == null) {
        // put back
        for (int j = taken.size() - 1; j >= 0; j--) {
          redis.opsForList().leftPush(k, taken.get(j).encode());
        }
        return List.of();
      }
      Entry e = Entry.decode(raw);
      if (e == null) {
        i--;
        continue;
      }
      taken.add(e);
    }
    return taken;
  }

  public long size(int maxPlayers, String tier) {
    Long n = redis.opsForList().size(key(maxPlayers, tier));
    return n == null ? 0 : n;
  }
}
