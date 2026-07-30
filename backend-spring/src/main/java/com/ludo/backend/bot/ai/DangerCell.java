package com.ludo.backend.bot.ai;

/**
 * Danger analysis for one playable cell from the defending bot's perspective.
 */
public final class DangerCell {

  private final int position;
  private final int dangerScore;
  private final ThreatLevel level;
  private final int enemyThreatCount;
  private final boolean safeCell;
  private final boolean homePath;
  private final boolean blockProtected;
  private final boolean leaderThreat;
  private final boolean trap;
  private final int futureOneTurn;
  private final int futureTwoTurn;

  public DangerCell(
      int position,
      int dangerScore,
      ThreatLevel level,
      int enemyThreatCount,
      boolean safeCell,
      boolean homePath,
      boolean blockProtected,
      boolean leaderThreat,
      boolean trap,
      int futureOneTurn,
      int futureTwoTurn
  ) {
    this.position = position;
    this.dangerScore = Math.max(0, Math.min(100, dangerScore));
    this.level = level != null ? level : ThreatLevel.fromScore(this.dangerScore);
    this.enemyThreatCount = enemyThreatCount;
    this.safeCell = safeCell;
    this.homePath = homePath;
    this.blockProtected = blockProtected;
    this.leaderThreat = leaderThreat;
    this.trap = trap;
    this.futureOneTurn = futureOneTurn;
    this.futureTwoTurn = futureTwoTurn;
  }

  public int position() {
    return position;
  }

  public int dangerScore() {
    return dangerScore;
  }

  public ThreatLevel level() {
    return level;
  }

  public int enemyThreatCount() {
    return enemyThreatCount;
  }

  public boolean safeCell() {
    return safeCell;
  }

  public boolean homePath() {
    return homePath;
  }

  public boolean blockProtected() {
    return blockProtected;
  }

  public boolean leaderThreat() {
    return leaderThreat;
  }

  public boolean trap() {
    return trap;
  }

  public int futureOneTurn() {
    return futureOneTurn;
  }

  public int futureTwoTurn() {
    return futureTwoTurn;
  }

  @Override
  public String toString() {
    return "Cell " + position + " Danger=" + dangerScore + " " + level
        + (trap ? " TRAP" : "");
  }
}
