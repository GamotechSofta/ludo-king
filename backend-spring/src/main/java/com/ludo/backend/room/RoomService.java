package com.ludo.backend.room;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameEngineService.SeatInfo;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.platform.wallet.MatchEconomyService;
import com.ludo.backend.platform.wallet.WalletProperties;
import com.ludo.backend.realtime.MatchmakingEventPublisher;
import com.ludo.backend.realtime.RedisMatchQueue;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Online matchmaking + room lifecycle only.
 * Does not alter game rules / board / dice / pawn movement.
 */
@Service
public class RoomService {

  /** Max wait to fill seats before existing bot-fill kicks in. */
  private static final int FILL_SECONDS = 15;
  private static final int COUNTDOWN_SECONDS = 3;
  private static final int RECONNECT_SECONDS = 30;
  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final RoomRepository roomRepository;
  private final GameEngineService gameEngineService;
  private final RedisMatchQueue redisMatchQueue;
  private final MatchEconomyService matchEconomy;
  private final MatchmakingEventPublisher events;
  private final SecureRandom random = new SecureRandom();

  private final ConcurrentHashMap<String, ConcurrentLinkedQueue<QueueEntry>> queues =
      new ConcurrentHashMap<>();

  public RoomService(
      RoomRepository roomRepository,
      GameEngineService gameEngineService,
      @Autowired(required = false) RedisMatchQueue redisMatchQueue,
      MatchEconomyService matchEconomy,
      MatchmakingEventPublisher events
  ) {
    this.roomRepository = roomRepository;
    this.gameEngineService = gameEngineService;
    this.redisMatchQueue = redisMatchQueue;
    this.matchEconomy = matchEconomy;
    this.events = events;
  }

  public record QueueEntry(String userId, String username, Instant enqueuedAt) {
  }

  public record QueueResponse(String status, String roomId, String roomCode, Room room) {
  }

  public QueueResponse enqueue(String userId, String username, int maxPlayers, String stakeTier) {
    if (maxPlayers < 2 || maxPlayers > 4) {
      throw new IllegalArgumentException("maxPlayers must be 2-4");
    }
    String tier = stakeTier == null || stakeTier.isBlank() ? "FREE" : stakeTier.trim().toUpperCase();
    double bet = WalletProperties.parseStakeAmount(tier, matchEconomy.entryFee());
    if (matchEconomy.isLive() && bet <= 0 && !"FREE".equals(tier) && !"PRIVATE".equals(tier)) {
      bet = matchEconomy.entryFee();
      tier = WalletProperties.stakeTierForBet(bet);
    }
    if (matchEconomy.isLive() && bet <= 0 && tier.startsWith("BET_")) {
      throw new IllegalArgumentException("Invalid bet amount");
    }

    // One player → one room (ignore duplicate JOIN_QUEUE)
    Optional<Room> existing = findActiveRoomForUser(userId);
    if (existing.isPresent()) {
      Room room = existing.get();
      return new QueueResponse(
          room.getStatus() == RoomStatus.WAITING ? "WAITING" : "MATCHED",
          room.getId(),
          room.getRoomCode(),
          room
      );
    }

    removeFromAllQueues(userId);

    events.toUser(userId, MatchmakingEventPublisher.EVENT_JOIN_QUEUE, Map.of(
        "userId", userId,
        "username", username,
        "gameMode", maxPlayers,
        "stakeTier", tier,
        "joinedAt", Instant.now().toString()
    ));

    List<Room> waiting = roomRepository
        .findByStatusAndMaxPlayersAndStakeTier(RoomStatus.WAITING, maxPlayers, tier)
        .stream()
        .filter(r -> r.getPlayers().size() < r.getMaxPlayers())
        .filter(r -> r.getPlayers().stream().noneMatch(p -> userId.equals(p.getUserId())))
        .toList();

    if (!waiting.isEmpty()) {
      Room room = waiting.get(0);
      List<LudoColor> colors = LudoColor.forPlayerCount(maxPlayers);
      int seat = room.getPlayers().size();
      RoomPlayer joined = new RoomPlayer(userId, username, colors.get(seat).name(), false, seat);
      room.getPlayers().add(joined);
      room = roomRepository.save(room);
      try {
        double fee = room.getEntryFee() > 0 ? room.getEntryFee() : bet;
        matchEconomy.reserveEntry(room.getId(), userId, fee);
      } catch (RuntimeException e) {
        room.getPlayers().removeIf(p -> userId.equals(p.getUserId()));
        roomRepository.save(room);
        throw e;
      }
      broadcastPlayerJoined(room, joined);
      if (room.getPlayers().size() == room.getMaxPlayers()) {
        room = enterReadyPhase(room);
        return new QueueResponse("MATCHED", room.getId(), room.getRoomCode(), room);
      }
      return new QueueResponse("WAITING", room.getId(), room.getRoomCode(), room);
    }

    Room room = newEmptyRoom(maxPlayers, tier);
    if (bet > 0) {
      room.setEntryFee(Math.round(bet));
    }
    List<LudoColor> colors = LudoColor.forPlayerCount(maxPlayers);
    RoomPlayer host = new RoomPlayer(userId, username, colors.get(0).name(), false, 0);
    room.getPlayers().add(host);
    room.setFillDeadlineAt(Instant.now().plus(FILL_SECONDS, ChronoUnit.SECONDS));
    room = roomRepository.save(room);
    try {
      matchEconomy.reserveEntry(room.getId(), userId, bet);
    } catch (RuntimeException e) {
      roomRepository.delete(room);
      throw e;
    }
    if (redisMatchQueue != null) {
      redisMatchQueue.enqueue(maxPlayers, tier, userId, username);
    }
    events.toUser(userId, MatchmakingEventPublisher.EVENT_ROOM_CREATED, roomPayload(room));
    events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_ROOM_CREATED, roomPayload(room));
    broadcastPlayerJoined(room, host);
    return new QueueResponse("WAITING", room.getId(), room.getRoomCode(), room);
  }

  public void cancelQueue(String userId) {
    removeFromAllQueues(userId);
    if (redisMatchQueue != null) {
      redisMatchQueue.removeFromAll(userId);
    }
    events.toUser(userId, MatchmakingEventPublisher.EVENT_LEAVE_QUEUE, Map.of("userId", userId));
  }

  public void leaveRoom(String roomId, String userId) {
    removeFromAllQueues(userId);
    if (redisMatchQueue != null) {
      redisMatchQueue.removeFromAll(userId);
    }
    Room room = roomRepository.findById(roomId).orElse(null);
    if (room == null) {
      return;
    }

    // After game starts → mark disconnected (seat reserved)
    if (room.getStatus() == RoomStatus.IN_PROGRESS
        || room.getStatus() == RoomStatus.WAITING_RECONNECT) {
      markDisconnected(roomId, userId);
      return;
    }

    if (room.getStatus() != RoomStatus.WAITING && room.getStatus() != RoomStatus.READY) {
      return;
    }
    boolean removed = room.getPlayers().removeIf(p -> userId.equals(p.getUserId()));
    if (!removed) {
      return;
    }
    matchEconomy.refundEntry(roomId, userId);
    events.toRoom(roomId, MatchmakingEventPublisher.EVENT_PLAYER_LEFT, Map.of(
        "userId", userId,
        "playersJoined", room.getPlayers().size(),
        "maxPlayers", room.getMaxPlayers()
    ));
    boolean onlyBotsLeft = room.getPlayers().stream().allMatch(RoomPlayer::isBot);
    if (room.getPlayers().isEmpty() || onlyBotsLeft) {
      matchEconomy.refundAllHumans(room);
      roomRepository.delete(room);
      events.toRoom(roomId, MatchmakingEventPublisher.EVENT_ROOM_CLOSED, Map.of("roomId", roomId));
      return;
    }
    // Back to WAITING if someone left during READY
    room.setStatus(RoomStatus.WAITING);
    room.setCountdownEndsAt(null);
    room.setCountdownValue(null);
    room.getPlayers().forEach(p -> {
      if (!p.isBot()) {
        p.setReady(false);
      }
    });
    List<LudoColor> colors = LudoColor.forPlayerCount(room.getMaxPlayers());
    for (int i = 0; i < room.getPlayers().size(); i++) {
      RoomPlayer p = room.getPlayers().get(i);
      p.setSeatIndex(i);
      p.setColor(colors.get(i).name());
    }
    if (room.getFillDeadlineAt() == null) {
      room.setFillDeadlineAt(Instant.now().plus(FILL_SECONDS, ChronoUnit.SECONDS));
    }
    roomRepository.save(room);
  }

  public Room createPrivateRoom(String userId, String username, int maxPlayers) {
    Optional<Room> existing = findActiveRoomForUser(userId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Room room = newEmptyRoom(maxPlayers, "PRIVATE");
    List<LudoColor> colors = LudoColor.forPlayerCount(maxPlayers);
    room.getPlayers().add(new RoomPlayer(userId, username, colors.get(0).name(), false, 0));
    room.setFillDeadlineAt(Instant.now().plus(FILL_SECONDS, ChronoUnit.SECONDS));
    room = roomRepository.save(room);
    try {
      matchEconomy.reserveEntry(room.getId(), userId);
    } catch (RuntimeException e) {
      roomRepository.delete(room);
      throw e;
    }
    events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_ROOM_CREATED, roomPayload(room));
    return room;
  }

  public Room joinByCode(String roomCode, String userId, String username) {
    Optional<Room> existing = findActiveRoomForUser(userId);
    if (existing.isPresent()) {
      return existing.get();
    }
    Room room = roomRepository.findByRoomCode(normalizeCode(roomCode))
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getStatus() != RoomStatus.WAITING) {
      throw new IllegalStateException("Room not joinable");
    }
    if (room.getPlayers().stream().anyMatch(p -> userId.equals(p.getUserId()))) {
      return room;
    }
    if (room.getPlayers().size() >= room.getMaxPlayers()) {
      throw new IllegalStateException("Room full");
    }

    List<LudoColor> colors = LudoColor.forPlayerCount(room.getMaxPlayers());
    int seat = room.getPlayers().size();
    RoomPlayer joined = new RoomPlayer(userId, username, colors.get(seat).name(), false, seat);
    room.getPlayers().add(joined);
    if (room.getFillDeadlineAt() == null) {
      room.setFillDeadlineAt(Instant.now().plus(FILL_SECONDS, ChronoUnit.SECONDS));
    }
    room = roomRepository.save(room);
    try {
      matchEconomy.reserveEntry(room.getId(), userId);
    } catch (RuntimeException e) {
      room.getPlayers().removeIf(p -> userId.equals(p.getUserId()));
      roomRepository.save(room);
      throw e;
    }
    broadcastPlayerJoined(room, joined);
    if (room.getPlayers().size() == room.getMaxPlayers()) {
      room = enterReadyPhase(room);
    }
    return room;
  }

  /** Human presses READY while room status is READY. */
  public Room markReady(String roomId, String userId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if (room.getStatus() != RoomStatus.READY) {
      throw new IllegalStateException("Room is not accepting ready");
    }
    RoomPlayer player = room.getPlayers().stream()
        .filter(p -> userId.equals(p.getUserId()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not in room"));
    if (player.isBot()) {
      return room;
    }
    player.setReady(true);
    room = roomRepository.save(room);
    events.toRoom(roomId, MatchmakingEventPublisher.EVENT_PLAYER_READY, Map.of(
        "userId", userId,
        "readyCount", readyCount(room),
        "maxPlayers", room.getMaxPlayers(),
        "room", room
    ));
    if (allReady(room)) {
      room = beginCountdown(room);
    }
    return room;
  }

  public Optional<Room> getRoom(String id) {
    return roomRepository.findById(id);
  }

  public Map<String, Object> getRoomState(String id) {
    Room room = roomRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    if ((room.getStatus() == RoomStatus.IN_PROGRESS
            || room.getStatus() == RoomStatus.WAITING_RECONNECT)
        && !gameEngineService.hasMatch(id)) {
      rehydrateMatch(room);
    }
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("room", room);
    body.put("displayStatus", displayStatus(room.getStatus()));
    body.put("playersJoined", room.getPlayers().size());
    body.put("maxPlayers", room.getMaxPlayers());
    body.put("readyCount", readyCount(room));
    body.put("allReady", allReady(room));
    if (room.getCountdownValue() != null) {
      body.put("countdown", room.getCountdownValue());
    }
    if (gameEngineService.hasMatch(id)) {
      body.put("game", gameEngineService.getSnapshot(id));
    }
    return body;
  }

  public GameSnapshot rehydrateMatch(Room room) {
    List<SeatInfo> seats = new ArrayList<>();
    for (RoomPlayer p : room.getPlayers()) {
      seats.add(new SeatInfo(
          p.getUserId(),
          p.getUsername(),
          LudoColor.valueOf(p.getColor()),
          p.isBot()
      ));
    }
    return gameEngineService.createMatch(room.getId(), seats);
  }

  /** Existing bot-fill feature: pad seats then enter READY (not instant play). */
  public Room fillBotsAndStart(Room room) {
    if (room.getStatus() != RoomStatus.WAITING) {
      return room;
    }
    List<LudoColor> colors = LudoColor.forPlayerCount(room.getMaxPlayers());
    while (room.getPlayers().size() < room.getMaxPlayers()) {
      int seat = room.getPlayers().size();
      List<String> takenNames = room.getPlayers().stream()
          .map(RoomPlayer::getUsername)
          .toList();
      RoomPlayer bot = new RoomPlayer(
          "bot-" + room.getId() + "-" + seat,
          BotNames.randomName(takenNames),
          colors.get(seat).name(),
          true,
          seat
      );
      bot.setBotDifficulty(BotDifficulty.MEDIUM);
      bot.setReady(true);
      room.getPlayers().add(bot);
      broadcastPlayerJoined(room, bot);
    }
    room = roomRepository.save(room);
    return enterReadyPhase(room);
  }

  public Room startMatch(Room room) {
    if (room.getStatus() == RoomStatus.IN_PROGRESS) {
      return room;
    }
    try {
      shuffleHumanColors(room);
      List<SeatInfo> seats = new ArrayList<>();
      for (RoomPlayer p : room.getPlayers()) {
        seats.add(new SeatInfo(
            p.getUserId(),
            p.getUsername(),
            LudoColor.valueOf(p.getColor()),
            p.isBot()
        ));
      }
      // Engine picks starting seat; seats list order = seat index
      gameEngineService.createMatch(room.getId(), seats);
      room.setStatus(RoomStatus.IN_PROGRESS);
      room.setStartedAt(Instant.now());
      room.setCountdownEndsAt(null);
      room.setCountdownValue(null);
      room.setFillDeadlineAt(null);
      room = roomRepository.save(room);
      matchEconomy.markPlaying(room.getId());
      Map<String, Object> payload = roomPayload(room);
      payload.put("game", gameEngineService.getSnapshot(room.getId()));
      events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_GAME_STARTED, payload);
      return room;
    } catch (RuntimeException e) {
      matchEconomy.refundAllHumans(room);
      throw e;
    }
  }

  public void settleIfFinished(Room room, GameSnapshot snap) {
    if (room.getStatus() == RoomStatus.COMPLETED) {
      return;
    }
    if (!GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return;
    }
    room.setStatus(RoomStatus.COMPLETED);
    room.setEndedAt(Instant.now());
    roomRepository.save(room);
    matchEconomy.settleMatch(room, snap);
  }

  public void processExpiredFills() {
    List<Room> expired = roomRepository.findByStatusAndFillDeadlineAtBefore(
        RoomStatus.WAITING, Instant.now());
    for (Room room : expired) {
      // Existing bot-fill feature retained
      fillBotsAndStart(room);
    }
  }

  public void processCountdowns() {
    Instant now = Instant.now();
    List<Room> expired = roomRepository.findByStatusAndCountdownEndsAtBefore(
        RoomStatus.READY, now);
    for (Room room : expired) {
      if (room.getCountdownEndsAt() != null) {
        startMatch(room);
      }
    }

    for (Room room : roomRepository.findByStatus(RoomStatus.READY)) {
      if (room.getCountdownEndsAt() == null) {
        continue;
      }
      long secsLeft = ChronoUnit.SECONDS.between(now, room.getCountdownEndsAt());
      int value = (int) Math.max(0, secsLeft);
      if (room.getCountdownValue() == null || room.getCountdownValue() != value) {
        room.setCountdownValue(value);
        roomRepository.save(room);
        Object label = value <= 0 ? "GO" : value;
        events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_COUNTDOWN, Map.of(
            "countdown", label,
            "secondsLeft", value,
            "roomId", room.getId()
        ));
      }
    }
  }

  public void processReconnectTimeouts() {
    List<Room> expired = roomRepository.findByStatusAndReconnectDeadlineAtBefore(
        RoomStatus.WAITING_RECONNECT, Instant.now());
    for (Room room : expired) {
      for (RoomPlayer p : room.getPlayers()) {
        if (p.getConnectionStatus() == ConnectionStatus.DISCONNECTED && !p.isBot()) {
          events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_PLAYER_LEFT, Map.of(
              "userId", p.getUserId(),
              "reason", "reconnect_timeout"
          ));
          p.setConnectionStatus(ConnectionStatus.DISCONNECTED);
        }
      }
      // Resume play with remaining; keep seat as disconnected bot-like AFK
      room.setStatus(RoomStatus.IN_PROGRESS);
      room.setReconnectDeadlineAt(null);
      roomRepository.save(room);
    }
  }

  public void processQueues() {
    for (String key : new ArrayList<>(queues.keySet())) {
      String[] parts = key.split("\\|", 2);
      int max = Integer.parseInt(parts[0]);
      String tier = parts.length > 1 ? parts[1] : "FREE";
      tryFormRoom(key, max, tier);
    }
  }

  public Room markDisconnected(String roomId, String userId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    room.getPlayers().stream()
        .filter(p -> userId.equals(p.getUserId()))
        .findFirst()
        .ifPresent(p -> {
          p.setConnectionStatus(ConnectionStatus.DISCONNECTED);
          p.setDisconnectedAt(Instant.now());
        });
    if (room.getStatus() == RoomStatus.IN_PROGRESS) {
      room.setStatus(RoomStatus.WAITING_RECONNECT);
      room.setReconnectDeadlineAt(Instant.now().plus(RECONNECT_SECONDS, ChronoUnit.SECONDS));
    }
    room = roomRepository.save(room);
    events.toRoom(roomId, MatchmakingEventPublisher.EVENT_PLAYER_DISCONNECTED, Map.of(
        "userId", userId,
        "reconnectSeconds", RECONNECT_SECONDS,
        "room", room
    ));
    events.toRoom(roomId, MatchmakingEventPublisher.EVENT_WAITING_RECONNECT, Map.of(
        "userId", userId,
        "deadline", room.getReconnectDeadlineAt() != null
            ? room.getReconnectDeadlineAt().toString()
            : ""
    ));
    return room;
  }

  public Room reconnect(String roomId, String userId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    room.getPlayers().stream()
        .filter(p -> userId.equals(p.getUserId()))
        .findFirst()
        .ifPresent(p -> {
          p.setConnectionStatus(ConnectionStatus.CONNECTED);
          p.setDisconnectedAt(null);
        });
    boolean anyDisconnected = room.getPlayers().stream()
        .anyMatch(p -> !p.isBot() && p.getConnectionStatus() == ConnectionStatus.DISCONNECTED);
    if (!anyDisconnected && room.getStatus() == RoomStatus.WAITING_RECONNECT) {
      room.setStatus(RoomStatus.IN_PROGRESS);
      room.setReconnectDeadlineAt(null);
    }
    room = roomRepository.save(room);
    events.toRoom(roomId, MatchmakingEventPublisher.EVENT_PLAYER_RECONNECTED, Map.of(
        "userId", userId,
        "room", room
    ));
    return room;
  }

  private Room enterReadyPhase(Room room) {
    room.setStatus(RoomStatus.READY);
    room.setFillDeadlineAt(null);
    // Bots are auto-ready; humans must press READY
    for (RoomPlayer p : room.getPlayers()) {
      if (p.isBot()) {
        p.setReady(true);
      } else {
        p.setReady(false);
      }
    }
    room = roomRepository.save(room);
    events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_MATCH_FOUND, roomPayload(room));
    for (RoomPlayer p : room.getPlayers()) {
      if (!p.isBot()) {
        events.toUser(p.getUserId(), MatchmakingEventPublisher.EVENT_MATCH_FOUND, roomPayload(room));
      }
    }
    // If only bots somehow (shouldn't), or all already ready
    if (allReady(room)) {
      room = beginCountdown(room);
    }
    return room;
  }

  private Room beginCountdown(Room room) {
    room.setCountdownEndsAt(Instant.now().plus(COUNTDOWN_SECONDS, ChronoUnit.SECONDS));
    room.setCountdownValue(COUNTDOWN_SECONDS);
    room = roomRepository.save(room);
    events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_COUNTDOWN, Map.of(
        "countdown", COUNTDOWN_SECONDS,
        "secondsLeft", COUNTDOWN_SECONDS,
        "roomId", room.getId()
    ));
    return room;
  }

  private void shuffleHumanColors(Room room) {
    List<String> palette = new ArrayList<>(
        LudoColor.forPlayerCount(room.getMaxPlayers()).stream().map(Enum::name).toList()
    );
    Collections.shuffle(palette, random);
    for (int i = 0; i < room.getPlayers().size() && i < palette.size(); i++) {
      room.getPlayers().get(i).setColor(palette.get(i));
    }
  }

  private boolean allReady(Room room) {
    return room.getPlayers().stream().allMatch(p -> p.isBot() || p.isReady());
  }

  private long readyCount(Room room) {
    return room.getPlayers().stream().filter(p -> p.isBot() || p.isReady()).count();
  }

  private void broadcastPlayerJoined(Room room, RoomPlayer player) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("userId", player.getUserId());
    payload.put("username", player.getUsername());
    payload.put("seatIndex", player.getSeatIndex());
    payload.put("playersJoined", room.getPlayers().size());
    payload.put("maxPlayers", room.getMaxPlayers());
    payload.put("room", room);
    events.toRoom(room.getId(), MatchmakingEventPublisher.EVENT_PLAYER_JOINED, payload);
  }

  private Map<String, Object> roomPayload(Room room) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("roomId", room.getId());
    payload.put("roomCode", room.getRoomCode());
    payload.put("status", room.getStatus().name());
    payload.put("displayStatus", displayStatus(room.getStatus()));
    payload.put("playersJoined", room.getPlayers().size());
    payload.put("maxPlayers", room.getMaxPlayers());
    payload.put("gameMode", room.getMaxPlayers());
    payload.put("room", room);
    return payload;
  }

  private static String displayStatus(RoomStatus status) {
    return switch (status) {
      case IN_PROGRESS -> "PLAYING";
      case COMPLETED -> "FINISHED";
      default -> status.name();
    };
  }

  private Optional<Room> findActiveRoomForUser(String userId) {
    List<Room> rooms = roomRepository.findActiveRoomsForUser(userId);
    if (rooms == null || rooms.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(rooms.get(0));
  }

  private Room tryFormRoom(String key, int maxPlayers, String tier) {
    ConcurrentLinkedQueue<QueueEntry> q = queues.get(key);
    if (q == null || q.size() < maxPlayers) {
      return null;
    }
    List<QueueEntry> taken = new ArrayList<>();
    for (int i = 0; i < maxPlayers; i++) {
      QueueEntry e = q.poll();
      if (e == null) {
        taken.forEach(q::offer);
        return null;
      }
      taken.add(e);
    }

    Room room = newEmptyRoom(maxPlayers, tier);
    List<LudoColor> colors = LudoColor.forPlayerCount(maxPlayers);
    for (int i = 0; i < taken.size(); i++) {
      QueueEntry e = taken.get(i);
      room.getPlayers().add(new RoomPlayer(e.userId(), e.username(), colors.get(i).name(), false, i));
    }
    room = roomRepository.save(room);
    return enterReadyPhase(room);
  }

  private Room newEmptyRoom(int maxPlayers, String tier) {
    Room room = new Room();
    room.setRoomCode(generateCode());
    room.setMaxPlayers(maxPlayers);
    room.setStakeTier(tier);
    room.setStatus(RoomStatus.WAITING);
    room.setCreatedAt(Instant.now());
    return room;
  }

  private void removeFromAllQueues(String userId) {
    queues.values().forEach(q -> q.removeIf(e -> e.userId().equals(userId)));
  }

  private String generateCode() {
    StringBuilder sb = new StringBuilder("ROOM-");
    for (int i = 0; i < 6; i++) {
      sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
    }
    return sb.toString();
  }

  private static String normalizeCode(String roomCode) {
    String code = roomCode.trim().toUpperCase();
    if (!code.startsWith("ROOM-") && code.length() == 6) {
      return "ROOM-" + code;
    }
    return code;
  }
}
