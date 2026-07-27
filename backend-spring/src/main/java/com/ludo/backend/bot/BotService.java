package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.exitIndex;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Smart bot move selection — evaluates every legal move with strict priority bands
 * so a stronger option always beats a weaker one. Tuned to win more often.
 */
@Service
public class BotService {

  private static final Logger log = LoggerFactory.getLogger(BotService.class);

  /** Lexicographic bands for non-capture moves. */
  private static final int SCORE_REACH_HOME = 100_000_000;
  private static final int SCORE_EXIT_JAIL = 1_000_000;
  private static final int SCORE_AVOID_THREAT = 100_000;
  private static final int SCORE_SAFE_CELL = 10_000;
  private static final int SCORE_CREATE_BLOCK = 1_000;
  private static final int SCORE_KEEP_BLOCK = 100;
  private static final int SCORE_NEAREST_HOME_MAX = 99;
  private static final int SCORE_LEAVE_DANGER = 5_000;
  private static final int SCORE_ENTER_EXIT_LANE = 50_000;

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
    if (snap.getIsBot() == null
        || seat < 0
        || seat >= snap.getIsBot().length
        || !Boolean.TRUE.equals(snap.getIsBot()[seat])) {
      return snap;
    }

    BotDifficulty diff = difficulty == null ? BotDifficulty.HARD : difficulty;

    int guard = 0;
    while (guard++ < 12
        && snap.getIsBot() != null
        && seat < snap.getIsBot().length
        && Boolean.TRUE.equals(snap.getIsBot()[seat])
        && snap.getCurrentSeatIndex() == seat
        && !GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {

      try {
        if (GameEngineService.PHASE_ROLL.equals(snap.getPhase())) {
          sleepBeforeDiceRoll();
          snap = gameEngineService.rollDiceAsSeat(roomId, seat);
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
        log.warn(
            "Bot turn failed roomId={} seat={}: {} — forcing PASS",
            roomId,
            seat,
            ex.toString()
        );
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

  /**
   * Capture-first selection, then existing smart scoring.
   * When any capture exists: always capture (no randomness).
   * EASY noise only applies when no capture is available.
   */
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
    LudoColor color = LudoColor.valueOf(colorName);
    List<Integer> ownPositions = snap.getTokenPositions().get(colorName);
    if (ownPositions == null) {
      return moves.get(0);
    }

    Map<String, List<Integer>> allPositions = snap.getTokenPositions();
    List<String> seatColors = snap.getSeatColors();

    // 1) Absolute priority: capture if any legal capture exists
    List<CaptureCandidate> captures =
        collectCaptureMoves(
            moves,
            snap,
            color,
            seat,
            ownPositions,
            allPositions,
            seatColors
        );
    if (!captures.isEmpty()) {
      return selectBestCapture(captures).move;
    }

    // 2) No capture — EASY may play a weaker random legal move
    if (difficulty == BotDifficulty.EASY
        && ThreadLocalRandom.current().nextInt(100) < 25) {
      return moves.get(ThreadLocalRandom.current().nextInt(moves.size()));
    }

    boolean hard =
        difficulty == BotDifficulty.HARD || difficulty == BotDifficulty.MEDIUM;

    List<int[]> bestMoves = new ArrayList<>();
    int bestScore = Integer.MIN_VALUE;

    for (int[] m : moves) {
      MoveEval eval = evaluateMove(m, snap, color, seat, ownPositions);
      if (eval == null) {
        continue;
      }
      int score =
          scoreNonCaptureMove(
              color,
              seat,
              eval.token,
              eval.from,
              eval.to,
              eval.dice,
              ownPositions,
              allPositions,
              seatColors,
              hard
          );

      if (score > bestScore) {
        bestScore = score;
        bestMoves.clear();
        bestMoves.add(m);
      } else if (score == bestScore) {
        bestMoves.add(m);
      }
    }

    if (bestMoves.isEmpty()) {
      return moves.get(0);
    }
    return bestMoves.get(ThreadLocalRandom.current().nextInt(bestMoves.size()));
  }

  private static final class MoveEval {
    final int token;
    final int diceIndex;
    final int dice;
    final int from;
    final int to;

    MoveEval(int token, int diceIndex, int dice, int from, int to) {
      this.token = token;
      this.diceIndex = diceIndex;
      this.dice = dice;
      this.from = from;
      this.to = to;
    }
  }

  private static final class CaptureCandidate {
    final int[] move;
    final int token;
    final int diceIndex;
    /** Victim remaining steps to HOME (lower = closer to home). */
    final int victimRemaining;
    /** Victim journey progress (higher = farther along). */
    final int victimProgress;
    /** How much this move advances the bot pawn (higher better). */
    final int botAdvance;

    CaptureCandidate(
        int[] move,
        int token,
        int diceIndex,
        int victimRemaining,
        int victimProgress,
        int botAdvance
    ) {
      this.move = move;
      this.token = token;
      this.diceIndex = diceIndex;
      this.victimRemaining = victimRemaining;
      this.victimProgress = victimProgress;
      this.botAdvance = botAdvance;
    }
  }

  private MoveEval evaluateMove(
      int[] m,
      GameSnapshot snap,
      LudoColor color,
      int seat,
      List<Integer> ownPositions
  ) {
    if (m == null || m.length < 2) {
      return null;
    }
    int token = m[0];
    int diceIndex = m[1];
    if (diceIndex < 0 || diceIndex >= snap.getDiceList().size()) {
      return null;
    }
    if (token < 0 || token >= ownPositions.size()) {
      return null;
    }
    Integer fromObj = ownPositions.get(token);
    int from = fromObj == null ? JAIL : fromObj;
    int dice = snap.getDiceList().get(diceIndex);
    int to = applySteps(color, from, dice);
    return new MoveEval(token, diceIndex, dice, from, to);
  }

  /** All legal moves that would capture an unprotected single opponent. */
  private List<CaptureCandidate> collectCaptureMoves(
      List<int[]> moves,
      GameSnapshot snap,
      LudoColor color,
      int seat,
      List<Integer> ownPositions,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    List<CaptureCandidate> out = new ArrayList<>();
    for (int[] m : moves) {
      MoveEval eval = evaluateMove(m, snap, color, seat, ownPositions);
      if (eval == null) {
        continue;
      }
      VictimInfo victim =
          findCaptureVictim(seat, eval.to, allPositions, seatColors);
      if (victim == null) {
        continue;
      }
      int victimRemaining = remainingDistance(victim.color, eval.to);
      if (victimRemaining == Integer.MAX_VALUE) {
        victimRemaining = TOTAL_TILES + HOME_STEPS;
      }
      int victimProgress =
          Math.max(0, TOTAL_TILES + HOME_STEPS - victimRemaining);
      int botFromRem = remainingDistance(color, eval.from);
      int botToRem = remainingDistance(color, eval.to);
      int botAdvance = 0;
      if (botFromRem != Integer.MAX_VALUE && botToRem != Integer.MAX_VALUE) {
        botAdvance = Math.max(0, botFromRem - botToRem);
      } else if (isJail(eval.from)) {
        botAdvance = eval.dice;
      } else {
        botAdvance = eval.dice;
      }
      out.add(
          new CaptureCandidate(
              m,
              eval.token,
              eval.diceIndex,
              victimRemaining,
              victimProgress,
              botAdvance
          )
      );
    }
    return out;
  }

  /**
   * Among captures: closest victim to HOME, then highest victim progress,
   * then most bot advance. Fully deterministic — no random.
   */
  private static CaptureCandidate selectBestCapture(List<CaptureCandidate> captures) {
    CaptureCandidate best = captures.get(0);
    for (int i = 1; i < captures.size(); i++) {
      CaptureCandidate c = captures.get(i);
      int cmp = compareCaptures(c, best);
      if (cmp > 0) {
        best = c;
      }
    }
    return best;
  }

  /** Positive if a is better than b. */
  private static int compareCaptures(CaptureCandidate a, CaptureCandidate b) {
    // 1) Capture pawn closest to Home (smaller remaining)
    if (a.victimRemaining != b.victimRemaining) {
      return Integer.compare(b.victimRemaining, a.victimRemaining);
    }
    // 2) Highest victim progress
    if (a.victimProgress != b.victimProgress) {
      return Integer.compare(a.victimProgress, b.victimProgress);
    }
    // 3) Bot pawn advances the most
    if (a.botAdvance != b.botAdvance) {
      return Integer.compare(a.botAdvance, b.botAdvance);
    }
    // Stable tie-break: lower token index, then lower dice index
    if (a.token != b.token) {
      return Integer.compare(b.token, a.token);
    }
    return Integer.compare(b.diceIndex, a.diceIndex);
  }

  private static final class VictimInfo {
    final LudoColor color;

    VictimInfo(LudoColor color) {
      this.color = color;
    }
  }

  /** Single unprotected opponent on {@code landPos}; if several, closest to HOME. */
  private static VictimInfo findCaptureVictim(
      int moverSeat,
      int landPos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (!isMain(landPos) || isSafe(landPos) || allPositions == null || seatColors == null) {
      return null;
    }
    VictimInfo best = null;
    int bestRemaining = Integer.MAX_VALUE;
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == moverSeat) {
        continue;
      }
      String c = seatColors.get(s);
      List<Integer> positions = allPositions.get(c);
      if (positions == null) {
        continue;
      }
      int n = 0;
      for (Integer p : positions) {
        if (p != null && p == landPos) {
          n++;
        }
      }
      if (n != 1) {
        continue;
      }
      LudoColor victimColor;
      try {
        victimColor = LudoColor.valueOf(c);
      } catch (RuntimeException ignored) {
        continue;
      }
      int rem = remainingDistance(victimColor, landPos);
      if (rem == Integer.MAX_VALUE) {
        rem = TOTAL_TILES + HOME_STEPS;
      }
      if (best == null || rem < bestRemaining) {
        best = new VictimInfo(victimColor);
        bestRemaining = rem;
      }
    }
    return best;
  }

  /** Existing non-capture priorities (HOME, jail, safe, progress, …). */
  private int scoreNonCaptureMove(
      LudoColor color,
      int seat,
      int token,
      int from,
      int to,
      int dice,
      List<Integer> ownPositions,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors,
      boolean hard
  ) {
    int score = 0;

    // Reach HOME
    if (isHome(to)) {
      score += SCORE_REACH_HOME;
    }

    // Exit jail on six
    if (isJail(from) && dice == 6) {
      score += SCORE_EXIT_JAIL;
    }

    // Enter private exit column
    if (isMain(from) && isExit(to)) {
      score += SCORE_ENTER_EXIT_LANE;
    }

    // Avoid threatened landing
    if (!isPositionThreatened(seat, to, allPositions, seatColors)) {
      score += SCORE_AVOID_THREAT;
    }

    if (hard
        && isMain(from)
        && isPositionThreatened(seat, from, allPositions, seatColors)
        && !isPositionThreatened(seat, to, allPositions, seatColors)) {
      score += SCORE_LEAVE_DANGER;
    }

    // Prefer safe / exit / HOME (safe star)
    if (isSafe(to) || isHome(to) || isExit(to)) {
      score += SCORE_SAFE_CELL;
    }

    int ownOnDest = countOwnOnCell(ownPositions, token, to);
    if (isMain(to) && ownOnDest >= 1) {
      score += SCORE_CREATE_BLOCK;
    }

    int ownOnFrom = countOwnOnCell(ownPositions, -1, from);
    if (!(isMain(from) && ownOnFrom >= 2)) {
      score += SCORE_KEEP_BLOCK;
    }

    int remainingAfter = remainingDistance(color, to);
    if (remainingAfter != Integer.MAX_VALUE) {
      int progress = Math.max(0, TOTAL_TILES + HOME_STEPS - remainingAfter);
      score += Math.min(SCORE_NEAREST_HOME_MAX, progress);
    }

    return score;
  }

  private static String resolveSeatColor(GameSnapshot snap, int seat) {
    List<String> seatColors = snap.getSeatColors();
    if (seatColors != null && seat >= 0 && seat < seatColors.size()) {
      return seatColors.get(seat);
    }
    return snap.getCurrentColor();
  }

  private static int countOwnOnCell(List<Integer> ownPositions, int excludeToken, int cell) {
    if (ownPositions == null || isJail(cell) || isHome(cell)) {
      return 0;
    }
    int count = 0;
    for (int i = 0; i < ownPositions.size(); i++) {
      if (i == excludeToken) {
        continue;
      }
      Integer p = ownPositions.get(i);
      if (p != null && p == cell) {
        count++;
      }
    }
    return count;
  }

  private static boolean isPositionThreatened(
      int defenderSeat,
      int pos,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors
  ) {
    if (!isMain(pos) || isSafe(pos) || allPositions == null || seatColors == null) {
      return false;
    }
    for (int s = 0; s < seatColors.size(); s++) {
      if (s == defenderSeat) {
        continue;
      }
      String name = seatColors.get(s);
      LudoColor attacker;
      try {
        attacker = LudoColor.valueOf(name);
      } catch (RuntimeException ignored) {
        continue;
      }
      List<Integer> positions = allPositions.get(name);
      if (positions == null) {
        continue;
      }
      for (Integer from : positions) {
        if (from == null || !isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (applySteps(attacker, from, d) == pos) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static int remainingDistance(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return Integer.MAX_VALUE;
    }
    if (isExit(pos)) {
      return HOME_STEPS - 1 - exitIndex(pos);
    }
    int toExit = (color.exitTile() - pos + TOTAL_TILES) % TOTAL_TILES;
    return toExit + HOME_STEPS;
  }

  private static int applySteps(LudoColor color, int from, int steps) {
    if (isJail(from)) {
      return color.startTile();
    }
    int pos = from;
    for (int i = 0; i < steps; i++) {
      if (isMain(pos)) {
        if (pos == color.exitTile()) {
          pos = toExit(0);
        } else {
          pos = (pos + 1) % TOTAL_TILES;
        }
      } else if (isExit(pos)) {
        int idx = exitIndex(pos);
        if (idx >= EXIT_LEN - 1) {
          pos = HOME;
        } else {
          pos = toExit(idx + 1);
        }
      } else {
        break;
      }
    }
    return pos;
  }

  private void sleepBeforeDiceRoll() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(2000, 3001));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void sleepThinking() {
    try {
      Thread.sleep(ThreadLocalRandom.current().nextInt(950, 1401));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
