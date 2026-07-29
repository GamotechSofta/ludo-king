package com.ludo.backend.bot;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.BotDifficulty;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;

/** One bot runner per room — prevents parallel rolls that force-skip turns. */
@Service
public class BotTurnCoordinator {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final BotService botService;
  private final GameEventBus gameEventBus;
  private final ExecutorService botExecutor = Executors.newCachedThreadPool();
  private final ConcurrentHashMap<String, AtomicBoolean> running =
      new ConcurrentHashMap<>();
  /** A schedule() that arrived while the room was busy — replayed, never dropped. */
  private final ConcurrentHashMap<String, AtomicBoolean> requested =
      new ConcurrentHashMap<>();

  public BotTurnCoordinator(
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

  public void schedule(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      return;
    }
    botExecutor.submit(() -> runBotChain(roomId));
  }

  private void runBotChain(String roomId) {
    AtomicBoolean flag = running.computeIfAbsent(roomId, k -> new AtomicBoolean(false));
    AtomicBoolean want = requested.computeIfAbsent(roomId, k -> new AtomicBoolean(false));
    // Claim the request before the lock so a chain about to finish still sees it.
    want.set(true);

    while (true) {
      if (!flag.compareAndSet(false, true)) {
        return;
      }
      try {
        while (want.compareAndSet(true, false)) {
          driveBots(roomId);
        }
      } finally {
        flag.set(false);
      }
      // Lost a request in the window between the last check and the release.
      if (!want.get()) {
        return;
      }
    }
  }

  private void driveBots(String roomId) {
    try {
      if (!gameEngineService.hasMatch(roomId)) {
        return;
      }
      GameSnapshot snap = gameEngineService.getSnapshot(roomId);
      long lastSeq = -1;
      int stalledRounds = 0;
      while (snap.getIsBot() != null
          && snap.getCurrentSeatIndex() < snap.getIsBot().length
          && snap.getIsBot()[snap.getCurrentSeatIndex()]
          && !GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {

        // A bot seat that cannot progress must not spin the CPU.
        if (snap.getActionSeq() == lastSeq) {
          if (++stalledRounds > 3) {
            break;
          }
        } else {
          stalledRounds = 0;
          lastSeq = snap.getActionSeq();
        }

        BotDifficulty diff = BotDifficulty.HARD;
        Room room = roomService.getRoom(roomId).orElse(null);
        if (room != null && snap.getCurrentSeatIndex() < room.getPlayers().size()) {
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
      if (gameEngineService.hasMatch(roomId)) {
        gameEventBus.publishSnapshot(roomId, gameEngineService.getSnapshot(roomId));
      }
    } catch (Exception ignored) {
      try {
        if (gameEngineService.hasMatch(roomId)) {
          gameEventBus.publishSnapshot(roomId, gameEngineService.getSnapshot(roomId));
        }
      } catch (Exception ignored2) {
        // non-fatal
      }
    }
  }
}
