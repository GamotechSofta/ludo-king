package com.ludo.backend.bot.ai;

/**
 * Immutable snapshot of one bot pawn used by the Pawn Value Engine.
 */
public final class PawnState {

  private final int pawnIndex;
  private final int position;
  private final int progress;
  private final int remaining;
  private final boolean jail;
  private final boolean home;
  private final boolean safe;
  private final boolean exit;
  private final boolean leader;
  private final boolean nearHome;
  private final boolean finalStretch;
  private final boolean advanced;
  private final boolean justReleased;
  private final int dangerScore;
  private final int finishedSiblings;

  public PawnState(
      int pawnIndex,
      int position,
      int progress,
      int remaining,
      boolean jail,
      boolean home,
      boolean safe,
      boolean exit,
      boolean leader,
      boolean nearHome,
      boolean finalStretch,
      boolean advanced,
      boolean justReleased,
      int dangerScore,
      int finishedSiblings
  ) {
    this.pawnIndex = pawnIndex;
    this.position = position;
    this.progress = progress;
    this.remaining = remaining;
    this.jail = jail;
    this.home = home;
    this.safe = safe;
    this.exit = exit;
    this.leader = leader;
    this.nearHome = nearHome;
    this.finalStretch = finalStretch;
    this.advanced = advanced;
    this.justReleased = justReleased;
    this.dangerScore = dangerScore;
    this.finishedSiblings = finishedSiblings;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public int position() {
    return position;
  }

  public int progress() {
    return progress;
  }

  public int remaining() {
    return remaining;
  }

  public boolean jail() {
    return jail;
  }

  public boolean home() {
    return home;
  }

  public boolean safe() {
    return safe;
  }

  public boolean exit() {
    return exit;
  }

  public boolean leader() {
    return leader;
  }

  public boolean nearHome() {
    return nearHome;
  }

  public boolean finalStretch() {
    return finalStretch;
  }

  public boolean advanced() {
    return advanced;
  }

  public boolean justReleased() {
    return justReleased;
  }

  public int dangerScore() {
    return dangerScore;
  }

  public int finishedSiblings() {
    return finishedSiblings;
  }
}
