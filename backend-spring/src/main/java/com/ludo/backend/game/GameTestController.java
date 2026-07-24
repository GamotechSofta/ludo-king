package com.ludo.backend.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/game/test")
public class GameTestController {

  private final GameEngineService gameEngineService;

  public GameTestController(GameEngineService gameEngineService) {
    this.gameEngineService = gameEngineService;
  }

  @PostMapping("/create")
  public GameSnapshot create(@RequestParam(defaultValue = "2") int players) {
    String roomId = "test-" + UUID.randomUUID();
    List<LudoColor> colors = LudoColor.forPlayerCount(players);
    List<GameEngineService.SeatInfo> seats = new ArrayList<>();
    for (int i = 0; i < players; i++) {
      seats.add(new GameEngineService.SeatInfo(
          "u" + i,
          "Player " + (i + 1),
          colors.get(i),
          false
      ));
    }
    return gameEngineService.createMatch(roomId, seats);
  }

  @PostMapping("/{roomId}/roll")
  public GameSnapshot roll(@PathVariable String roomId, @RequestParam int seat) {
    return gameEngineService.rollDiceAsSeat(roomId, seat);
  }

  @PostMapping("/{roomId}/move")
  public GameSnapshot move(
      @PathVariable String roomId,
      @RequestParam int seat,
      @RequestParam int token,
      @RequestParam int diceIndex
  ) {
    return gameEngineService.moveTokenAsSeat(roomId, seat, token, diceIndex);
  }

  @GetMapping("/{roomId}")
  public GameSnapshot get(@PathVariable String roomId) {
    return gameEngineService.getSnapshot(roomId);
  }

  @GetMapping("/{roomId}/legal")
  public Map<String, Object> legal(@PathVariable String roomId) {
    return Map.of("moves", gameEngineService.legalMoves(roomId));
  }
}
