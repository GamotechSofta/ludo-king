package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.superior.SuperiorBotBridge;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Bot action executor. Delays live in {@link BotTurnCoordinator} (LudoGame-style
 * deadlines) — this class never sleeps, so workers stay free under load.
 *
 * <p>Dice are never manipulated; move pick uses SuperiorBotEngine.
 */
@Service
public class BotService {

  private static final Logger log = LoggerFactory.getLogger(BotService.class);

  private final GameEngineService gameEngineService;
  private final SuperiorBotBridge superiorBotBridge;

  public BotService(GameEngineService gameEngineService, SuperiorBotBridge superiorBotBridge) {
    this.gameEngineService = gameEngineService;
    this.superiorBotBridge = superiorBotBridge;
  }

  /**
   * One atomic bot step: roll XOR move (or skip). No Thread.sleep.
   * Used by the deadline-driven {@link BotTurnCoordinator}.
   */
  public GameSnapshot executeOneBotAction(
      String roomId, BotDifficulty difficulty, Consumer<GameSnapshot> onStep) {
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

    try {
      if (GameEngineService.PHASE_ROLL.equals(snap.getPhase())) {
        snap = gameEngineService.rollDiceAsSeat(roomId, seat, null);
        publish(onStep, snap);
        return snap;
      }

      if (GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
        List<int[]> moves = gameEngineService.legalMoves(roomId);
        if (moves == null || moves.isEmpty()) {
          log.info("Bot seat {} in room {} has no legal moves — skipping turn", seat, roomId);
          snap = gameEngineService.skipTurn(roomId);
          publish(onStep, snap);
          return snap;
        }
        int[] chosen = chooseMove(roomId, seat, moves, diff);
        snap = gameEngineService.moveTokenAsSeat(roomId, seat, chosen[0], chosen[1]);
        publish(onStep, snap);
        return snap;
      }
    } catch (RuntimeException ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : "";
      if (msg.contains("Dice already rolled")
          || msg.contains("Not your turn")
          || msg.contains("Cannot roll")
          || msg.contains("Cannot move")) {
        log.debug("Bot action noop roomId={} seat={}: {}", roomId, seat, msg);
        snap = gameEngineService.getSnapshot(roomId);
        publish(onStep, snap);
        return snap;
      }
      log.warn(
          "Bot action failed roomId={} seat={}: {} — forcing PASS",
          roomId,
          seat,
          ex.toString());
      try {
        snap = gameEngineService.skipTurn(roomId);
        publish(onStep, snap);
      } catch (RuntimeException skipEx) {
        log.error("Bot force-pass failed roomId={} seat={}", roomId, seat, skipEx);
        snap = gameEngineService.getSnapshot(roomId);
      }
    }
    return snap;
  }

  /** @deprecated Prefer {@link #executeOneBotAction}; kept for tests. */
  public GameSnapshot takeTurnIfBot(String roomId, BotDifficulty difficulty) {
    return takeTurnIfBot(roomId, difficulty, null);
  }

  /** @deprecated Prefer coordinator-driven single steps (no sleep). */
  public GameSnapshot takeTurnIfBot(
      String roomId, BotDifficulty difficulty, Consumer<GameSnapshot> onStep) {
    GameSnapshot snap = gameEngineService.getSnapshot(roomId);
    int guard = 0;
    while (guard++ < 12) {
      if (snap.getIsBot() == null) {
        break;
      }
      int seat = snap.getCurrentSeatIndex();
      if (seat < 0
          || seat >= snap.getIsBot().length
          || !snap.getIsBot()[seat]
          || GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
        break;
      }
      if (!GameEngineService.PHASE_ROLL.equals(snap.getPhase())
          && !GameEngineService.PHASE_MOVE.equals(snap.getPhase())) {
        break;
      }
      long seq = snap.getActionSeq();
      snap = executeOneBotAction(roomId, difficulty, onStep);
      if (snap.getActionSeq() == seq) {
        break;
      }
      if (snap.getCurrentSeatIndex() != seat) {
        break;
      }
    }
    return snap;
  }

  private void publish(Consumer<GameSnapshot> onStep, GameSnapshot snap) {
    if (onStep != null) {
      onStep.accept(snap);
    }
  }

  private int[] chooseMove(
      String roomId, int seat, List<int[]> moves, BotDifficulty difficulty) {
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

    // Hard rule on real board cells: if any legal move kills, always take a kill.
    List<int[]> kills = captureMoves(snap, seat, colorName, ownPositions, candidates);
    if (!kills.isEmpty()) {
      if (kills.size() == 1) {
        return kills.get(0);
      }
      int[] amongKills = superiorBotBridge.chooseMove(snap, seat, kills, difficulty);
      return amongKills != null ? amongKills : kills.get(0);
    }

    int[] chosen = superiorBotBridge.chooseMove(snap, seat, candidates, difficulty);
    return chosen != null ? chosen : candidates.get(0);
  }

  /** Legal moves that land on a capturable opponent (unsafe main cell, single token). */
  private static List<int[]> captureMoves(
      GameSnapshot snap,
      int seat,
      String colorName,
      List<Integer> ownPositions,
      List<int[]> moves) {
    LudoColor color = BotBoardMath.parseColor(colorName);
    if (color == null || moves == null || snap.getDiceList() == null) {
      return List.of();
    }
    Map<String, List<Integer>> all = snap.getTokenPositions();
    List<String> colors = snap.getSeatColors();
    boolean[] isBot = snap.getIsBot();
    List<int[]> kills = new ArrayList<>();
    for (int[] m : moves) {
      if (m == null || m.length < 2) {
        continue;
      }
      int token = m[0];
      int diceIndex = m[1];
      if (token < 0 || token >= ownPositions.size()) {
        continue;
      }
      if (diceIndex < 0 || diceIndex >= snap.getDiceList().size()) {
        continue;
      }
      int from = ownPositions.get(token) == null ? JAIL : ownPositions.get(token);
      int dice = snap.getDiceList().get(diceIndex);
      int to = BotBoardMath.applySteps(color, from, dice);
      if (BotBoardMath.findCaptureVictim(seat, to, all, colors, isBot) != null) {
        kills.add(m);
      }
    }
    return kills;
  }

  private static List<int[]> soleActivePawnOnlyMoves(
      List<int[]> moves, List<Integer> ownPositions) {
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
}
