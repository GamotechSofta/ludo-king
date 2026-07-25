package com.ludo.backend.room;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameEngineService.SeatInfo;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.platform.wallet.MatchEconomyService;
import com.ludo.backend.realtime.RedisMatchQueue;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RoomService {

  private static final int FILL_SECONDS = 18;
  private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

  private final RoomRepository roomRepository;
  private final GameEngineService gameEngineService;
  private final RedisMatchQueue redisMatchQueue;
  private final MatchEconomyService matchEconomy;
  private final SecureRandom random = new SecureRandom();

  private final ConcurrentHashMap<String, ConcurrentLinkedQueue<QueueEntry>> queues =
      new ConcurrentHashMap<>();

  public RoomService(
      RoomRepository roomRepository,
      GameEngineService gameEngineService,
      @Autowired(required = false) RedisMatchQueue redisMatchQueue,
      MatchEconomyService matchEconomy
  ) {
    this.roomRepository = roomRepository;
    this.gameEngineService = gameEngineService;
    this.redisMatchQueue = redisMatchQueue;
    this.matchEconomy = matchEconomy;
  }

  public record QueueEntry(String userId, String username, Instant enqueuedAt) {
  }

  public record QueueResponse(String status, String roomId, String roomCode, Room room) {
  }

  public QueueResponse enqueue(String userId, String username, int maxPlayers, String stakeTier) {
    if (maxPlayers < 2 || maxPlayers > 4) {
      throw new IllegalArgumentException("maxPlayers must be 2-4");
    }
    String tier = stakeTier == null || stakeTier.isBlank() ? "FREE" : stakeTier;
    String key = maxPlayers + "|" + tier;

    removeFromAllQueues(userId);

    // Prefer joining an existing WAITING room of same size/tier
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
      room.getPlayers().add(new RoomPlayer(userId, username, colors.get(seat).name(), false, seat));
      room = roomRepository.save(room);
      try {
        matchEconomy.reserveEntry(room.getId(), userId);
      } catch (RuntimeException e) {
        room.getPlayers().removeIf(p -> userId.equals(p.getUserId()));
        roomRepository.save(room);
        throw e;
      }
      if (room.getPlayers().size() == room.getMaxPlayers()) {
        room = startMatch(room);
      }
      return new QueueResponse("MATCHED", room.getId(), room.getRoomCode(), room);
    }

    // Create new waiting room for this player (bot-fill timer starts)
    Room room = newEmptyRoom(maxPlayers, tier);
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
    if (redisMatchQueue != null) {
      redisMatchQueue.enqueue(maxPlayers, tier, userId, username);
    }
    return new QueueResponse("WAITING", room.getId(), room.getRoomCode(), room);
  }

  public void cancelQueue(String userId) {
    removeFromAllQueues(userId);
    if (redisMatchQueue != null) {
      redisMatchQueue.removeFromAll(userId);
    }
  }

  /**
   * Player backs out of a WAITING room (e.g. leaves the lobby screen).
   * Deletes the room when it empties so it can't bot-fill into a ghost match.
   */
  public void leaveRoom(String roomId, String userId) {
    removeFromAllQueues(userId);
    if (redisMatchQueue != null) {
      redisMatchQueue.removeFromAll(userId);
    }
    Room room = roomRepository.findById(roomId).orElse(null);
    if (room == null || room.getStatus() != RoomStatus.WAITING) {
      return;
    }
    boolean removed = room.getPlayers().removeIf(p -> userId.equals(p.getUserId()));
    if (!removed) {
      return;
    }
    matchEconomy.refundEntry(roomId, userId);
    boolean onlyBotsLeft = room.getPlayers().stream().allMatch(RoomPlayer::isBot);
    if (room.getPlayers().isEmpty() || onlyBotsLeft) {
      matchEconomy.refundAllHumans(room);
      roomRepository.delete(room);
      return;
    }
    List<LudoColor> colors = LudoColor.forPlayerCount(room.getMaxPlayers());
    for (int i = 0; i < room.getPlayers().size(); i++) {
      RoomPlayer p = room.getPlayers().get(i);
      p.setSeatIndex(i);
      p.setColor(colors.get(i).name());
    }
    roomRepository.save(room);
  }

  public Room createPrivateRoom(String userId, String username, int maxPlayers) {
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
    return room;
  }

  public Room joinByCode(String roomCode, String userId, String username) {
    Room room = roomRepository.findByRoomCode(roomCode.toUpperCase())
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
    room.getPlayers().add(new RoomPlayer(userId, username, colors.get(seat).name(), false, seat));
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
    if (room.getPlayers().size() == room.getMaxPlayers()) {
      room = startMatch(room);
    }
    return room;
  }

  public Optional<Room> getRoom(String id) {
    return roomRepository.findById(id);
  }

  public Map<String, Object> getRoomState(String id) {
    Room room = roomRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    // In-memory engine can be empty after deploy/restart while Mongo still says IN_PROGRESS
    if (room.getStatus() == RoomStatus.IN_PROGRESS && !gameEngineService.hasMatch(id)) {
      rehydrateMatch(room);
    }
    Map<String, Object> body = new java.util.LinkedHashMap<>();
    body.put("room", room);
    if (gameEngineService.hasMatch(id)) {
      body.put("game", gameEngineService.getSnapshot(id));
    }
    return body;
  }

  /** Recreate engine state for an in-progress room (fresh board after JVM restart). */
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
      room.getPlayers().add(bot);
    }
    room = roomRepository.save(room);
    return startMatch(room);
  }

  public Room startMatch(Room room) {
    if (room.getStatus() == RoomStatus.IN_PROGRESS) {
      return room;
    }
    try {
      if (matchEconomy.isLive()) {
        room.setEntryFee(Math.round(matchEconomy.entryFee()));
      }
      List<SeatInfo> seats = new ArrayList<>();
      for (RoomPlayer p : room.getPlayers()) {
        seats.add(new SeatInfo(
            p.getUserId(),
            p.getUsername(),
            LudoColor.valueOf(p.getColor()),
            p.isBot()
        ));
      }
      gameEngineService.createMatch(room.getId(), seats);
      room.setStatus(RoomStatus.IN_PROGRESS);
      room.setStartedAt(Instant.now());
      room = roomRepository.save(room);
      matchEconomy.markPlaying(room.getId());
      return room;
    } catch (RuntimeException e) {
      matchEconomy.refundAllHumans(room);
      throw e;
    }
  }

  /** Settle wallet when match finishes (idempotent). */
  public void settleIfFinished(Room room, GameSnapshot snap) {
    if (room.getStatus() == RoomStatus.COMPLETED) {
      return;
    }
    if (!GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return;
    }
    room.setStatus(RoomStatus.COMPLETED);
    roomRepository.save(room);
    matchEconomy.settleMatch(room, snap);
  }

  public void processExpiredFills() {
    List<Room> expired = roomRepository.findByStatusAndFillDeadlineAtBefore(
        RoomStatus.WAITING, Instant.now());
    for (Room room : expired) {
      fillBotsAndStart(room);
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
        .ifPresent(p -> p.setConnectionStatus(ConnectionStatus.DISCONNECTED));
    return roomRepository.save(room);
  }

  public Room reconnect(String roomId, String userId) {
    Room room = roomRepository.findById(roomId)
        .orElseThrow(() -> new IllegalArgumentException("Room not found"));
    room.getPlayers().stream()
        .filter(p -> userId.equals(p.getUserId()))
        .findFirst()
        .ifPresent(p -> p.setConnectionStatus(ConnectionStatus.CONNECTED));
    return roomRepository.save(room);
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
    return startMatch(room);
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
    StringBuilder sb = new StringBuilder(6);
    for (int i = 0; i < 6; i++) {
      sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
    }
    return sb.toString();
  }
}
