package com.ludo.backend.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.backend.game.GameSnapshot;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Caches live match snapshots in Redis and fans out updates over pub/sub so
 * every backend instance can push WebSocket frames with minimal delay.
 *
 * Registered only when Redis is available — see {@link RedisGameBridgeConfig}.
 */
public class RedisGameBridge implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RedisGameBridge.class);
  public static final String CHANNEL = "ludo:game:events";
  public static final String SNAP_KEY_PREFIX = "ludo:match:";
  private static final Duration SNAP_TTL = Duration.ofHours(2);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final String instanceId = UUID.randomUUID().toString();

  public RedisGameBridge(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      SimpMessagingTemplate messagingTemplate
  ) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.messagingTemplate = messagingTemplate;
  }

  public void cacheAndPublish(String roomId, GameSnapshot snap) {
    try {
      String json = objectMapper.writeValueAsString(snap);
      redis.opsForValue().set(SNAP_KEY_PREFIX + roomId, json, SNAP_TTL);
      String envelope = objectMapper.writeValueAsString(
          new MapEnvelope(instanceId, roomId, json)
      );
      redis.convertAndSend(CHANNEL, envelope);
    } catch (Exception e) {
      log.warn("Redis publish failed for room {}: {}", roomId, e.getMessage());
    }
  }

  public GameSnapshot loadSnapshot(String roomId) {
    try {
      String json = redis.opsForValue().get(SNAP_KEY_PREFIX + roomId);
      if (json == null || json.isBlank()) {
        return null;
      }
      return objectMapper.readValue(json, GameSnapshot.class);
    } catch (Exception e) {
      log.warn("Redis load failed for room {}: {}", roomId, e.getMessage());
      return null;
    }
  }

  public void deleteSnapshot(String roomId) {
    try {
      redis.delete(SNAP_KEY_PREFIX + roomId);
    } catch (Exception ignored) {
      // ignore
    }
  }

  @Override
  public void onMessage(Message message, byte[] pattern) {
    try {
      String body = new String(message.getBody());
      MapEnvelope env = objectMapper.readValue(body, MapEnvelope.class);
      if (instanceId.equals(env.origin())) {
        return;
      }
      GameSnapshot snap = objectMapper.readValue(env.snapshotJson(), GameSnapshot.class);
      messagingTemplate.convertAndSend("/topic/room/" + env.roomId(), snap);
    } catch (Exception e) {
      log.warn("Redis fan-in failed: {}", e.getMessage());
    }
  }

  public record MapEnvelope(String origin, String roomId, String snapshotJson) {
  }
}
