package com.ludo.backend.bot.ai;

import java.util.List;

/** Aggregated counters derived from visible human decisions. */
public final class PlayerStatistics {

  private int rolls;
  private int moves;
  private int captures;
  private int safeLands;
  private int homeMoves;
  private int openings;
  private int escapes;
  private int riskyMoves;
  private final int[] pawnMoves = new int[4];
  private int sixes;

  public static PlayerStatistics from(List<BehaviorEvent> events) {
    PlayerStatistics s = new PlayerStatistics();
    if (events == null) {
      return s;
    }
    for (BehaviorEvent e : events) {
      if (e.kind() == BehaviorEvent.Kind.ROLL) {
        s.rolls++;
        if (e.dice() == 6) {
          s.sixes++;
        }
        continue;
      }
      s.moves++;
      if (e.pawnIndex() >= 0 && e.pawnIndex() < 4) {
        s.pawnMoves[e.pawnIndex()]++;
      }
      if (e.capture()) {
        s.captures++;
      }
      if (e.safeLand()) {
        s.safeLands++;
      }
      if (e.homePriority()) {
        s.homeMoves++;
      }
      if (e.opening()) {
        s.openings++;
      }
      if (e.escape()) {
        s.escapes++;
      }
      if (e.risky()) {
        s.riskyMoves++;
      }
    }
    return s;
  }

  public int rolls() {
    return rolls;
  }

  public int moves() {
    return moves;
  }

  public int captures() {
    return captures;
  }

  public int safeLands() {
    return safeLands;
  }

  public int homeMoves() {
    return homeMoves;
  }

  public int openings() {
    return openings;
  }

  public int escapes() {
    return escapes;
  }

  public int riskyMoves() {
    return riskyMoves;
  }

  public int sixes() {
    return sixes;
  }

  public int[] pawnMoves() {
    return pawnMoves.clone();
  }

  public int favouritePawn() {
    int best = 0;
    int idx = 0;
    for (int i = 0; i < pawnMoves.length; i++) {
      if (pawnMoves[i] > best) {
        best = pawnMoves[i];
        idx = i;
      }
    }
    return idx;
  }

  public double captureRate() {
    return moves == 0 ? 0 : captures / (double) moves;
  }

  public double safeRate() {
    return moves == 0 ? 0 : safeLands / (double) moves;
  }

  public double homeRate() {
    return moves == 0 ? 0 : homeMoves / (double) moves;
  }

  public double escapeRate() {
    return moves == 0 ? 0 : escapes / (double) moves;
  }

  public double riskRate() {
    return moves == 0 ? 0 : riskyMoves / (double) moves;
  }

  public double openingRate() {
    return moves == 0 ? 0 : openings / (double) moves;
  }
}
