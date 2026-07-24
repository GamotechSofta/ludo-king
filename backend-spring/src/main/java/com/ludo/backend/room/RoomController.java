package com.ludo.backend.room;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

  private final RoomService roomService;

  public RoomController(RoomService roomService) {
    this.roomService = roomService;
  }

  public record QueueRequest(String userId, String username, Integer maxPlayers, String stakeTier) {
  }

  public record JoinRequest(String userId, String username) {
  }

  public record CancelRequest(String userId) {
  }

  @PostMapping("/queue")
  public RoomService.QueueResponse queue(@RequestBody QueueRequest req) {
    return roomService.enqueue(
        req.userId(),
        req.username(),
        req.maxPlayers() == null ? 4 : req.maxPlayers(),
        req.stakeTier()
    );
  }

  @PostMapping("/private")
  public Room createPrivate(@RequestBody QueueRequest req) {
    return roomService.createPrivateRoom(
        req.userId(),
        req.username(),
        req.maxPlayers() == null ? 4 : req.maxPlayers()
    );
  }

  @PostMapping("/{roomCode}/join")
  public Room join(@PathVariable String roomCode, @RequestBody JoinRequest req) {
    return roomService.joinByCode(roomCode, req.userId(), req.username());
  }

  @GetMapping("/{id}")
  public ResponseEntity<Room> get(@PathVariable String id) {
    return roomService.getRoom(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping("/{id}/state")
  public Map<String, Object> state(@PathVariable String id) {
    return roomService.getRoomState(id);
  }

  @PostMapping("/queue/cancel")
  public Map<String, Object> cancel(@RequestBody CancelRequest req) {
    roomService.cancelQueue(req.userId());
    return Map.of("ok", true);
  }
}
