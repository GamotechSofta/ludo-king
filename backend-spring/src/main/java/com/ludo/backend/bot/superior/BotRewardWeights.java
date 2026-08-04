package com.ludo.backend.bot.superior;

/** Reward / penalty weights for {@link SuperiorBotEngine} (defaults match LudoGame BotModels). */
public final class BotRewardWeights {

  public double winGame = 10_000.0;
  public double tokenHome = 2_200.0;
  public double captureBase = 2_500.0;
  /** Luzo BotModels defaults. */
  public double escapeThreat = 650.0;
  public double createBlockade = 400.0;
  public double breakOpponentBlockade = 350.0;
  public double enterHomePath = 500.0;
  public double landSafe = 220.0;
  public double saveThreatened = 280.0;
  public double createCaptureThreat = 220.0;
  public double maintainBlockade = 150.0;
  public double leaveBase = 120.0;
  public double progressPerStep = 12.0;
  public double exposeToCapture = -700.0;
  public double multiOpponentDanger = -900.0;
  public double breakOwnBlockade = -300.0;
  public double ignoreGuaranteedCapture = -2_000.0;
  public double leaveSafetyIntoDanger = -250.0;
  public double twoPlayerAttackMultiplier = 1.60;
  public double huntReward = 750.0;
  public double dangerProbabilityPenalty = -900.0;
  public double tokenDiversityReward = 180.0;
  public int huntHorizonTurns = 4;
  public int expectimaxDepth = 2;
  public long maxDecisionMillis = 300;

  public BotRewardWeights() {}
}
