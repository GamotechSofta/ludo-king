package com.ludo.backend.bot.ai;

import com.ludo.backend.game.LudoColor;

/** Snapshot of one pawn after a board scan. */
public final class ScannedPawn {

  private final int seat;
  private final int pawnIndex;
  private final LudoColor color;
  private final int position;
  private final int progress;
  private final int remainingToHome;
  private final boolean onSafeCell;
  private final boolean inJail;
  private final boolean atHome;
  private final boolean onHomePath;
  private final boolean bot;
  private final boolean leader;

  public ScannedPawn(
      int seat,
      int pawnIndex,
      LudoColor color,
      int position,
      int progress,
      int remainingToHome,
      boolean onSafeCell,
      boolean inJail,
      boolean atHome,
      boolean onHomePath,
      boolean bot,
      boolean leader
  ) {
    this.seat = seat;
    this.pawnIndex = pawnIndex;
    this.color = color;
    this.position = position;
    this.progress = progress;
    this.remainingToHome = remainingToHome;
    this.onSafeCell = onSafeCell;
    this.inJail = inJail;
    this.atHome = atHome;
    this.onHomePath = onHomePath;
    this.bot = bot;
    this.leader = leader;
  }

  public int seat() {
    return seat;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public LudoColor color() {
    return color;
  }

  public int position() {
    return position;
  }

  public int progress() {
    return progress;
  }

  public int remainingToHome() {
    return remainingToHome;
  }

  public boolean onSafeCell() {
    return onSafeCell;
  }

  public boolean inJail() {
    return inJail;
  }

  public boolean atHome() {
    return atHome;
  }

  public boolean onHomePath() {
    return onHomePath;
  }

  public boolean bot() {
    return bot;
  }

  public boolean leader() {
    return leader;
  }
}
