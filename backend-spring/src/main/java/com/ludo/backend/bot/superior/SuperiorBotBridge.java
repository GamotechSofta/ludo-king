package com.ludo.backend.bot.superior;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOKENS_PER_COLOR;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Adapts {@link GameSnapshot} + legal {@code int[]{tokenIndex, diceIndex}} moves into
 * {@link SuperiorBotEngine} progress-space decisions.
 *
 * <p>Dice are never altered — only token/dice-index selection among legal moves.
 */
@Service
public class SuperiorBotBridge {

  private static final Logger log = LoggerFactory.getLogger(SuperiorBotBridge.class);

  /**
   * Pick a legal move for the bot seat.
   *
   * @param snap current snapshot
   * @param seat bot seat index
   * @param legalMoves each entry {@code {tokenIndex, diceIndex}}
   * @param roomDifficulty room {@link BotDifficulty} (mapped to {@link SuperiorBotDifficulty})
   * @return matching legal move, or first legal / null if none
   */
  public int[] chooseMove(
      GameSnapshot snap, int seat, List<int[]> legalMoves, BotDifficulty roomDifficulty) {
    if (legalMoves == null || legalMoves.isEmpty()) {
      return null;
    }
    if (legalMoves.size() == 1) {
      return copyMove(legalMoves.get(0));
    }
    if (snap == null || snap.getDiceList() == null || snap.getDiceList().isEmpty()) {
      return copyMove(legalMoves.get(0));
    }

    List<SuperiorPlayerState> players = buildPlayers(snap);
    if (players.isEmpty() || seat < 0 || seat >= players.size()) {
      return copyMove(legalMoves.get(0));
    }

    SuperiorBotDifficulty diff = mapDifficulty(roomDifficulty);
    Map<Integer, List<Integer>> tokensByDiceIndex = groupTokensByDiceIndex(legalMoves);

    int[] bestMove = null;
    double bestScore = Double.NEGATIVE_INFINITY;

    for (Map.Entry<Integer, List<Integer>> entry : tokensByDiceIndex.entrySet()) {
      int diceIndex = entry.getKey();
      if (diceIndex < 0 || diceIndex >= snap.getDiceList().size()) {
        continue;
      }
      int diceValue = snap.getDiceList().get(diceIndex);
      List<Integer> movable = distinctPreserveOrder(entry.getValue());
      if (movable.isEmpty()) {
        continue;
      }

      MoveEvaluation chosen =
          SuperiorBotEngine.chooseBestEvaluation(
              players,
              seat,
              movable,
              diceValue,
              diff,
              null,
              ThreadLocalRandom.current());

      int[] matched = firstMatching(legalMoves, chosen.move.tokenIndex, diceIndex);
      if (matched == null) {
        continue;
      }
      if (bestMove == null || chosen.finalScore > bestScore) {
        bestScore = chosen.finalScore;
        bestMove = matched;
      }
    }

    if (bestMove == null) {
      log.debug("SuperiorBotBridge fallback to first legal move seat={}", seat);
      return copyMove(legalMoves.get(0));
    }
    return bestMove;
  }

  public static SuperiorBotDifficulty mapDifficulty(BotDifficulty roomDifficulty) {
    // Do NOT map HARD→SUPER: SUPER expectimax feels over-aggressive vs humans on live.
    // LudoGame exposes SUPER as an explicit tier; queue bots use HARD/MEDIUM.
    if (roomDifficulty == null) {
      return SuperiorBotDifficulty.HARD;
    }
    return switch (roomDifficulty) {
      case EASY -> SuperiorBotDifficulty.EASY;
      case MEDIUM -> SuperiorBotDifficulty.MEDIUM;
      case HARD -> SuperiorBotDifficulty.HARD;
    };
  }

  static List<SuperiorPlayerState> buildPlayers(GameSnapshot snap) {
    List<String> seatColors = snap.getSeatColors();
    if (seatColors == null || seatColors.isEmpty()) {
      return List.of();
    }
    Map<String, List<Integer>> positions = snap.getTokenPositions();
    boolean[] eliminated = snap.getEliminated();
    boolean[] finished = snap.getFinished();

    List<SuperiorPlayerState> players = new ArrayList<>(seatColors.size());
    for (int seat = 0; seat < seatColors.size(); seat++) {
      String colorName = seatColors.get(seat);
      LudoColor color;
      try {
        color = LudoColor.valueOf(colorName);
      } catch (RuntimeException ex) {
        color = LudoColor.RED;
      }
      int[] tokens = toProgressTokens(color, positions != null ? positions.get(colorName) : null);
      boolean abandoned =
          (eliminated != null && seat < eliminated.length && eliminated[seat])
              || (finished != null && seat < finished.length && finished[seat])
              || tokens.length == 0;
      players.add(new SuperiorPlayerState(color, tokens, abandoned));
    }
    return players;
  }

  static int[] toProgressTokens(LudoColor color, List<Integer> boardPositions) {
    int n = boardPositions != null ? boardPositions.size() : TOKENS_PER_COLOR;
    if (n <= 0) {
      n = TOKENS_PER_COLOR;
    }
    int[] tokens = new int[n];
    for (int i = 0; i < n; i++) {
      int boardPos = JAIL;
      if (boardPositions != null && i < boardPositions.size() && boardPositions.get(i) != null) {
        boardPos = boardPositions.get(i);
      }
      tokens[i] = ProgressCodec.toProgress(color, boardPos);
    }
    return tokens;
  }

  private static Map<Integer, List<Integer>> groupTokensByDiceIndex(List<int[]> legalMoves) {
    Map<Integer, List<Integer>> map = new LinkedHashMap<>();
    for (int[] m : legalMoves) {
      if (m == null || m.length < 2) {
        continue;
      }
      map.computeIfAbsent(m[1], k -> new ArrayList<>()).add(m[0]);
    }
    return map;
  }

  private static List<Integer> distinctPreserveOrder(List<Integer> values) {
    List<Integer> out = new ArrayList<>();
    for (Integer v : values) {
      if (v != null && !out.contains(v)) {
        out.add(v);
      }
    }
    return out;
  }

  /** Prefer first matching legal move when multiple dice indices share the same token. */
  private static int[] firstMatching(List<int[]> legalMoves, int tokenIndex, int diceIndex) {
    for (int[] m : legalMoves) {
      if (m != null && m.length >= 2 && m[0] == tokenIndex && m[1] == diceIndex) {
        return copyMove(m);
      }
    }
    for (int[] m : legalMoves) {
      if (m != null && m.length >= 2 && m[0] == tokenIndex) {
        return copyMove(m);
      }
    }
    return null;
  }

  private static int[] copyMove(int[] move) {
    return new int[] {move[0], move[1]};
  }
}
