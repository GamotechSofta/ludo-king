package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep-copied board used only for lookahead. Never mutates live match state.
 */
public final class SimulationBoard {

  private final List<String> seatColors;
  private final LudoColor[] colors;
  private final boolean[] isBot;
  private final int[][] tokens; // [seat][pawn]
  private final int botSeat;
  private final int leaderSeat;
  private int currentSeat;

  private SimulationBoard(
      List<String> seatColors,
      LudoColor[] colors,
      boolean[] isBot,
      int[][] tokens,
      int botSeat,
      int leaderSeat,
      int currentSeat
  ) {
    this.seatColors = seatColors;
    this.colors = colors;
    this.isBot = isBot;
    this.tokens = tokens;
    this.botSeat = botSeat;
    this.leaderSeat = leaderSeat;
    this.currentSeat = currentSeat;
  }

  public static SimulationBoard fromSnapshot(
      GameSnapshot snap, int botSeat, int leaderSeat
  ) {
    List<String> seatColors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    boolean[] isBot = snap.getIsBot() != null ? snap.getIsBot().clone() : new boolean[seatColors.size()];
    int n = seatColors.size();
    LudoColor[] colors = new LudoColor[n];
    int[][] tokens = new int[n][];
    for (int s = 0; s < n; s++) {
      String name = seatColors.get(s);
      colors[s] = BotBoardMath.parseColor(name);
      List<Integer> pos = all.get(name);
      int len = pos != null ? pos.size() : 4;
      tokens[s] = new int[len];
      for (int p = 0; p < len; p++) {
        tokens[s][p] = pos == null || pos.get(p) == null ? JAIL : pos.get(p);
      }
    }
    return new SimulationBoard(
        List.copyOf(seatColors),
        colors,
        isBot,
        tokens,
        botSeat,
        leaderSeat,
        snap.getCurrentSeatIndex());
  }

  public SimulationBoard deepCopy() {
    int[][] copy = new int[tokens.length][];
    for (int i = 0; i < tokens.length; i++) {
      copy[i] = Arrays.copyOf(tokens[i], tokens[i].length);
    }
    return new SimulationBoard(
        seatColors, colors, isBot.clone(), copy, botSeat, leaderSeat, currentSeat);
  }

  public int botSeat() {
    return botSeat;
  }

  public int leaderSeat() {
    return leaderSeat;
  }

  public int seatCount() {
    return tokens.length;
  }

  public LudoColor color(int seat) {
    return colors[seat];
  }

  public boolean isBotSeat(int seat) {
    return seat >= 0 && seat < isBot.length && isBot[seat];
  }

  public int token(int seat, int pawn) {
    return tokens[seat][pawn];
  }

  public int pawnCount(int seat) {
    return tokens[seat].length;
  }

  public int currentSeat() {
    return currentSeat;
  }

  public void setCurrentSeat(int seat) {
    this.currentSeat = seat;
  }

  public String fingerprint() {
    StringBuilder sb = new StringBuilder(64);
    sb.append(currentSeat).append('|');
    for (int s = 0; s < tokens.length; s++) {
      sb.append(Arrays.toString(tokens[s])).append(';');
    }
    return sb.toString();
  }

  /** Apply a legal move; returns capture victim seat or -1. */
  public int applyMove(SimulationMove move) {
    int seat = move.seat();
    int pawn = move.pawnIndex();
    int to = move.to();
    VictimInfo victim =
        BotBoardMath.findCaptureVictim(seat, to, positionsMap(), seatColors, isBot);
    tokens[seat][pawn] = to;
    if (victim != null) {
      // Send captured pawn to jail (first matching token on that cell)
      int[] enemy = tokens[victim.seat];
      for (int i = 0; i < enemy.length; i++) {
        if (enemy[i] == to) {
          enemy[i] = JAIL;
          break;
        }
      }
      return victim.seat;
    }
    return -1;
  }

  public Map<String, List<Integer>> positionsMap() {
    Map<String, List<Integer>> map = new HashMap<>();
    for (int s = 0; s < seatColors.size(); s++) {
      List<Integer> list = new ArrayList<>(tokens[s].length);
      for (int p : tokens[s]) {
        list.add(p);
      }
      map.put(seatColors.get(s), list);
    }
    return map;
  }

  public List<String> seatColors() {
    return seatColors;
  }

  public boolean[] isBotFlags() {
    return isBot;
  }

  /** Legal destinations for seat with a given die (simplified Ludo rules). */
  public List<SimulationMove> legalMovesForDie(int seat, int dice) {
    List<SimulationMove> out = new ArrayList<>(4);
    LudoColor color = colors[seat];
    if (color == null) {
      return out;
    }
    for (int p = 0; p < tokens[seat].length; p++) {
      int from = tokens[seat][p];
      if (isHome(from)) {
        continue;
      }
      if (isJail(from)) {
        if (dice != 6) {
          continue;
        }
        int to = color.startTile();
        // Cannot land on own block of 2+ on start? Simplified: allow; engine handles stacks
        out.add(new SimulationMove(seat, p, dice, from, to));
        continue;
      }
      int to = BotBoardMath.applySteps(color, from, dice);
      if (to == from && !isJail(from)) {
        // Exact home overshoot stays put in some rules — BotBoardMath advances exact only
      }
      // Exact finish / path: if remaining < dice and not exact, applySteps may stop early —
      // reject if no progress and not home
      if (to == from) {
        continue;
      }
      // Block: cannot land on own 2+ stack unless same cell merge — allow landing on own 1
      if (isMain(to) && !isSafe(to) && countOwnOn(seat, p, to) >= 2) {
        continue;
      }
      out.add(new SimulationMove(seat, p, dice, from, to));
    }
    return out;
  }

  private int countOwnOn(int seat, int excludePawn, int cell) {
    int n = 0;
    for (int i = 0; i < tokens[seat].length; i++) {
      if (i == excludePawn) {
        continue;
      }
      if (tokens[seat][i] == cell) {
        n++;
      }
    }
    return n;
  }

  public int bestRemaining(int seat) {
    LudoColor c = colors[seat];
    int best = Integer.MAX_VALUE;
    for (int pos : tokens[seat]) {
      if (isJail(pos) || isHome(pos)) {
        continue;
      }
      int rem = BotBoardMath.remainingDistance(c, pos);
      if (rem < best) {
        best = rem;
      }
    }
    return best;
  }

  public int finishedCount(int seat) {
    int n = 0;
    for (int pos : tokens[seat]) {
      if (isHome(pos)) {
        n++;
      }
    }
    return n;
  }

  public int progressTotal(int seat) {
    LudoColor c = colors[seat];
    int sum = 0;
    for (int pos : tokens[seat]) {
      sum += BotBoardMath.pawnProgress(c, pos);
    }
    return sum;
  }

  public boolean ownOnSafeOrExit(int seat, int pawn) {
    int pos = tokens[seat][pawn];
    return isSafe(pos) || isExit(pos) || isHome(pos) || isJail(pos);
  }
}
