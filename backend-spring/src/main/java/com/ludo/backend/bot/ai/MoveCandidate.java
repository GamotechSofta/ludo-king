package com.ludo.backend.bot.ai;

/**
 * One legal move option before scoring.
 *
 * <p>Immutable value object — safe to share across future AI modules
 * (Danger Map, Future Simulation, Adaptive Difficulty).
 */
public final class MoveCandidate {

  private final int[] rawMove;
  private final int pawnIndex;
  private final int diceValue;
  private final int diceIndex;
  private final int from;
  private final int to;
  private final MoveType moveType;
  private final boolean underThreatAtFrom;
  private final int threatCountAtTo;
  private final boolean capture;
  private final int victimSeat;
  private final boolean victimIsLeader;
  private final int victimRemaining;
  private final boolean victimJustOut;
  private final int pawnValue;
  private final boolean createsBlock;
  private final boolean blockProtectsAdvanced;

  public MoveCandidate(
      int[] rawMove,
      int pawnIndex,
      int diceValue,
      int diceIndex,
      int from,
      int to,
      MoveType moveType,
      boolean underThreatAtFrom,
      int threatCountAtTo,
      boolean capture,
      int victimSeat,
      boolean victimIsLeader,
      int victimRemaining,
      boolean victimJustOut,
      int pawnValue,
      boolean createsBlock,
      boolean blockProtectsAdvanced
  ) {
    this.rawMove = rawMove;
    this.pawnIndex = pawnIndex;
    this.diceValue = diceValue;
    this.diceIndex = diceIndex;
    this.from = from;
    this.to = to;
    this.moveType = moveType;
    this.underThreatAtFrom = underThreatAtFrom;
    this.threatCountAtTo = threatCountAtTo;
    this.capture = capture;
    this.victimSeat = victimSeat;
    this.victimIsLeader = victimIsLeader;
    this.victimRemaining = victimRemaining;
    this.victimJustOut = victimJustOut;
    this.pawnValue = pawnValue;
    this.createsBlock = createsBlock;
    this.blockProtectsAdvanced = blockProtectsAdvanced;
  }

  public int[] rawMove() {
    return rawMove;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public int diceValue() {
    return diceValue;
  }

  public int diceIndex() {
    return diceIndex;
  }

  public int from() {
    return from;
  }

  public int to() {
    return to;
  }

  public MoveType moveType() {
    return moveType;
  }

  public boolean underThreatAtFrom() {
    return underThreatAtFrom;
  }

  public int threatCountAtTo() {
    return threatCountAtTo;
  }

  public boolean capture() {
    return capture;
  }

  public int victimSeat() {
    return victimSeat;
  }

  public boolean victimIsLeader() {
    return victimIsLeader;
  }

  public int victimRemaining() {
    return victimRemaining;
  }

  public boolean victimJustOut() {
    return victimJustOut;
  }

  public int pawnValue() {
    return pawnValue;
  }

  public boolean createsBlock() {
    return createsBlock;
  }

  public boolean blockProtectsAdvanced() {
    return blockProtectsAdvanced;
  }
}
