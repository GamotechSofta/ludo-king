package com.ludo.backend.game;

import com.ludo.backend.bot.BotService;
import com.ludo.backend.room.BotDifficulty;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
  private final BotService botService;
  private final SimpMessagingTemplate messagingTemplate;
  private final ExecutorService botExecutor = Executors.newCachedThreadPool();

  public GameHttpController(
      GameEngineService gameEngineService,
      RoomService roomService,
      BotService botService,
      SimpMessagingTemplate messagingTemplate
  ) {
    this.gameEngineService = gameEngineService;
    this.roomService = roomService;
    this.botService = botService;
    this.messagingTemplate = messagingTemplate;
  }

  public record ActionRequest(String userId, Integer tokenIndex, Integer diceIndex) {
  }

  @PostMapping("/roll")
  public GameSnapshot roll(
      @PathVariable String roomId,
      @RequestBody ActionRequest req
  ) {
    ensureMatch(roomId);
    GameSnapshot snap = gameEngineService.rollDice(roomId, req.userId());
    broadcast(roomId, snap);
    maybeScheduleBot(roomId);
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
    maybeScheduleBot(roomId);
    return snap;
  }

  private void ensureMatch(String roomId) {
    if (gameEngineService.hasMatch(roomId)) {
      return;
    }
    Room room = roomService.getRoom(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    roomService.rehydrateMatch(room);
  }

  private void broadcast(String roomId, GameSnapshot snap) {
    messagingTemplate.convertAndSend("/topic/room/" + roomId, snap);
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
          if (room != null && snap.getCurrentSeatIndex() < room.getPlayers().size()) {
            RoomPlayer p = room.getPlayers().get(snap.getCurrentSeatIndex());
            if (p.getBotDifficulty() != null) {
              diff = p.getBotDifficulty();
            }
          }
          snap = botService.takeTurnIfBot(roomId, diff);
          messagingTemplate.convertAndSend("/topic/room/" + roomId, snap);
        }
      } catch (Exception e) {
        messagingTemplate.convertAndSend(
            "/topic/room/" + roomId + "/errors",
            Map.of("error", e.getMessage() == null ? "bot error" : e.getMessage())
        );
      }
    });
  }
}
