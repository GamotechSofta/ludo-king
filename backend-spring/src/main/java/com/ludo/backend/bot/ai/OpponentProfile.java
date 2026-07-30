package com.ludo.backend.bot.ai;

import com.ludo.backend.game.LudoColor;

/**
 * Full analysis of one seat for the current bot turn.
 */
public final class OpponentProfile {

  private final int seat;
  private final String colorName;
  private final LudoColor color;
  private final boolean bot;
  private final int finishedPawns;
  private final int activePawns;
  private final int jailPawns;
  private final int safePawns;
  private final int totalProgress;
  private final int averageProgress;
  private final int leaderPawnIndex;
  private final int leaderPawnProgress;
  private final int mostDangerousPawnIndex;
  private final int mostExposedPawnIndex;
  private final int weakestPawnIndex;
  private final LeaderScore leaderScore;
  private final int threatScore;
  private final PlayerThreat threat;
  private final double winningProbability;
  private final boolean weak;
  private final boolean winningCritical;
  private final boolean futureLeaderRisk;
  private final PlayStyle playStyle;
  private final boolean preferredTarget;
  private final boolean ignoreForAttack;

  public OpponentProfile(
      int seat,
      String colorName,
      LudoColor color,
      boolean bot,
      int finishedPawns,
      int activePawns,
      int jailPawns,
      int safePawns,
      int totalProgress,
      int averageProgress,
      int leaderPawnIndex,
      int leaderPawnProgress,
      int mostDangerousPawnIndex,
      int mostExposedPawnIndex,
      int weakestPawnIndex,
      LeaderScore leaderScore,
      int threatScore,
      PlayerThreat threat,
      double winningProbability,
      boolean weak,
      boolean winningCritical,
      boolean futureLeaderRisk,
      PlayStyle playStyle,
      boolean preferredTarget,
      boolean ignoreForAttack
  ) {
    this.seat = seat;
    this.colorName = colorName;
    this.color = color;
    this.bot = bot;
    this.finishedPawns = finishedPawns;
    this.activePawns = activePawns;
    this.jailPawns = jailPawns;
    this.safePawns = safePawns;
    this.totalProgress = totalProgress;
    this.averageProgress = averageProgress;
    this.leaderPawnIndex = leaderPawnIndex;
    this.leaderPawnProgress = leaderPawnProgress;
    this.mostDangerousPawnIndex = mostDangerousPawnIndex;
    this.mostExposedPawnIndex = mostExposedPawnIndex;
    this.weakestPawnIndex = weakestPawnIndex;
    this.leaderScore = leaderScore;
    this.threatScore = threatScore;
    this.threat = threat;
    this.winningProbability = winningProbability;
    this.weak = weak;
    this.winningCritical = winningCritical;
    this.futureLeaderRisk = futureLeaderRisk;
    this.playStyle = playStyle;
    this.preferredTarget = preferredTarget;
    this.ignoreForAttack = ignoreForAttack;
  }

  public int seat() {
    return seat;
  }

  public String colorName() {
    return colorName;
  }

  public LudoColor color() {
    return color;
  }

  public boolean bot() {
    return bot;
  }

  public int finishedPawns() {
    return finishedPawns;
  }

  public int activePawns() {
    return activePawns;
  }

  public int jailPawns() {
    return jailPawns;
  }

  public int safePawns() {
    return safePawns;
  }

  public int totalProgress() {
    return totalProgress;
  }

  public int averageProgress() {
    return averageProgress;
  }

  public int leaderPawnIndex() {
    return leaderPawnIndex;
  }

  public int leaderPawnProgress() {
    return leaderPawnProgress;
  }

  public int mostDangerousPawnIndex() {
    return mostDangerousPawnIndex;
  }

  public int mostExposedPawnIndex() {
    return mostExposedPawnIndex;
  }

  public int weakestPawnIndex() {
    return weakestPawnIndex;
  }

  public LeaderScore leaderScore() {
    return leaderScore;
  }

  public int threatScore() {
    return threatScore;
  }

  public PlayerThreat threat() {
    return threat;
  }

  public double winningProbability() {
    return winningProbability;
  }

  public boolean weak() {
    return weak;
  }

  public boolean winningCritical() {
    return winningCritical;
  }

  public boolean futureLeaderRisk() {
    return futureLeaderRisk;
  }

  public PlayStyle playStyle() {
    return playStyle;
  }

  public boolean preferredTarget() {
    return preferredTarget;
  }

  public boolean ignoreForAttack() {
    return ignoreForAttack;
  }

  public String debugLine() {
    return "Player "
        + seat
        + " Leader Score "
        + (leaderScore != null ? leaderScore.total() : 0)
        + " Threat "
        + threat
        + " Winning Probability "
        + Math.round(winningProbability * 100)
        + "%"
        + (preferredTarget ? " TARGET" : "")
        + (ignoreForAttack ? " IGNORE" : "")
        + (winningCritical ? " CRITICAL_WIN" : "");
  }
}
