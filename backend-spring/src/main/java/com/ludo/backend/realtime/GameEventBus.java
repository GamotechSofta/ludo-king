package com.ludo.backend.realtime;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameEvent;
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
 * Ordered multiplayer fan-out: compact {@link GameEvent} on the room topic.
 * Redis cache stays hot; Mongo checkpoints are time-coalesced under load.
 */
@Service
public class GameEventBus {

  private static final Logger log = LoggerFactory.getLogger(GameEventBus.class);
  /** Min gap between Mongo writes per room (FINISHED always flushes). */
  private static final long PERSIST_MIN_MS = 5_000L;

  private final SimpMessagingTemplate messagingTemplate;
  private final RoomService roomService;
  private final Optional<RedisGameBridge> redisBridge;
  private final ConcurrentHashMap<String, Long> lastPublishedSeq = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, GameSnapshot> pendingPersist =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> lastPersistAt = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Boolean> persistFlushing = new ConcurrentHashMap<>();
  private final ExecutorService asyncIo =
      Executors.newFixedThreadPool(
          Math.min(16, Math.max(6, Runtime.getRuntime().availableProcessors() * 2)),
          r -> {
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

  public void publishSnapshot(String roomId, GameSnapshot snap) {
    String type = GameEvent.typeFor(snap);
    publishEvent(
        roomId,
        GameEvent.fromSnapshot(type, snap, GameEvent.shouldEmbedFullState(type)),
        false,
        snap
    );
  }

  public void publishSnapshotForced(String roomId, GameSnapshot snap) {
    publishEvent(
        roomId,
        GameEvent.fromSnapshot(GameEvent.STATE, snap, true),
        true,
        snap
    );
  }

  public void publishSnapshotAndMeta(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap);
    asyncIo.execute(
        () -> {
          try {
            roomService
                .getRoom(roomId)
                .ifPresent(
                    room ->
                        messagingTemplate.convertAndSend(
                            "/topic/room/" + roomId + "/meta", room));
          } catch (Exception e) {
            log.debug("async meta publish: {}", e.getMessage());
          }
        });
  }

  private void publishEvent(String roomId, GameEvent event, boolean force, GameSnapshot snap) {
    long seq = event.getActionSeq();
    if (!force) {
      Long prev = lastPublishedSeq.put(roomId, seq);
      if (prev != null && prev == seq) {
        return;
      }
    } else {
      lastPublishedSeq.put(roomId, seq);
    }

    messagingTemplate.convertAndSend("/topic/room/" + roomId, event);

    if (snap != null) {
      redisBridge.ifPresent(
          bridge ->
              asyncIo.execute(
                  () -> {
                    try {
                      bridge.cacheAndPublish(roomId, snap);
                    } catch (Exception e) {
                      log.debug("async redis publish: {}", e.getMessage());
                    }
                  }));
      schedulePersist(roomId, snap);
    }
  }

  private void schedulePersist(String roomId, GameSnapshot snap) {
    pendingPersist.put(roomId, snap);
    boolean force =
        snap.getPhase() != null
            && GameEngineService.PHASE_FINISHED.equals(snap.getPhase());
    long now = System.currentTimeMillis();
    Long last = lastPersistAt.get(roomId);
    if (!force && last != null && now - last < PERSIST_MIN_MS) {
      return;
    }
    if (persistFlushing.putIfAbsent(roomId, Boolean.TRUE) != null) {
      return;
    }
    asyncIo.execute(
        () -> {
          try {
            GameSnapshot latest = pendingPersist.remove(roomId);
            if (latest != null) {
              roomService.persistLiveSnapshot(roomId, latest);
              lastPersistAt.put(roomId, System.currentTimeMillis());
            }
          } catch (Exception e) {
            log.debug("async mongo persist roomId={}: {}", roomId, e.getMessage());
          } finally {
            persistFlushing.remove(roomId);
            GameSnapshot newer = pendingPersist.get(roomId);
            if (newer != null) {
              schedulePersist(roomId, newer);
            }
          }
        });
  }

  public Optional<GameSnapshot> loadCachedSnapshot(String roomId) {
    if (redisBridge.isPresent()) {
      GameSnapshot cached = redisBridge.get().loadSnapshot(roomId);
      if (cached != null) {
        return Optional.of(cached);
      }
    }
    return roomService.loadPersistedSnapshot(roomId);
  }

  public void clearRoom(String roomId) {
    lastPublishedSeq.remove(roomId);
    pendingPersist.remove(roomId);
    lastPersistAt.remove(roomId);
    persistFlushing.remove(roomId);
  }
}
