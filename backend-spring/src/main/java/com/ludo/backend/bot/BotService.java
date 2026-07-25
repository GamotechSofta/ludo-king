package com.ludo.backend.bot;

import com.ludo.backend.game.BoardConstants;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class BotService {

  private final GameEngineService gameEngineService;

  public BotService(GameEngineService gameEngineService) {
    this.gameEngineService = gameEngineService;
  }

  public GameSnapshot takeTurnIfBot(String roomId, BotDifficulty difficulty) {
    return takeTurnIfBot(roomId, difficulty, null);
  }

  /**
   * Plays one bot seat until the turn passes. When {@code onStep} is set, each
   * roll and each move is published immediately so clients can animate in realtime.
   */
  public GameSnapshot takeTurnIfBot(
      String roomId,
      BotDifficulty difficulty,
      Consumer<GameSnapshot> onStep
  ) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    if (!GameEngineService.PHASE_ROLL.equals(snap.getPhase())
        && !GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
      return snap;
    }
    int seat = snap.getCurrentSeatIndex();
    if (snap.getIsBot() == null || !snap.getIsBot()[seat]) {
      return snap;
    }

    int guard = 0;
    while (guard++ < 12
        && snap.getIsBot() != null
        && snap.getIsBot()[seat]
        && snap.getCurrentSeatIndex() == seat
        && !GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {

      if (GameEngineService.PHASE_ROLL.equals(snap.getPhase())) {
        sleepBeforeDiceRoll();
        snap = gameEngineService.rollDiceAsSeat(roomId, seat);
        publish(onStep, snap);
        continue;
      }

      if (GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
        List<int[]> moves = gameEngineService.legalMoves(roomId);
        if (moves.isEmpty()) {
          break;
        }
        int[] chosen =
            chooseMove(
                roomId,
                seat,
                moves,
                difficulty == null ? BotDifficulty.MEDIUM : difficulty
            );
        sleepThinking();
        snap = gameEngineService.moveTokenAsSeat(roomId, seat, chosen[0], chosen[1]);
        publish(onStep, snap);
        continue;
      }

      break;
    }
    return snap;
  }

  private void publish(Consumer<GameSnapshot> onStep, GameSnapshot snap) {
    if (onStep != null) {
      onStep.accept(snap);
    }
  }

  private int[] chooseMove(String roomId, int seat, List<int[]> moves, BotDifficulty difficulty) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    String color = snap.getCurrentColor();
    List<Integer> positions = snap.getTokenPositions().get(color);

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

  /** Online bot dice — wait 2–3 s before rolling. */
  private void sleepBeforeDiceRoll() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 3001));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void sleepThinking() {
    try {
      // Short pause so dice/moves are visible without feeling laggy
      Thread.sleep(ThreadLocalRandom.current().nextInt(350, 701));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
