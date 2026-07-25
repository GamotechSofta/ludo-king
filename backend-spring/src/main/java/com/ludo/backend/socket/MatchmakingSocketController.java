package com.ludo.backend.socket;

import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomService;
import java.util.Map;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

/**
 * Matchmaking / lobby STOMP endpoints only — no game roll/move here.
 */
@Controller
public class MatchmakingSocketController {

  private final RoomService roomService;

  public MatchmakingSocketController(RoomService roomService) {
    this.roomService = roomService;
  }

  public record QueuePayload(
      String userId,
      String username,
      Integer maxPlayers,
      String stakeTier,
      String socketId
  ) {
  }

  public record UserPayload(String userId) {
  }

  @MessageMapping("/matchmaking/join")
  public void joinQueue(@Payload QueuePayload payload) {
    roomService.enqueue(
        payload.userId(),
        payload.username() == null ? "Player" : payload.username(),
        payload.maxPlayers() == null ? 4 : payload.maxPlayers(),
        payload.stakeTier()
    );
  }

  @MessageMapping("/matchmaking/leave")
  public void leaveQueue(@Payload UserPayload payload) {
    roomService.cancelQueue(payload.userId());
  }

  @MessageMapping("/room/{roomId}/ready")
  public void ready(@DestinationVariable String roomId, @Payload UserPayload payload) {
    roomService.markReady(roomId, payload.userId());
  }

  @MessageMapping("/room/{roomId}/leave")
  public void leaveRoom(@DestinationVariable String roomId, @Payload UserPayload payload) {
    roomService.leaveRoom(roomId, payload.userId());
  }

  @MessageMapping("/room/{roomId}/disconnect")
  public Room disconnect(@DestinationVariable String roomId, @Payload UserPayload payload) {
    return roomService.markDisconnected(roomId, payload.userId());
  }

  @MessageMapping("/room/{roomId}/reconnect")
  public Room reconnect(@DestinationVariable String roomId, @Payload UserPayload payload) {
    return roomService.reconnect(roomId, payload.userId());
  }
}
