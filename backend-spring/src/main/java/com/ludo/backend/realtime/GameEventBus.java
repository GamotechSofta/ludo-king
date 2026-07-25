package com.ludo.backend.realtime;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.RoomService;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Single push path for live match updates: STOMP immediately, Redis cache +
 * pub/sub async (never block the action path on Redis/Mongo).
 */
@Service
public class GameEventBus {

  private static final Logger log = LoggerFactory.getLogger(GameEventBus.class);

  private final SimpMessagingTemplate messagingTemplate;
  private final RoomService roomService;
  private final Optional<RedisGameBridge> redisBridge;
  private final ConcurrentHashMap<String, Long> lastPublishedSeq = new ConcurrentHashMap<>();
  private final ExecutorService asyncIo = Executors.newFixedThreadPool(2, r -> {
    Thread t = new Thread(r, "ludo-game-async-io");
    t.setDaemon(true);
    return t;
  });

  public GameEventBus(
      SimpMessagingTemplate messagingTemplate,
      RoomService roomService,
      @Autowired(required = false) RedisGameBridge redisBridge
  ) {
    this.messagingTemplate = messagingTemplate;
    this.roomService = roomService;
    this.redisBridge = Optional.ofNullable(redisBridge);
  }

  /**
   * Broadcast validated snapshot to every subscriber of the room.
   * Skips identical actionSeq republishes (unchanged state) unless forced.
   */
  public void publishSnapshot(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap, false);
  }

  /** Always fan-out (join / reconnect / explicit state sync). */
  public void publishSnapshotForced(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap, true);
  }

  private void publishSnapshot(String roomId, GameSnapshot snap, boolean force) {
    long t0 = System.nanoTime();
    long seq = snap.getActionSeq();
    if (!force) {
      Long prev = lastPublishedSeq.put(roomId, seq);
      if (prev != null && prev == seq) {
        return;
      }
    } else {
      lastPublishedSeq.put(roomId, seq);
    }

    // Fan-out first (hot path) — do not wait for Redis/Mongo
    messagingTemplate.convertAndSend("/topic/room/" + roomId, snap);

    redisBridge.ifPresent(bridge -> asyncIo.execute(() -> {
      try {
        bridge.cacheAndPublish(roomId, snap);
      } catch (Exception e) {
        log.debug("async redis publish: {}", e.getMessage());
      }
    }));

    long ms = (System.nanoTime() - t0) / 1_000_000L;
    if (ms > 15) {
      log.debug("publishSnapshot room={} seq={} took {}ms", roomId, seq, ms);
    }
  }

  public void publishSnapshotAndMeta(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap, false);
    // Room meta is heavier and rarely needed for pawn motion — async
    asyncIo.execute(() -> {
      try {
        roomService.getRoom(roomId).ifPresent(room ->
            messagingTemplate.convertAndSend("/topic/room/" + roomId + "/meta", room)
        );
      } catch (Exception e) {
        log.debug("async meta publish: {}", e.getMessage());
      }
    });
  }

  public Optional<GameSnapshot> loadCachedSnapshot(String roomId) {
    return redisBridge.map(bridge -> bridge.loadSnapshot(roomId));
  }

  public void clearRoom(String roomId) {
    lastPublishedSeq.remove(roomId);
  }
}
