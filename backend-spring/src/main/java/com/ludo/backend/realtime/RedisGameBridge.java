package com.ludo.backend.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Caches live match snapshots and fans out over pub/sub.
 * On fan-in, restores the local MatchRuntime so every instance shares one logical session.
 */
public class RedisGameBridge implements MessageListener {

  private static final Logger log = LoggerFactory.getLogger(RedisGameBridge.class);
  public static final String CHANNEL = "ludo:game:events";
  public static final String SNAP_KEY_PREFIX = "ludo:match:";
  private static final Duration SNAP_TTL = Duration.ofHours(2);

  private final StringRedisTemplate redis;
  private final ObjectMapper objectMapper;
  private final SimpMessagingTemplate messagingTemplate;
  private final ObjectProvider<GameEngineService> gameEngine;
  private final String instanceId = UUID.randomUUID().toString();

  public RedisGameBridge(
      StringRedisTemplate redis,
      ObjectMapper objectMapper,
      SimpMessagingTemplate messagingTemplate,
      ObjectProvider<GameEngineService> gameEngine
  ) {
    this.redis = redis;
    this.objectMapper = objectMapper;
    this.messagingTemplate = messagingTemplate;
    this.gameEngine = gameEngine;
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
      GameEngineService engine = gameEngine.getIfAvailable();
      if (engine != null) {
        try {
          engine.restoreFromSnapshot(snap);
        } catch (Exception e) {
          log.debug("restore on fan-in: {}", e.getMessage());
        }
      }
      // Re-emit as GameEvent for local WS subscribers
      com.ludo.backend.game.GameEvent event =
          com.ludo.backend.game.GameEvent.fromSnapshot(
              com.ludo.backend.game.GameEvent.typeFor(snap), snap);
      messagingTemplate.convertAndSend("/topic/room/" + env.roomId(), event);
    } catch (Exception e) {
      log.warn("Redis fan-in failed: {}", e.getMessage());
    }
  }

  public record MapEnvelope(String origin, String roomId, String snapshotJson) {
  }
}
