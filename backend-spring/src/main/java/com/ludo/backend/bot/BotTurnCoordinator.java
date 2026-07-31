package com.ludo.backend.bot;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.BotDifficulty;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomService;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * LudoGame-style bot driver: deadline delays on a bounded scheduler — never
 * {@code Thread.sleep} on an unbounded CachedThreadPool (that exploded under load).
 *
 * <p>One in-flight chain per room; delays free the worker thread so hundreds of
 * rooms share a small pool.
 */
@Service
public class BotTurnCoordinator {

  private static final Logger log = LoggerFactory.getLogger(BotTurnCoordinator.class);

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final BotService botService;
  private final GameEventBus gameEventBus;
  private final ScheduledExecutorService botScheduler;
  private final long rollDelayMs;
  private final long moveDelayMs;

  private final ConcurrentHashMap<String, AtomicBoolean> running = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicBoolean> requested = new ConcurrentHashMap<>();

  public BotTurnCoordinator(
      GameEngineService gameEngineService,
      RoomService roomService,
      BotService botService,
      GameEventBus gameEventBus,
      @Value("${ludo.bot.roll-delay-ms:700}") long rollDelayMs,
      @Value("${ludo.bot.move-delay-ms:850}") long moveDelayMs,
      @Value("${ludo.bot.scheduler-pool-size:0}") int poolSizeOverride
  ) {
    this.gameEngineService = gameEngineService;
    this.roomService = roomService;
    this.botService = botService;
    this.gameEventBus = gameEventBus;
    this.rollDelayMs = Math.max(0, rollDelayMs);
    this.moveDelayMs = Math.max(0, moveDelayMs);

    int cpus = Math.max(2, Runtime.getRuntime().availableProcessors());
    int poolSize =
        poolSizeOverride > 0
            ? poolSizeOverride
            : Math.min(32, Math.max(8, cpus * 2));
    AtomicInteger n = new AtomicInteger();
    ThreadFactory tf =
        r -> {
          Thread t = new Thread(r, "ludo-bot-" + n.incrementAndGet());
          t.setDaemon(true);
          return t;
        };
    ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(poolSize, tf);
    exec.setRemoveOnCancelPolicy(true);
    exec.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    this.botScheduler = exec;
    log.info(
        "Bot scheduler poolSize={} rollDelayMs={} moveDelayMs={} (LudoGame-style deadlines)",
        poolSize,
        this.rollDelayMs,
        this.moveDelayMs);
  }

  public void schedule(String roomId) {
    if (roomId == null || roomId.isBlank()) {
      return;
    }
    requested.computeIfAbsent(roomId, k -> new AtomicBoolean(false)).set(true);
    tryKick(roomId, peekBotDelay(roomId));
  }

  private void tryKick(String roomId, long delayMs) {
    AtomicBoolean flag = running.computeIfAbsent(roomId, k -> new AtomicBoolean(false));
    if (!flag.compareAndSet(false, true)) {
      return;
    }
    botScheduler.schedule(() -> tick(roomId), Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
  }

  /** LudoGame phaseDeadline: wait before roll / before move; 0 if not a bot turn. */
  private long peekBotDelay(String roomId) {
    try {
      if (!gameEngineService.hasMatch(roomId)) {
        return 0L;
      }
      GameSnapshot snap = gameEngineService.getSnapshot(roomId);
      if (!isBotTurn(snap)) {
        return 0L;
      }
      return GameEngineService.PHASE_MOVE.equals(snap.getPhase()) ? moveDelayMs : rollDelayMs;
    } catch (Exception e) {
      return rollDelayMs;
    }
  }

  private void tick(String roomId) {
    try {
      if (!gameEngineService.hasMatch(roomId)) {
        finishRoom(roomId);
        return;
      }

      requested.computeIfAbsent(roomId, k -> new AtomicBoolean(false)).set(false);

      GameSnapshot snap = gameEngineService.getSnapshot(roomId);
      if (!isBotTurn(snap)) {
        gameEventBus.publishSnapshot(roomId, snap);
        finishRoom(roomId);
        return;
      }

      BotDifficulty diff = resolveDifficulty(roomId, snap);
      long seqBefore = snap.getActionSeq();
      snap =
          botService.executeOneBotAction(
              roomId, diff, step -> gameEventBus.publishSnapshot(roomId, step));

      if (snap.getActionSeq() == seqBefore) {
        // No progress (race / illegal) — avoid hot loop
        log.debug("Bot tick no-op roomId={} seq={}", roomId, seqBefore);
        finishRoom(roomId);
        return;
      }

      if (isBotTurn(snap)) {
        long delay =
            GameEngineService.PHASE_MOVE.equals(snap.getPhase()) ? moveDelayMs : rollDelayMs;
        // Keep running=true while the delayed continuation is pending
        botScheduler.schedule(() -> tick(roomId), delay, TimeUnit.MILLISECONDS);
        return;
      }

      gameEventBus.publishSnapshot(roomId, snap);
      finishRoom(roomId);
    } catch (Exception ex) {
      log.warn("Bot tick failed roomId={}: {}", roomId, ex.toString());
      try {
        if (gameEngineService.hasMatch(roomId)) {
          gameEventBus.publishSnapshot(roomId, gameEngineService.getSnapshot(roomId));
        }
      } catch (Exception ignored) {
        // non-fatal
      }
      finishRoom(roomId);
    }
  }

  private void finishRoom(String roomId) {
    AtomicBoolean flag = running.get(roomId);
    if (flag != null) {
      flag.set(false);
    }
    AtomicBoolean want = requested.get(roomId);
    if (want != null && want.get()) {
      tryKick(roomId, peekBotDelay(roomId));
    }
  }

  private static boolean isBotTurn(GameSnapshot snap) {
    if (snap == null || snap.getIsBot() == null) {
      return false;
    }
    if (GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return false;
    }
    if (!GameEngineService.PHASE_ROLL.equals(snap.getPhase())
        && !GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
      return false;
    }
    int seat = snap.getCurrentSeatIndex();
    return seat >= 0 && seat < snap.getIsBot().length && snap.getIsBot()[seat];
  }

  private BotDifficulty resolveDifficulty(String roomId, GameSnapshot snap) {
    BotDifficulty diff = BotDifficulty.HARD;
    Room room = roomService.getRoom(roomId).orElse(null);
    if (room != null && snap.getCurrentSeatIndex() < room.getPlayers().size()) {
      RoomPlayer p = room.getPlayers().get(snap.getCurrentSeatIndex());
      if (p.getBotDifficulty() != null) {
        diff = p.getBotDifficulty();
      }
    }
    return diff;
  }

  @PreDestroy
  void shutdown() {
    botScheduler.shutdownNow();
  }
}
