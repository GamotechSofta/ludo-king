package com.ludo.backend.room;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MatchmakingScheduler {

  private final RoomService roomService;

  public MatchmakingScheduler(RoomService roomService) {
    this.roomService = roomService;
  }

  @Scheduled(fixedDelay = 2000)
  public void tick() {
    roomService.processQueues();
    roomService.processExpiredFills();
  }
}
