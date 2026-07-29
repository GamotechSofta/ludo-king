package com.ludo.backend.game;

import com.ludo.backend.bot.BotTurnCoordinator;
import com.ludo.backend.realtime.GameEventBus;
import com.ludo.backend.room.RoomService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TurnTimeoutScheduler {

  private final GameEngineService gameEngineService;
  private final RoomService roomService;
  private final GameEventBus gameEventBus;
  private final BotTurnCoordinator botTurnCoordinator;

  public TurnTimeoutScheduler(
      GameEngineService gameEngineService,
      RoomService roomService,
      GameEventBus gameEventBus,
      BotTurnCoordinator botTurnCoordinator
  ) {
    this.gameEngineService = gameEngineService;
    this.roomService = roomService;
    this.gameEventBus = gameEventBus;
    this.botTurnCoordinator = botTurnCoordinator;
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
          roomService.settleIfFinishedAsync(roomId, after);
        } else {
          botTurnCoordinator.schedule(roomId);
        }
      } catch (Exception ignored) {
        // room may have ended mid-tick
      }
    }
  }
}
