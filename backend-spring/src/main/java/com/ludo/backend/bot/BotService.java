package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.superior.SuperiorBotBridge;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bot turn loop. Move selection uses LudoGame {@link SuperiorBotBridge} / SuperiorBotEngine.
 * Dice are never manipulated — only chooses among legal options after a fair roll.
 */
@Service
public class BotService {

  private static final Logger log = LoggerFactory.getLogger(BotService.class);

  private final GameEngineService gameEngineService;
  private final SuperiorBotBridge superiorBotBridge;
  private final int rollDelayMinMs;
  private final int rollDelayMaxMs;
  private final int thinkDelayMinMs;
  private final int thinkDelayMaxMs;

  public BotService(
      GameEngineService gameEngineService,
      SuperiorBotBridge superiorBotBridge,
      @Value("${ludo.bot.roll-delay-min-ms:450}") int rollDelayMinMs,
      @Value("${ludo.bot.roll-delay-max-ms:750}") int rollDelayMaxMs,
      @Value("${ludo.bot.think-delay-min-ms:180}") int thinkDelayMinMs,
      @Value("${ludo.bot.think-delay-max-ms:350}") int thinkDelayMaxMs
  ) {
    this.gameEngineService = gameEngineService;
    this.superiorBotBridge = superiorBotBridge;
    this.rollDelayMinMs = Math.max(0, rollDelayMinMs);
    this.rollDelayMaxMs = Math.max(this.rollDelayMinMs + 1, rollDelayMaxMs);
    this.thinkDelayMinMs = Math.max(0, thinkDelayMinMs);
    this.thinkDelayMaxMs = Math.max(this.thinkDelayMinMs + 1, thinkDelayMaxMs);
  }

  public GameSnapshot takeTurnIfBot(String roomId, BotDifficulty difficulty) {
    return takeTurnIfBot(roomId, difficulty, null);
  }

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
    if (snap.getIsBot() == null
        || seat < 0
        || seat >= snap.getIsBot().length
        || !snap.getIsBot()[seat]) {
      return snap;
    }

    BotDifficulty diff = difficulty == null ? BotDifficulty.HARD : difficulty;

    int guard = 0;
    while (guard++ < 12
        && snap.getIsBot() != null
        && seat < snap.getIsBot().length
        && snap.getIsBot()[seat]
        && snap.getCurrentSeatIndex() == seat
        && !GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {

      try {
        if (GameEngineService.PHASE_ROLL.equals(snap.getPhase())) {
          sleepBeforeDiceRoll();
          // LudoGame-style: fair dice only — never force a face
          snap = gameEngineService.rollDiceAsSeat(roomId, seat, null);
          publish(onStep, snap);
          if (snap.getCurrentSeatIndex() != seat
              || !GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
            break;
          }
          continue;
        }

        if (GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
          List<int[]> moves = gameEngineService.legalMoves(roomId);
          if (moves == null || moves.isEmpty()) {
            log.info("Bot seat {} in room {} has no legal moves — skipping turn", seat, roomId);
            snap = gameEngineService.skipTurn(roomId);
            publish(onStep, snap);
            break;
          }
          int[] chosen = chooseMove(roomId, seat, moves, diff);
          sleepThinking();
          snap = gameEngineService.moveTokenAsSeat(roomId, seat, chosen[0], chosen[1]);
          publish(onStep, snap);
          continue;
        }
      } catch (RuntimeException ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("Dice already rolled")
            || msg.contains("Not your turn")
            || msg.contains("Cannot roll")
            || msg.contains("Cannot move")) {
          log.debug("Bot turn noop roomId={} seat={}: {}", roomId, seat, msg);
          snap = gameEngineService.getSnapshot(roomId);
          publish(onStep, snap);
          break;
        }
        log.warn(
            "Bot turn failed roomId={} seat={}: {} — forcing PASS",
            roomId,
            seat,
            ex.toString());
        try {
          snap = gameEngineService.skipTurn(roomId);
          publish(onStep, snap);
        } catch (RuntimeException skipEx) {
          log.error("Bot force-pass failed roomId={} seat={}", roomId, seat, skipEx);
        }
        break;
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

  private int[] chooseMove(
      String roomId,
      int seat,
      List<int[]> moves,
      BotDifficulty difficulty
  ) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    String colorName = resolveSeatColor(snap, seat);
    if (colorName == null || snap.getDiceList() == null) {
      return moves.get(0);
    }
    List<Integer> ownPositions = snap.getTokenPositions().get(colorName);
    if (ownPositions == null) {
      return moves.get(0);
    }

    List<int[]> sole = soleActivePawnOnlyMoves(moves, ownPositions);
    List<int[]> candidates = sole != null ? sole : moves;
    int[] chosen = superiorBotBridge.chooseMove(snap, seat, candidates, difficulty);
    return chosen != null ? chosen : candidates.get(0);
  }

  private static List<int[]> soleActivePawnOnlyMoves(
      List<int[]> moves,
      List<Integer> ownPositions
  ) {
    if (moves == null || moves.isEmpty() || ownPositions == null) {
      return null;
    }
    int soleToken = -1;
    int activeCount = 0;
    for (int i = 0; i < ownPositions.size(); i++) {
      int pos = ownPositions.get(i) == null ? JAIL : ownPositions.get(i);
      if (isJail(pos) || isHome(pos)) {
        continue;
      }
      activeCount++;
      soleToken = i;
    }
    if (activeCount != 1 || soleToken < 0) {
      return null;
    }
    List<int[]> soleMoves = new ArrayList<>();
    for (int[] m : moves) {
      if (m == null || m.length < 2) {
        continue;
      }
      if (m[0] != soleToken) {
        return null;
      }
      soleMoves.add(m);
    }
    return soleMoves.isEmpty() ? null : soleMoves;
  }

  private static String resolveSeatColor(GameSnapshot snap, int seat) {
    List<String> seatColors = snap.getSeatColors();
    if (seatColors != null && seat >= 0 && seat < seatColors.size()) {
      return seatColors.get(seat);
    }
    return snap.getCurrentColor();
  }

  private void sleepBeforeDiceRoll() {
    sleepRandom(rollDelayMinMs, rollDelayMaxMs);
  }

  private void sleepThinking() {
    sleepRandom(thinkDelayMinMs, thinkDelayMaxMs);
  }

  private void sleepRandom(int minMs, int maxMs) {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(minMs, maxMs + 1));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
