package com.ludo.backend.realtime;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomService;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Single push path for live match updates: STOMP immediately, Redis cache +
 * pub/sub when available (low-latency fan-out / multi-instance).
 */
@Service
public class GameEventBus {

  private final SimpMessagingTemplate messagingTemplate;
  private final RoomService roomService;
  private final Optional<RedisGameBridge> redisBridge;

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
    messagingTemplate.convertAndSend("/topic/room/" + roomId, snap);
    redisBridge.ifPresent(bridge -> bridge.cacheAndPublish(roomId, snap));
  }

  public void publishSnapshotAndMeta(String roomId, GameSnapshot snap) {
    publishSnapshot(roomId, snap);
    roomService.getRoom(roomId).ifPresent(room ->
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/meta", room)
    );
  }

  public Optional<GameSnapshot> loadCachedSnapshot(String roomId) {
    return redisBridge.map(bridge -> bridge.loadSnapshot(roomId));
  }
}
