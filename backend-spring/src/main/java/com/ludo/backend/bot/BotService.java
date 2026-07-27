package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.bot.BotMoveEvaluator.Context;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Bot turn loop + move selection. Scoring is delegated to {@link BotMoveEvaluator}
 * so every legal move (jail exit, capture, progress, …) is compared on one scale.
 *
 * <p>Difficulty: EASY occasionally ignores the best move; MEDIUM uses core
 * tactics; HARD adds future prediction and stronger danger avoidance.
 */
@Service
public class BotService {

  private static final Logger log = LoggerFactory.getLogger(BotService.class);

  /** EASY: chance to ignore the best move. */
  private static final int EASY_MISTAKE_PCT = 30;
  /** MEDIUM: smaller strategic mistakes. */
  private static final int MEDIUM_MISTAKE_PCT = 12;
  /** HARD: rare blunders only. */
  private static final int HARD_MISTAKE_PCT = 3;

  private final GameEngineService gameEngineService;
  private final boolean smartKillDiceAssist;

  public BotService(
      GameEngineService gameEngineService,
      @Value("${ludo.bot.smart-kill-dice-assist:true}") boolean smartKillDiceAssist
  ) {
    this.gameEngineService = gameEngineService;
    this.smartKillDiceAssist = smartKillDiceAssist;
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
          Integer assistDice = null;
          if (smartKillDiceAssist && diff == BotDifficulty.HARD) {
            assistDice =
                BotKillDiceAssist.maybePickCaptureDice(
                    snap,
                    seat,
                    (token, dice) ->
                        gameEngineService.canBotUseDiceForAssist(roomId, seat, token, dice));
          }
          snap = gameEngineService.rollDiceAsSeat(roomId, seat, assistDice);
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
   * Evaluates every legal move with {@link BotMoveEvaluator}, then picks the
   * highest score (random among ties). Sole-active-pawn shortcut only when that
   * pawn is the unique legal choice (no competing jail exit).
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

    // Only one active pawn AND no jail-exit alternative → play it immediately
    List<int[]> soleMoves = soleActivePawnOnlyMoves(moves, ownPositions);
    if (soleMoves != null) {
      return pickBestScored(soleMoves, snap, color, seat, ownPositions, allPositions, seatColors, difficulty);
    }

    return pickBestScored(moves, snap, color, seat, ownPositions, allPositions, seatColors, difficulty);
  }

  private int[] pickBestScored(
      List<int[]> moves,
      GameSnapshot snap,
      LudoColor color,
      int seat,
      List<Integer> ownPositions,
      Map<String, List<Integer>> allPositions,
      List<String> seatColors,
      BotDifficulty difficulty
  ) {
    Context ctx =
        new Context(color, seat, ownPositions, allPositions, seatColors, difficulty);

    List<ScoredMove> scored = new ArrayList<>(moves.size());
    for (int[] m : moves) {
      MoveEval eval = evaluateMove(m, snap, color, ownPositions);
      if (eval == null) {
        continue;
      }
      long value =
          BotMoveEvaluator.scoreMove(
              ctx, eval.token, eval.from, eval.to, eval.dice);
      scored.add(new ScoredMove(m, value));
    }

    if (scored.isEmpty()) {
      return moves.get(0);
    }

    scored.sort((a, b) -> Long.compare(b.score, a.score));
    long best = scored.get(0).score;

    // Difficulty mistakes: occasionally pick a weaker move
    int mistakePct = mistakePercent(difficulty);
    if (mistakePct > 0
        && scored.size() > 1
        && ThreadLocalRandom.current().nextInt(100) < mistakePct) {
      int pick = 1 + ThreadLocalRandom.current().nextInt(Math.min(3, scored.size() - 1));
      return scored.get(pick).move;
    }

    List<int[]> ties = new ArrayList<>();
    for (ScoredMove s : scored) {
      if (s.score == best) {
        ties.add(s.move);
      } else {
        break;
      }
    }
    return ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
  }

  private static int mistakePercent(BotDifficulty difficulty) {
    if (difficulty == BotDifficulty.EASY) {
      return EASY_MISTAKE_PCT;
    }
    if (difficulty == BotDifficulty.MEDIUM) {
      return MEDIUM_MISTAKE_PCT;
    }
    if (difficulty == BotDifficulty.HARD) {
      return HARD_MISTAKE_PCT;
    }
    return 0;
  }

  /**
   * When exactly one pawn is on the board (not jail, not home) and every legal
   * move belongs to that pawn, return those moves. If a 6 also opens a jail
   * exit, returns null so full evaluation can prefer releasing a second pawn.
   */
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
      Integer posObj = ownPositions.get(i);
      int pos = posObj == null ? JAIL : posObj;
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

  private static final class ScoredMove {
    final int[] move;
    final long score;

    ScoredMove(int[] move, long score) {
      this.move = move;
      this.score = score;
    }
  }

  private static final class MoveEval {
    final int token;
    final int dice;
    final int from;
    final int to;

    MoveEval(int token, int dice, int from, int to) {
      this.token = token;
      this.dice = dice;
      this.from = from;
      this.to = to;
    }
  }

  private MoveEval evaluateMove(
      int[] m,
      GameSnapshot snap,
      LudoColor color,
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
    int to = BotMoveEvaluator.applySteps(color, from, dice);
    return new MoveEval(token, dice, from, to);
  }

  private static String resolveSeatColor(GameSnapshot snap, int seat) {
    List<String> seatColors = snap.getSeatColors();
    if (seatColors != null && seat >= 0 && seat < seatColors.size()) {
      return seatColors.get(seat);
    }
    return snap.getCurrentColor();
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
