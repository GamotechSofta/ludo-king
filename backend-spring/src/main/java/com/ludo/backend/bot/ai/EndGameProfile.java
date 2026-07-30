package com.ludo.backend.bot.ai;

import com.ludo.backend.room.BotDifficulty;
import java.util.Collections;
import java.util.List;

/**
 * Turn-level endgame context reused by scoring, future sim, dice, and personality.
 */
public final class EndGameProfile {

  private final boolean active;
  private final String activationReason;
  private final int botFinished;
  private final int maxOpponentFinished;
  private final int remainingMovesEstimate;
  private final double raceRemaining;
  private final boolean botLeading;
  private final boolean botBehind;
  private final boolean anyExactFinishAvailable;
  private final double homeBias;
  private final double safeBias;
  private final double captureBias;
  private final int futureDepth;
  private final List<EndGameDecision> decisions;

  public EndGameProfile(
      boolean active,
      String activationReason,
      int botFinished,
      int maxOpponentFinished,
      int remainingMovesEstimate,
      double raceRemaining,
      boolean botLeading,
      boolean botBehind,
      boolean anyExactFinishAvailable,
      double homeBias,
      double safeBias,
      double captureBias,
      int futureDepth,
      List<EndGameDecision> decisions
  ) {
    this.active = active;
    this.activationReason = activationReason == null ? "" : activationReason;
    this.botFinished = botFinished;
    this.maxOpponentFinished = maxOpponentFinished;
    this.remainingMovesEstimate = remainingMovesEstimate;
    this.raceRemaining = raceRemaining;
    this.botLeading = botLeading;
    this.botBehind = botBehind;
    this.anyExactFinishAvailable = anyExactFinishAvailable;
    this.homeBias = homeBias;
    this.safeBias = safeBias;
    this.captureBias = captureBias;
    this.futureDepth = futureDepth;
    this.decisions = decisions == null ? List.of() : List.copyOf(decisions);
  }

  public static EndGameProfile inactive() {
    return new EndGameProfile(
        false, "", 0, 0, Integer.MAX_VALUE, 1.0, false, false, false, 1.0, 1.0, 1.0, 3, List.of());
  }

  public boolean active() {
    return active;
  }

  public String activationReason() {
    return activationReason;
  }

  public int botFinished() {
    return botFinished;
  }

  public int maxOpponentFinished() {
    return maxOpponentFinished;
  }

  public int remainingMovesEstimate() {
    return remainingMovesEstimate;
  }

  public double raceRemaining() {
    return raceRemaining;
  }

  public boolean botLeading() {
    return botLeading;
  }

  public boolean botBehind() {
    return botBehind;
  }

  public boolean anyExactFinishAvailable() {
    return anyExactFinishAvailable;
  }

  public double homeBias() {
    return homeBias;
  }

  public double safeBias() {
    return safeBias;
  }

  public double captureBias() {
    return captureBias;
  }

  public int futureDepth() {
    return futureDepth;
  }

  public List<EndGameDecision> decisions() {
    return Collections.unmodifiableList(decisions);
  }

  public EndGameDecision forMove(MoveCandidate c) {
    if (c == null || decisions.isEmpty()) {
      return null;
    }
    for (EndGameDecision d : decisions) {
      if (d.move() == c
          || (d.move() != null
              && d.move().pawnIndex() == c.pawnIndex()
              && d.move().from() == c.from()
              && d.move().to() == c.to()
              && d.move().diceValue() == c.diceValue())) {
        return d;
      }
    }
    return null;
  }

  public boolean appliesTo(BotDifficulty difficulty) {
    return active && difficulty == BotDifficulty.HARD;
  }

  public String debugHeader() {
    return "EndGame Activated Reason " + activationReason;
  }

  /** Soft personality finish-blend factor when endgame is active (0–1). */
  public double personalityFinishBlend() {
    if (!active) {
      return 0;
    }
    if (botFinished >= 3 || anyExactFinishAvailable) {
      return 0.65;
    }
    if (botBehind) {
      return 0.40;
    }
    return 0.55;
  }
}
