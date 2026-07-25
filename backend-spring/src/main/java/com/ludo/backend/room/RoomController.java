package com.ludo.backend.room;

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
import org.springframework.web.server.ResponseStatusException;

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

  @PostMapping("/{id}/leave")
  public Map<String, Object> leave(@PathVariable String id, @RequestBody CancelRequest req) {
    roomService.leaveRoom(id, req.userId());
    return Map.of("ok", true);
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, String>> handlePayment(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("error", e.getReason() == null ? "error" : e.getReason()));
  }

  @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
  public ResponseEntity<Map<String, String>> handleBad(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
  }
}
