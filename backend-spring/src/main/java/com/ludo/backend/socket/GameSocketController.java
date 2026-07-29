package com.ludo.backend.socket;

import com.ludo.backend.bot.BotTurnCoordinator;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.RoomService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final GameEventBus gameEventBus;
  private final BotTurnCoordinator botTurnCoordinator;

  public GameSocketController(
      GameEngineService gameEngineService,
      RoomService roomService,
      GameEventBus gameEventBus,
      BotTurnCoordinator botTurnCoordinator
  ) {
    this.gameEngineService = gameEngineService;
    this.roomService = roomService;
    this.gameEventBus = gameEventBus;
    this.botTurnCoordinator = botTurnCoordinator;
  }

  public record ActionMessage(String userId, Integer tokenIndex, Integer diceIndex) {
  }

  @MessageMapping("/room/{roomId}/join")
  public void join(@DestinationVariable String roomId, @Payload ActionMessage msg) {
    try {
      roomService.reconnect(roomId, msg.userId());
    } catch (Exception ignored) {
      // ignore
    }
    ensureLocalSession(roomId);
    if (gameEngineService.hasMatch(roomId)) {
      syncState(roomId, gameEngineService.getSnapshot(roomId));
      botTurnCoordinator.schedule(roomId);
    }
  }

  @MessageMapping("/room/{roomId}/state")
  public void state(@DestinationVariable String roomId, @Payload ActionMessage msg) {
    ensureLocalSession(roomId);
    if (gameEngineService.hasMatch(roomId)) {
      syncState(roomId, gameEngineService.getSnapshot(roomId));
    }
  }

  @MessageMapping("/room/{roomId}/roll")
  public void roll(@DestinationVariable String roomId, @Payload ActionMessage msg) {
    ensureLocalSession(roomId);
    try {
      GameSnapshot snap = gameEngineService.rollDice(roomId, msg.userId());
      broadcast(roomId, snap);
      botTurnCoordinator.schedule(roomId);
    } catch (IllegalStateException | IllegalArgumentException e) {
      if (gameEngineService.hasMatch(roomId)) {
        syncState(roomId, gameEngineService.getSnapshot(roomId));
      }
    }
  }

  @MessageMapping("/room/{roomId}/move")
  public void move(@DestinationVariable String roomId, @Payload ActionMessage msg) {
    ensureLocalSession(roomId);
    try {
      GameSnapshot snap = gameEngineService.moveToken(
          roomId,
          msg.userId(),
          msg.tokenIndex() == null ? 0 : msg.tokenIndex(),
          msg.diceIndex() == null ? 0 : msg.diceIndex()
      );
      broadcast(roomId, snap);
      botTurnCoordinator.schedule(roomId);
    } catch (IllegalStateException | IllegalArgumentException e) {
      if (gameEngineService.hasMatch(roomId)) {
        syncState(roomId, gameEngineService.getSnapshot(roomId));
      }
    }
  }

  private void ensureLocalSession(String roomId) {
    if (gameEngineService.hasMatch(roomId)) {
      return;
    }
    gameEventBus.loadCachedSnapshot(roomId).ifPresentOrElse(
        snap -> {
          snap.setRoomId(roomId);
          gameEngineService.restoreFromSnapshot(snap);
        },
        () -> roomService.getRoom(roomId).ifPresent(room -> {
          if (room.getStatus() == com.ludo.backend.room.RoomStatus.IN_PROGRESS
              || room.getStatus() == com.ludo.backend.room.RoomStatus.WAITING_RECONNECT) {
            try {
              roomService.rehydrateMatch(room);
            } catch (RuntimeException ignored) {
              // no snapshot yet
            }
          }
        })
    );
  }

  private void syncState(String roomId, GameSnapshot snap) {
    gameEventBus.publishSnapshotForced(roomId, snap);
  }

  private void broadcast(String roomId, GameSnapshot snap) {
    gameEventBus.publishSnapshotAndMeta(roomId, snap);
    if (GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      roomService.settleIfFinishedAsync(roomId, snap);
    }
  }
}
