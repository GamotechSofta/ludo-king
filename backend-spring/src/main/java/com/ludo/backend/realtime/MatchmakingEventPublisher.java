package com.ludo.backend.realtime;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Lobby / matchmaking WebSocket fan-out only.
 * Does not touch game engine, board, or dice.
 */
@Service
public class MatchmakingEventPublisher {

  public static final String EVENT_JOIN_QUEUE = "JOIN_QUEUE";
  public static final String EVENT_LEAVE_QUEUE = "LEAVE_QUEUE";
  public static final String EVENT_MATCH_FOUND = "MATCH_FOUND";
  public static final String EVENT_ROOM_CREATED = "ROOM_CREATED";
  public static final String EVENT_PLAYER_JOINED = "PLAYER_JOINED";
  public static final String EVENT_PLAYER_LEFT = "PLAYER_LEFT";
  public static final String EVENT_PLAYER_READY = "PLAYER_READY";
  public static final String EVENT_COUNTDOWN = "COUNTDOWN";
  public static final String EVENT_GAME_STARTED = "GAME_STARTED";
  public static final String EVENT_PLAYER_DISCONNECTED = "PLAYER_DISCONNECTED";
  public static final String EVENT_PLAYER_RECONNECTED = "PLAYER_RECONNECTED";
  public static final String EVENT_ROOM_CLOSED = "ROOM_CLOSED";
  public static final String EVENT_WAITING_RECONNECT = "WAITING_RECONNECT";

  private final SimpMessagingTemplate messaging;

  public MatchmakingEventPublisher(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  public void toUser(String userId, String type, Map<String, Object> payload) {
    Map<String, Object> body = envelope(type, payload);
    messaging.convertAndSend("/topic/matchmaking/user/" + userId, body);
  }

  public void toRoom(String roomId, String type, Map<String, Object> payload) {
    Map<String, Object> body = envelope(type, payload);
    messaging.convertAndSend("/topic/room/" + roomId + "/lobby", body);
  }

  private static Map<String, Object> envelope(String type, Map<String, Object> payload) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", type);
    body.put("ts", System.currentTimeMillis());
    if (payload != null) {
      body.putAll(payload);
    }
    return body;
  }
}
