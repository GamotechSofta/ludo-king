package com.ludo.backend.bot.ai;

/**
 * One publicly observable human action (roll or move).
 * Never stores hidden / client-only state.
 */
public final class BehaviorEvent {

  public enum Kind {
    ROLL,
    MOVE
  }

  private final Kind kind;
  private final int dice;
  private final int pawnIndex;
  private final int from;
  private final int to;
  private final boolean capture;
  private final boolean safeLand;
  private final boolean homePriority;
  private final boolean opening;
  private final boolean escape;
  private final boolean risky;

  public BehaviorEvent(
      Kind kind,
      int dice,
      int pawnIndex,
      int from,
      int to,
      boolean capture,
      boolean safeLand,
      boolean homePriority,
      boolean opening,
      boolean escape,
      boolean risky
  ) {
    this.kind = kind == null ? Kind.MOVE : kind;
    this.dice = dice;
    this.pawnIndex = pawnIndex;
    this.from = from;
    this.to = to;
    this.capture = capture;
    this.safeLand = safeLand;
    this.homePriority = homePriority;
    this.opening = opening;
    this.escape = escape;
    this.risky = risky;
  }

  public static BehaviorEvent roll(int dice) {
    return new BehaviorEvent(Kind.ROLL, dice, -1, -1, -1, false, false, false, false, false, false);
  }

  public Kind kind() {
    return kind;
  }

  public int dice() {
    return dice;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public int from() {
    return from;
  }

  public int to() {
    return to;
  }

  public boolean capture() {
    return capture;
  }

  public boolean safeLand() {
    return safeLand;
  }

  public boolean homePriority() {
    return homePriority;
  }

  public boolean opening() {
    return opening;
  }

  public boolean escape() {
    return escape;
  }

  public boolean risky() {
    return risky;
  }
}
