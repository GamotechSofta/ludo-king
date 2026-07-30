package com.ludo.backend.bot.ai;

/** One move applied inside a simulation (never touches live match state). */
public final class SimulationMove {

  private final int seat;
  private final int pawnIndex;
  private final int dice;
  private final int from;
  private final int to;

  public SimulationMove(int seat, int pawnIndex, int dice, int from, int to) {
    this.seat = seat;
    this.pawnIndex = pawnIndex;
    this.dice = dice;
    this.from = from;
    this.to = to;
  }

  public int seat() {
    return seat;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public int dice() {
    return dice;
  }

  public int from() {
    return from;
  }

  public int to() {
    return to;
  }
}
