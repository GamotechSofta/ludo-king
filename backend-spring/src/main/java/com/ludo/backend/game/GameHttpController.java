package com.ludo.backend.game;

import com.ludo.backend.bot.BotTurnCoordinator;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP fallback for roll/move when STOMP is unavailable (common on some hosts).
 */
@RestController
@RequestMapping("/api/rooms/{roomId}/game")
public class GameHttpController {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final GameEventBus gameEventBus;
  private final BotTurnCoordinator botTurnCoordinator;

  public GameHttpController(
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

  public record ActionRequest(String userId, Integer tokenIndex, Integer diceIndex) {
  }

  @GetMapping
  public GameSnapshot get(@PathVariable String roomId) {
    if (gameEngineService.hasMatch(roomId)) {
      return gameEngineService.getSnapshot(roomId);
    }
    return gameEventBus.loadCachedSnapshot(roomId)
        .map(gameEngineService::restoreFromSnapshot)
        .orElseGet(() -> {
          ensureMatch(roomId);
          return gameEngineService.getSnapshot(roomId);
        });
  }

  @PostMapping("/roll")
  public GameSnapshot roll(
      @PathVariable String roomId,
      @RequestBody ActionRequest req
  ) {
    ensureMatch(roomId);
    GameSnapshot snap = gameEngineService.rollDice(roomId, req.userId());
    broadcast(roomId, snap);
    botTurnCoordinator.schedule(roomId);
    return snap;
  }

  @PostMapping("/move")
  public GameSnapshot move(
      @PathVariable String roomId,
      @RequestBody ActionRequest req
  ) {
    ensureMatch(roomId);
    GameSnapshot snap = gameEngineService.moveToken(
        roomId,
        req.userId(),
        req.tokenIndex() == null ? 0 : req.tokenIndex(),
        req.diceIndex() == null ? 0 : req.diceIndex()
    );
    broadcast(roomId, snap);
    botTurnCoordinator.schedule(roomId);
    return snap;
  }

  /**
   * Ensure the single live MatchRuntime is present. Restores from Redis/Mongo —
   * never creates a fresh jail board for an in-progress game.
   */
  private void ensureMatch(String roomId) {
    if (gameEngineService.hasMatch(roomId)) {
      return;
    }
    Room room = roomService.getRoom(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getPlayers() == null || room.getPlayers().isEmpty()) {
      throw new IllegalStateException("Room has no players");
    }
    if (room.getStatus() == com.ludo.backend.room.RoomStatus.WAITING
        || room.getStatus() == com.ludo.backend.room.RoomStatus.READY) {
      throw new IllegalStateException("Match not started yet — waiting for ready/countdown");
    }
    if (room.getStatus() == com.ludo.backend.room.RoomStatus.COMPLETED) {
      throw new IllegalStateException("Match finished — start a new game");
    }

    GameSnapshot cached = gameEventBus.loadCachedSnapshot(roomId).orElse(null);
    if (cached != null) {
      cached.setRoomId(roomId);
      gameEngineService.restoreFromSnapshot(cached);
      return;
    }
    roomService.rehydrateMatch(room);
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleGameError(RuntimeException e) {
    String msg = e.getMessage() == null ? "Game error" : e.getMessage();
    HttpStatus status = e instanceof IllegalArgumentException
        ? HttpStatus.NOT_FOUND
        : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(Map.of("error", msg));
  }

  private void broadcast(String roomId, GameSnapshot snap) {
    gameEventBus.publishSnapshot(roomId, snap);
    if (GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      roomService.getRoom(roomId).ifPresent(room -> roomService.settleIfFinished(room, snap));
    }
  }
}
