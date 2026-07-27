package com.ludo.backend.game;

import com.ludo.backend.bot.BotService;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.BotDifficulty;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TurnTimeoutScheduler {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final BotService botService;
  private final GameEventBus gameEventBus;
  private final ExecutorService botExecutor = Executors.newCachedThreadPool();

  public TurnTimeoutScheduler(
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

  @Scheduled(fixedDelay = 1000)
  public void tick() {
    for (String roomId : gameEngineService.activeRoomIds()) {
      try {
        GameSnapshot before = gameEngineService.getSnapshot(roomId);
        if (GameEngineService.PHASE_FINISHED.equals(before.getPhase())) {
          continue;
        }
        if (before.getTurnSecondsRemaining() > 0) {
          continue;
        }
        int seatBefore = before.getCurrentSeatIndex();
        String phaseBefore = before.getPhase();
        GameSnapshot after = gameEngineService.resolveTimeout(roomId);
        boolean changed =
            after.getCurrentSeatIndex() != seatBefore
                || !phaseBefore.equals(after.getPhase());
        if (!changed) {
          continue;
        }
        gameEventBus.publishSnapshot(roomId, after);
        if (GameEngineService.PHASE_FINISHED.equals(after.getPhase())) {
          roomService.getRoom(roomId).ifPresent(
              room -> roomService.settleIfFinished(room, after)
          );
        }
        maybeScheduleBot(roomId);
      } catch (Exception ignored) {
        // room may have ended mid-tick
      }
    }
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
          if (room != null) {
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
        gameEventBus.publishSnapshot(roomId, gameEngineService.getSnapshot(roomId));
      } catch (Exception ignored) {
        try {
          if (gameEngineService.hasMatch(roomId)) {
            gameEventBus.publishSnapshot(
                roomId,
                gameEngineService.getSnapshot(roomId)
            );
          }
        } catch (Exception ignored2) {
          // ignore recovery publish failure
        }
      }
    });
  }
}
