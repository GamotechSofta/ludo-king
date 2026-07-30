package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot End Game Master Strategy Engine. */
@Component
public class EndGameConfig {

  private final boolean enabled;
  private final int activationRemainingMoves;
  private final int futureDepth;
  private final double safeBias;
  private final double homeBias;
  private final double captureBias;
  private final double raceRemainingThreshold;

  public EndGameConfig(
      @Value("${ludo.bot.endgame.enabled:true}") boolean enabled,
      @Value("${ludo.bot.endgame.activationRemainingMoves:20}") int activationRemainingMoves,
      @Value("${ludo.bot.endgame.futureDepth:4}") int futureDepth,
      @Value("${ludo.bot.endgame.safeBias:0.70}") double safeBias,
      @Value("${ludo.bot.endgame.homeBias:1.40}") double homeBias,
      @Value("${ludo.bot.endgame.captureBias:0.35}") double captureBias,
      @Value("${ludo.bot.endgame.raceRemainingThreshold:0.25}") double raceRemainingThreshold
  ) {
    this.enabled = enabled;
    this.activationRemainingMoves = Math.max(4, Math.min(40, activationRemainingMoves));
    this.futureDepth = Math.max(2, Math.min(4, futureDepth));
    this.safeBias = Math.max(0.3, Math.min(1.5, safeBias));
    this.homeBias = Math.max(0.8, Math.min(2.0, homeBias));
    this.captureBias = Math.max(0.1, Math.min(1.0, captureBias));
    this.raceRemainingThreshold =
        Math.max(0.10, Math.min(0.40, raceRemainingThreshold));
  }

  public boolean enabled() {
    return enabled;
  }

  public int activationRemainingMoves() {
    return activationRemainingMoves;
  }

  public int futureDepth() {
    return futureDepth;
  }

  public double safeBias() {
    return safeBias;
  }

  public double homeBias() {
    return homeBias;
  }

  public double captureBias() {
    return captureBias;
  }

  public double raceRemainingThreshold() {
    return raceRemainingThreshold;
  }
}
