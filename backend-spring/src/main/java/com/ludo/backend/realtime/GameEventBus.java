package com.ludo.backend.realtime;

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
 * Redis cache + Mongo persistence run async after STOMP send.
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

  public void publishSnapshot(String roomId, GameSnapshot snap) {
    publishEvent(roomId, GameEvent.fromSnapshot(GameEvent.typeFor(snap), snap), false);
  }

  public void publishSnapshotForced(String roomId, GameSnapshot snap) {
    publishEvent(roomId, GameEvent.fromSnapshot(GameEvent.STATE, snap), true);
  }

  public void publishSnapshotAndMeta(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap);
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

  private void publishEvent(String roomId, GameEvent event, boolean force) {
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

    GameSnapshot snap = event.getState();
    if (snap != null) {
      redisBridge.ifPresent(bridge -> asyncIo.execute(() -> {
        try {
          bridge.cacheAndPublish(roomId, snap);
        } catch (Exception e) {
          log.debug("async redis publish: {}", e.getMessage());
        }
      }));
      asyncIo.execute(() -> roomService.persistLiveSnapshot(roomId, snap));
    }
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
  }
}
