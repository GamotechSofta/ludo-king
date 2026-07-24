package com.ludo.backend.bot;

import com.ludo.backend.game.BoardConstants;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;

@Service
public class BotService {

  private final GameEngineService gameEngineService;

  public BotService(GameEngineService gameEngineService) {
    this.gameEngineService = gameEngineService;
  }

  public GameSnapshot takeTurnIfBot(String roomId, BotDifficulty difficulty) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    if (!GameEngineService.PHASE_ROLL.equals(snap.getPhase())
        && !GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
      return snap;
    }
    int seat = snap.getCurrentSeatIndex();
    if (snap.getIsBot() == null || !snap.getIsBot()[seat]) {
      return snap;
    }

    sleepThinking();

    if (GameEngineService.PHASE_ROLL.equals(snap.getPhase())) {
      snap = gameEngineService.rollDiceAsSeat(roomId, seat);
      // keep rolling sixes
      while (GameEngineService.PHASE_ROLL.equals(snap.getPhase())
          && snap.getDiceList() != null
          && !snap.getDiceList().isEmpty()
          && snap.getDiceList().get(snap.getDiceList().size() - 1) == 6
          && snap.getDiceList().size() < 3) {
        sleepThinking();
        snap = gameEngineService.rollDiceAsSeat(roomId, seat);
      }
    }

    while (GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
      List<int[]> moves = gameEngineService.legalMoves(roomId);
      if (moves.isEmpty()) {
        break;
      }
      int[] chosen = chooseMove(roomId, seat, moves, difficulty == null ? BotDifficulty.MEDIUM : difficulty);
      sleepThinking();
      snap = gameEngineService.moveTokenAsSeat(roomId, seat, chosen[0], chosen[1]);
    }
    return snap;
  }

  private int[] chooseMove(String roomId, int seat, List<int[]> moves, BotDifficulty difficulty) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    String color = snap.getCurrentColor();
    List<Integer> positions = snap.getTokenPositions().get(color);

    // Prefer capture / exit jail / safe / closest to home
    int[] best = moves.get(0);
    int bestScore = Integer.MIN_VALUE;

    for (int[] m : moves) {
      int token = m[0];
      int diceIndex = m[1];
      int dice = snap.getDiceList().get(diceIndex);
      int from = positions.get(token);
      int score = 0;

      if (from == BoardConstants.JAIL && dice == 6) {
        score += 80;
      }
      // rough: higher dice better toward finish
      score += dice;
      if (BoardConstants.SAFE_AREAS.contains(Math.max(from, 0))) {
        score += 10;
      }

      boolean hardCaptureBias = difficulty == BotDifficulty.HARD
          || (difficulty == BotDifficulty.EASY && ThreadLocalRandom.current().nextBoolean());
      if (hardCaptureBias) {
        score += 5;
      }

      if (score > bestScore) {
        bestScore = score;
        best = m;
      }
    }
    return best;
  }

  private void sleepThinking() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(800, 1501));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
