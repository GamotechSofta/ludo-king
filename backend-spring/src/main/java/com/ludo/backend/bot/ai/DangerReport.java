package com.ludo.backend.bot.ai;

/**
 * Per-move escape / trap summary used by scoring and debug logs.
 */
public final class DangerReport {

  private final int pawnIndex;
  private final int from;
  private final int to;
  private final int currentDanger;
  private final int destinationDanger;
  private final boolean escape;
  private final boolean trap;
  private final boolean saferRoute;
  private final int scoreDelta;

  public DangerReport(
      int pawnIndex,
      int from,
      int to,
      int currentDanger,
      int destinationDanger,
      boolean escape,
      boolean trap,
      boolean saferRoute,
      int scoreDelta
  ) {
    this.pawnIndex = pawnIndex;
    this.from = from;
    this.to = to;
    this.currentDanger = currentDanger;
    this.destinationDanger = destinationDanger;
    this.escape = escape;
    this.trap = trap;
    this.saferRoute = saferRoute;
    this.scoreDelta = scoreDelta;
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

  public int currentDanger() {
    return currentDanger;
  }

  public int destinationDanger() {
    return destinationDanger;
  }

  public boolean escape() {
    return escape;
  }

  public boolean trap() {
    return trap;
  }

  public boolean saferRoute() {
    return saferRoute;
  }

  public int scoreDelta() {
    return scoreDelta;
  }

  @Override
  public String toString() {
    return "Pawn "
        + pawnIndex
        + " Current Danger "
        + currentDanger
        + " Destination "
        + destinationDanger
        + (escape ? " Escape YES" : "")
        + (trap ? " TRAP" : "")
        + " delta="
        + scoreDelta;
  }
}
