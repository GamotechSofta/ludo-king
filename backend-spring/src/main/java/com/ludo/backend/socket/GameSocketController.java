package com.ludo.backend.socket;

import com.ludo.backend.bot.BotService;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.BotDifficulty;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class GameSocketController {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final BotService botService;
  private final GameEventBus gameEventBus;
  private final ExecutorService botExecutor = Executors.newCachedThreadPool();

  public GameSocketController(
      GameEngineService gameEngineService,
      RoomService roomService,
      BotService botService,
      GameEventBus gameEventBus
  ) {
    this.gameEngineService = gameEngineService;
    this.roomService = roomService;
    this.botService = botService;
    this.gameEventBus = gameEventBus;
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
      maybeScheduleBot(roomId);
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
      maybeScheduleBot(roomId);
    } catch (IllegalStateException | IllegalArgumentException e) {
      // Duplicate roll / wrong turn — ignore; client already has authoritative state
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
      maybeScheduleBot(roomId);
    } catch (IllegalStateException | IllegalArgumentException e) {
      if (gameEngineService.hasMatch(roomId)) {
        syncState(roomId, gameEngineService.getSnapshot(roomId));
      }
    }
  }

  /** Restore MatchRuntime from Redis/Mongo — never blank create. */
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

  /** Forced state sync for join/reconnect — never skipped by actionSeq dedupe. */
  private void syncState(String roomId, GameSnapshot snap) {
    gameEventBus.publishSnapshotForced(roomId, snap);
  }

  private void broadcast(String roomId, GameSnapshot snap) {
    // Broadcast first; Mongo settlement is async so move latency stays low
    gameEventBus.publishSnapshotAndMeta(roomId, snap);
    if (GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      botExecutor.execute(() ->
          roomService.getRoom(roomId).ifPresent(room -> roomService.settleIfFinished(room, snap))
      );
    }
  }

  private void maybeScheduleBot(String roomId) {
    botExecutor.submit(() -> {
      try {
        GameSnapshot snap = gameEngineService.getSnapshot(roomId);
        while (snap.getIsBot() != null
            && snap.getCurrentSeatIndex() < snap.getIsBot().length
            && snap.getIsBot()[snap.getCurrentSeatIndex()]
            && !GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {

          BotDifficulty diff = BotDifficulty.MEDIUM;
          Room room = roomService.getRoom(roomId).orElse(null);
          if (room != null) {
            RoomPlayer p = room.getPlayers().get(snap.getCurrentSeatIndex());
            if (p.getBotDifficulty() != null) {
              diff = p.getBotDifficulty();
            }
          }
          snap = botService.takeTurnIfBot(
              roomId,
              diff,
              step -> gameEventBus.publishSnapshot(roomId, step)
          );
        }
        // Ensure clients see final seat (e.g. after bot chain → human turn)
        gameEventBus.publishSnapshot(roomId, gameEngineService.getSnapshot(roomId));
      } catch (Exception e) {
        // bot path errors are non-fatal for the human client
        try {
          if (gameEngineService.hasMatch(roomId)) {
            gameEventBus.publishSnapshot(
                roomId,
                gameEngineService.getSnapshot(roomId)
            );
          }
        } catch (Exception ignored) {
          // ignore recovery publish failure
        }
      }
    });
  }
}
