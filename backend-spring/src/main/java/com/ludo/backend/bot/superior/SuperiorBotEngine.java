package com.ludo.backend.bot.superior;

import static com.ludo.backend.bot.superior.ProgressCodec.FINISHED_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.HOME_LANE_LAST_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.HOME_LANE_START_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.MAIN_PATH_LAST_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.absoluteMainTile;
import static com.ludo.backend.bot.superior.ProgressCodec.canMoveToken;
import static com.ludo.backend.bot.superior.ProgressCodec.isSafeMainProgress;

import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Superior Ludo bot decision engine (port of LudoGame SuperiorBotEngine).
 *
 * <p>Strength comes only from evaluating legal moves — dice are never manipulated.
 *
 * <p>Collision / safe cells use absolute main-path tiles (0-51) + {@code BoardConstants.isSafe}.
 */
public final class SuperiorBotEngine {

  private static final Logger log = LoggerFactory.getLogger(SuperiorBotEngine.class);

  private static volatile BotRewardWeights weights = new BotRewardWeights();
  private static volatile SuperiorBotDifficulty difficulty = SuperiorBotDifficulty.SUPER;

  private SuperiorBotEngine() {}

  public static BotRewardWeights getWeights() {
    return weights;
  }

  public static void setWeights(BotRewardWeights next) {
    weights = next != null ? next : new BotRewardWeights();
  }

  public static SuperiorBotDifficulty getDifficulty() {
    return difficulty;
  }

  public static void setDifficulty(SuperiorBotDifficulty next) {
    difficulty = next != null ? next : SuperiorBotDifficulty.SUPER;
  }

  public static int chooseToken(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue) {
    return chooseToken(players, playerIndex, movableTokenIndexes, diceValue, null, null, null);
  }

  public static int chooseToken(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue,
      SuperiorBotDifficulty difficultyOverride,
      BotRewardWeights weightsOverride,
      Random random) {
    MoveEvaluation best =
        chooseBestEvaluation(
            players,
            playerIndex,
            movableTokenIndexes,
            diceValue,
            difficultyOverride,
            weightsOverride,
            random);
    return best.move.tokenIndex;
  }

  /**
   * Full evaluation + priority selection (same as chooseToken, but returns the scored move).
   * Useful when comparing across multiple dice values.
   */
  public static MoveEvaluation chooseBestEvaluation(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue,
      SuperiorBotDifficulty difficultyOverride,
      BotRewardWeights weightsOverride,
      Random random) {
    if (movableTokenIndexes == null || movableTokenIndexes.isEmpty()) {
      return new MoveEvaluation(
          new CandidateMove(0, -1, -1),
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          0,
          "No movable tokens");
    }
    Random rng = random != null ? random : ThreadLocalRandom.current();
    if (movableTokenIndexes.size() == 1) {
      CandidateMove only =
          candidateMove(players.get(playerIndex), movableTokenIndexes.get(0), diceValue);
      return new MoveEvaluation(only, 0, 0, 0, 0, 0, 0, 0, 0, "Only legal token");
    }

    SuperiorBotDifficulty activeDifficulty =
        difficultyOverride != null ? difficultyOverride : difficulty;
    BotRewardWeights activeWeights = weightsOverride != null ? weightsOverride : weights;
    long startedAt = System.nanoTime();

    List<MoveEvaluation> evaluations =
        switch (activeDifficulty) {
          case EASY ->
              evaluateEasy(
                  players, playerIndex, movableTokenIndexes, diceValue, activeWeights, rng);
          case MEDIUM ->
              evaluateMedium(players, playerIndex, movableTokenIndexes, diceValue, activeWeights);
          case HARD, EXPERT, SUPER ->
              evaluateStrategic(
                  players,
                  playerIndex,
                  movableTokenIndexes,
                  diceValue,
                  activeWeights,
                  activeDifficulty != SuperiorBotDifficulty.HARD,
                  rng);
        };

    MoveEvaluation best =
        selectPriorityMove(players, playerIndex, diceValue, evaluations, rng);
    long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

    if (log.isDebugEnabled()) {
      SuperiorPlayerState player = players.get(playerIndex);
      log.debug(
          "BOT_DECISION color={} dice={} difficulty={} selected=T{} score={} reason=\"{}\" decisionTimeMs={}",
          player.color,
          diceValue,
          activeDifficulty,
          best.move.tokenIndex + 1,
          Math.round(best.finalScore),
          best.reason,
          elapsedMs);
      evaluations.stream()
          .sorted((a, b) -> Double.compare(b.finalScore, a.finalScore))
          .forEach(
              evaluation ->
                  log.debug(
                      "candidate=T{} progress={} safety={} attack={} risk={} future={} finalScore={} reason=\"{}\"",
                      evaluation.move.tokenIndex + 1,
                      Math.round(evaluation.progressReward),
                      Math.round(evaluation.safetyReward),
                      Math.round(evaluation.attackReward),
                      Math.round(evaluation.riskPenalty),
                      Math.round(evaluation.futureValue),
                      Math.round(evaluation.finalScore),
                      evaluation.reason));
    }

    return best;
  }

  /**
   * Hard priorities: (1) match-winning move, then best expected value among the rest.
   */
  private static MoveEvaluation selectPriorityMove(
      List<SuperiorPlayerState> players,
      int playerIndex,
      int diceValue,
      List<MoveEvaluation> evaluations,
      Random random) {
    List<MoveEvaluation> winningMoves = new ArrayList<>();
    for (MoveEvaluation evaluation : evaluations) {
      List<SuperiorPlayerState> resulting =
          simulateMove(players, playerIndex, evaluation.move, diceValue);
      if (tokensAllFinished(resulting.get(playerIndex))) {
        winningMoves.add(evaluation);
      }
    }
    if (!winningMoves.isEmpty()) {
      return tieBreak(winningMoves, random);
    }

    return tieBreak(evaluations, random);
  }

  private static List<MoveEvaluation> evaluateEasy(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue,
      BotRewardWeights weights,
      Random random) {
    List<MoveEvaluation> out = new ArrayList<>(movableTokenIndexes.size());
    for (int tokenIndex : movableTokenIndexes) {
      CandidateMove move = candidateMove(players.get(playerIndex), tokenIndex, diceValue);
      double progress = progressReward(move.fromProgress, move.toProgress, weights);
      double noise = random.nextDouble() * 5.0;
      out.add(
          new MoveEvaluation(
              move,
              progress,
              progress,
              0.0,
              0.0,
              0.0,
              0.0,
              noise,
              progress + noise,
              "Easy random-biased progress"));
    }
    return out;
  }

  private static List<MoveEvaluation> evaluateMedium(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue,
      BotRewardWeights weights) {
    List<MoveEvaluation> out = new ArrayList<>(movableTokenIndexes.size());
    for (int tokenIndex : movableTokenIndexes) {
      CandidateMove move = candidateMove(players.get(playerIndex), tokenIndex, diceValue);
      List<SuperiorPlayerState> resultingPlayers =
          simulateMove(players, playerIndex, move, diceValue);
      double immediate =
          immediateReward(players, resultingPlayers, playerIndex, move, weights);
      double progress = progressReward(move.fromProgress, move.toProgress, weights);
      double safety = basicSafety(players, playerIndex, move, weights);
      double score = immediate + progress + safety;
      out.add(
          new MoveEvaluation(
              move,
              immediate,
              progress,
              safety,
              0.0,
              0.0,
              0.0,
              0.0,
              score,
              summarizeReason(immediate, progress, safety, 0.0, 0.0, 0.0, 0.0)));
    }
    return out;
  }

  private static List<MoveEvaluation> evaluateStrategic(
      List<SuperiorPlayerState> players,
      int playerIndex,
      List<Integer> movableTokenIndexes,
      int diceValue,
      BotRewardWeights weights,
      boolean useExpectimax,
      Random random) {
    long deadlineNs = System.nanoTime() + weights.maxDecisionMillis * 1_000_000L;
    int activeHumanOrBotCount = 0;
    for (SuperiorPlayerState p : players) {
      if (!p.abandoned) {
        activeHumanOrBotCount++;
      }
    }
    boolean twoPlayer = activeHumanOrBotCount == 2;
    double[] threatByOpponent = new double[players.size()];
    for (int index = 0; index < players.size(); index++) {
      if (index == playerIndex || players.get(index).abandoned) {
        threatByOpponent[index] = 0.0;
      } else {
        threatByOpponent[index] = opponentThreat(players, playerIndex, index);
      }
    }

    List<MoveEvaluation> out = new ArrayList<>(movableTokenIndexes.size());
    for (int tokenIndex : movableTokenIndexes) {
      CandidateMove move = candidateMove(players.get(playerIndex), tokenIndex, diceValue);
      List<SuperiorPlayerState> resultingPlayers =
          simulateMove(players, playerIndex, move, diceValue);
      List<int[]> captures = captureTargets(players, playerIndex, move);

      double immediate =
          immediateReward(players, resultingPlayers, playerIndex, move, weights);
      double progress = progressReward(move.fromProgress, move.toProgress, weights);
      double safety = safetyReward(players, playerIndex, move, weights);
      double attack =
          attackReward(
                  players,
                  playerIndex,
                  move,
                  captures,
                  resultingPlayers,
                  threatByOpponent,
                  twoPlayer,
                  weights)
              + huntReward(players, playerIndex, move, threatByOpponent, weights);
      double strategic =
          strategicReward(players, playerIndex, move, resultingPlayers, weights);
      double risk =
          captureRiskPenalty(resultingPlayers, playerIndex, move.tokenIndex, weights);
      double future = 0.0;
      if (useExpectimax
          && System.nanoTime() < deadlineNs
          && weights.expectimaxDepth >= 2) {
        future =
            expectimaxOpponentPly(resultingPlayers, playerIndex, weights, deadlineNs);
      }

      double finalScore = immediate + progress + safety + attack + strategic + future + risk;
      out.add(
          new MoveEvaluation(
              move,
              immediate,
              progress,
              safety,
              attack,
              strategic,
              risk,
              future,
              finalScore,
              summarizeReason(
                  immediate, progress, safety, attack, strategic, risk, future)));
    }
    return out;
  }

  private static CandidateMove candidateMove(
      SuperiorPlayerState player, int tokenIndex, int diceValue) {
    int from = player.tokens[tokenIndex];
    int to = from == -1 ? 0 : from + diceValue;
    return new CandidateMove(tokenIndex, from, to);
  }

  static List<SuperiorPlayerState> simulateMove(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      int diceValue) {
    List<SuperiorPlayerState> mutable = new ArrayList<>(players.size());
    for (SuperiorPlayerState p : players) {
      mutable.add(p.copy());
    }
    SuperiorPlayerState active = mutable.get(playerIndex);
    int[] tokens = active.tokens.clone();
    tokens[move.tokenIndex] = move.toProgress;
    mutable.set(playerIndex, active.withTokens(tokens));

    if (move.toProgress >= 0 && move.toProgress <= MAIN_PATH_LAST_PROGRESS) {
      int landingTile = absoluteMainTile(active.color, move.toProgress);
      if (landingTile >= 0 && !isSafeMainProgress(active.color, move.toProgress)) {
        for (int opponentIndex = 0; opponentIndex < mutable.size(); opponentIndex++) {
          if (opponentIndex == playerIndex || mutable.get(opponentIndex).abandoned) {
            continue;
          }
          SuperiorPlayerState opponent = mutable.get(opponentIndex);
          int[] adjusted = opponent.tokens.clone();
          boolean changed = false;
          for (int tokenIndex = 0; tokenIndex < adjusted.length; tokenIndex++) {
            int progress = adjusted[tokenIndex];
            if (progress >= 0
                && progress <= MAIN_PATH_LAST_PROGRESS
                && absoluteMainTile(opponent.color, progress) == landingTile) {
              adjusted[tokenIndex] = -1;
              changed = true;
            }
          }
          if (changed) {
            mutable.set(opponentIndex, opponent.withTokens(adjusted));
          }
        }
      }
    }

    return mutable;
  }

  private static double immediateReward(
      List<SuperiorPlayerState> before,
      List<SuperiorPlayerState> after,
      int playerIndex,
      CandidateMove move,
      BotRewardWeights weights) {
    double reward = 0.0;
    if (tokensAllFinished(after.get(playerIndex))) {
      return weights.winGame;
    }
    if (move.toProgress == FINISHED_PROGRESS) {
      reward += weights.tokenHome;
    }
    if (move.fromProgress == -1 && move.toProgress == 0) {
      int activeBefore = 0;
      for (int t : before.get(playerIndex).tokens) {
        if (t >= 0 && t < FINISHED_PROGRESS) {
          activeBefore++;
        }
      }
      reward +=
          switch (activeBefore) {
            case 0 -> weights.leaveBase * 2.2;
            case 1 -> weights.leaveBase * 1.4;
            case 2 -> weights.leaveBase * 0.7;
            default -> weights.leaveBase * -0.4;
          };
    }
    if (move.toProgress >= HOME_LANE_START_PROGRESS
        && move.toProgress <= HOME_LANE_LAST_PROGRESS) {
      reward += weights.enterHomePath;
    }
    return reward;
  }

  private static double progressReward(int from, int to, BotRewardWeights weights) {
    if (to < 0 || to > FINISHED_PROGRESS) {
      return 0.0;
    }
    int steps = from < 0 ? 1 : Math.max(0, to - from);
    double ratio = (double) to / (double) FINISHED_PROGRESS;
    double multiplier;
    if (ratio < 0.25) {
      multiplier = 1.0;
    } else if (ratio < 0.50) {
      multiplier = 1.2;
    } else if (ratio < 0.75) {
      multiplier = 1.5;
    } else if (ratio < 0.90) {
      multiplier = 2.0;
    } else {
      multiplier = 3.0;
    }
    return steps * weights.progressPerStep * multiplier;
  }

  private static double basicSafety(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      BotRewardWeights weights) {
    if (move.toProgress >= HOME_LANE_START_PROGRESS
        && move.toProgress <= FINISHED_PROGRESS) {
      return weights.enterHomePath * 0.5;
    }
    if (move.toProgress < 0 || move.toProgress > MAIN_PATH_LAST_PROGRESS) {
      return 0.0;
    }
    LudoColor color = players.get(playerIndex).color;
    int landingTile = absoluteMainTile(color, move.toProgress);
    if (isSafeMainProgress(color, move.toProgress)) {
      return weights.landSafe;
    }
    int attackers = countAttackers(players, playerIndex, landingTile);
    if (attackers >= 2) {
      return weights.multiOpponentDanger * 0.5;
    }
    if (attackers == 1) {
      return weights.exposeToCapture * 0.5;
    }
    return 0.0;
  }

  private static double safetyReward(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      BotRewardWeights weights) {
    double reward = 0.0;
    SuperiorPlayerState player = players.get(playerIndex);
    boolean wasThreatened =
        move.fromProgress >= 0
            && move.fromProgress <= MAIN_PATH_LAST_PROGRESS
            && !isSafeMainProgress(player.color, move.fromProgress)
            && countAttackers(
                    players, playerIndex, absoluteMainTile(player.color, move.fromProgress))
                > 0;

    if (move.toProgress >= HOME_LANE_START_PROGRESS
        && move.toProgress <= HOME_LANE_LAST_PROGRESS) {
      reward += weights.enterHomePath;
    }
    if (move.toProgress >= 0 && move.toProgress <= MAIN_PATH_LAST_PROGRESS) {
      int landingTile = absoluteMainTile(player.color, move.toProgress);
      boolean landingSafe = isSafeMainProgress(player.color, move.toProgress);
      int attackers = landingSafe ? 0 : countAttackers(players, playerIndex, landingTile);

      if (landingSafe) {
        reward += weights.landSafe;
      } else {
        if (attackers >= 2) {
          reward += weights.multiOpponentDanger;
        } else if (attackers == 1) {
          reward += weights.exposeToCapture;
        }
        if (move.fromProgress >= 0
            && move.fromProgress <= MAIN_PATH_LAST_PROGRESS
            && isSafeMainProgress(player.color, move.fromProgress)
            && attackers > 0) {
          reward += weights.leaveSafetyIntoDanger;
        }
      }

      if (wasThreatened && attackers == 0) {
        reward += weights.escapeThreat + weights.saveThreatened;
      }
    } else if (wasThreatened && move.toProgress >= HOME_LANE_START_PROGRESS) {
      reward += weights.escapeThreat + weights.saveThreatened;
    } else if (wasThreatened && move.toProgress > move.fromProgress) {
      reward += weights.saveThreatened * 0.5;
    }
    return reward;
  }

  private static double attackReward(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      List<int[]> captures,
      List<SuperiorPlayerState> resultingPlayers,
      double[] threatByOpponent,
      boolean twoPlayer,
      BotRewardWeights weights) {
    double reward = 0.0;
    for (int[] capture : captures) {
      int opponentIndex = capture[0];
      int opponentProgress = capture[1];
      double progressBonus = opponentProgress * 4.0;
      double nearHomeBonus =
          opponentProgress >= 40 ? 180.0 : (opponentProgress >= 25 ? 80.0 : 0.0);
      double threatBonus =
          (opponentIndex >= 0 && opponentIndex < threatByOpponent.length
                  ? threatByOpponent[opponentIndex]
                  : 0.0)
              * 40.0;
      double captureValue = weights.captureBase + progressBonus + nearHomeBonus + threatBonus;
      if (twoPlayer) {
        captureValue *= weights.twoPlayerAttackMultiplier;
      }
      int landingTile =
          absoluteMainTile(resultingPlayers.get(playerIndex).color, move.toProgress);
      int postDanger = countAttackers(resultingPlayers, playerIndex, landingTile);
      if (postDanger > 0
          && move.toProgress >= 0
          && move.toProgress <= MAIN_PATH_LAST_PROGRESS
          && !isSafeMainProgress(resultingPlayers.get(playerIndex).color, move.toProgress)) {
        captureValue -= 220.0 * postDanger;
      }
      reward += captureValue;
    }

    if (move.toProgress >= 0 && move.toProgress <= MAIN_PATH_LAST_PROGRESS) {
      for (int dice = 1; dice <= 6; dice++) {
        if (!canMoveToken(move.toProgress, dice)) {
          continue;
        }
        int reach = move.toProgress + dice;
        if (reach < 0 || reach > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        int reachTile = absoluteMainTile(players.get(playerIndex).color, reach);
        if (isSafeMainProgress(players.get(playerIndex).color, reach)) {
          continue;
        }
        for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
          if (opponentIndex == playerIndex || players.get(opponentIndex).abandoned) {
            continue;
          }
          SuperiorPlayerState opponent = players.get(opponentIndex);
          for (int tokenIndex = 0; tokenIndex < opponent.tokens.length; tokenIndex++) {
            int progress = opponent.tokens[tokenIndex];
            if (progress >= 0
                && progress <= MAIN_PATH_LAST_PROGRESS
                && absoluteMainTile(opponent.color, progress) == reachTile) {
              reward += weights.createCaptureThreat / 6.0;
            }
          }
        }
      }
    }
    return reward;
  }

  /**
   * Values opponents that can be reached with fair future dice rolls (exact-sum probabilities from
   * uniform 1..6 — not dice prediction / manipulation).
   */
  private static double huntReward(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      double[] threatByOpponent,
      BotRewardWeights weights) {
    if (move.toProgress < 0 || move.toProgress > MAIN_PATH_LAST_PROGRESS) {
      return 0.0;
    }

    int horizon = Math.max(1, Math.min(4, weights.huntHorizonTurns));
    double reward = 0.0;
    for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
      if (opponentIndex == playerIndex || players.get(opponentIndex).abandoned) {
        continue;
      }
      SuperiorPlayerState opponent = players.get(opponentIndex);
      for (int tokenIndex = 0; tokenIndex < opponent.tokens.length; tokenIndex++) {
        int opponentProgress = opponent.tokens[tokenIndex];
        if (opponentProgress < 0 || opponentProgress > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        if (isSafeMainProgress(opponent.color, opponentProgress)) {
          continue;
        }
        int targetTile = absoluteMainTile(opponent.color, opponentProgress);
        Integer distance =
            forwardDistanceToCell(
                players.get(playerIndex).color,
                move.toProgress,
                targetTile,
                horizon * 6);
        if (distance == null || distance == 0) {
          continue;
        }
        double reachProbability = exactReachProbability(distance, horizon);
        double threat =
            opponentIndex >= 0 && opponentIndex < threatByOpponent.length
                ? threatByOpponent[opponentIndex]
                : 0.0;
        double targetValue =
            1.0
                + (double) opponentProgress / FINISHED_PROGRESS * 1.4
                + threat * 0.06;
        reward += weights.huntReward * reachProbability * targetValue;
      }
    }
    return reward;
  }

  private static double strategicReward(
      List<SuperiorPlayerState> players,
      int playerIndex,
      CandidateMove move,
      List<SuperiorPlayerState> resultingPlayers,
      BotRewardWeights weights) {
    double reward = 0.0;
    int beforeStacks = ownStacks(players.get(playerIndex));
    int afterStacks = ownStacks(resultingPlayers.get(playerIndex));
    if (afterStacks > beforeStacks) {
      reward += weights.createBlockade;
    } else if (afterStacks < beforeStacks && move.toProgress != FINISHED_PROGRESS) {
      reward += weights.breakOwnBlockade * 0.5;
    } else if (afterStacks > 0) {
      reward += weights.maintainBlockade * 0.25;
    }
    reward += tokenDiversityReward(players.get(playerIndex), move, weights);
    return reward;
  }

  private static double tokenDiversityReward(
      SuperiorPlayerState player, CandidateMove move, BotRewardWeights weights) {
    List<Integer> outside = new ArrayList<>();
    for (int value : player.tokens) {
      if (value >= 0 && value <= MAIN_PATH_LAST_PROGRESS) {
        outside.add(value);
      }
    }
    if (outside.isEmpty()) {
      return move.fromProgress == -1 ? weights.tokenDiversityReward : 0.0;
    }

    int trailingProgress = outside.stream().mapToInt(Integer::intValue).min().orElse(0);
    int leadingProgress = outside.stream().mapToInt(Integer::intValue).max().orElse(0);
    int spread = leadingProgress - trailingProgress;
    double reward = 0.0;

    if (move.fromProgress == -1 && outside.size() < 3) {
      reward += weights.tokenDiversityReward * (1.0 - outside.size() * 0.2);
    }
    if (outside.size() >= 2 && move.fromProgress == trailingProgress && spread >= 10) {
      reward += weights.tokenDiversityReward * (Math.min(spread, 30) / 30.0);
    }
    if (outside.size() >= 2
        && move.fromProgress == leadingProgress
        && spread >= 20
        && leadingProgress < HOME_LANE_START_PROGRESS) {
      reward -= weights.tokenDiversityReward * 0.35;
    }
    return reward;
  }

  private static double captureRiskPenalty(
      List<SuperiorPlayerState> players,
      int playerIndex,
      int tokenIndex,
      BotRewardWeights weights) {
    int progress = players.get(playerIndex).tokens[tokenIndex];
    if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
      return 0.0;
    }
    SuperiorPlayerState player = players.get(playerIndex);
    if (isSafeMainProgress(player.color, progress)) {
      return 0.0;
    }
    int tile = absoluteMainTile(player.color, progress);
    double dangerProbability =
        captureProbabilityWithinTurns(players, playerIndex, tile, 2);
    double progressMultiplier = 1.0 + (double) progress / FINISHED_PROGRESS;
    return weights.dangerProbabilityPenalty * dangerProbability * progressMultiplier;
  }

  private static double expectimaxOpponentPly(
      List<SuperiorPlayerState> resultingPlayers,
      int botIndex,
      BotRewardWeights weights,
      long deadlineNs) {
    Integer nextOpponent = nextActiveIndex(resultingPlayers, botIndex + 1);
    if (nextOpponent == null) {
      return boardValue(resultingPlayers, botIndex);
    }
    if (nextOpponent == botIndex) {
      return boardValue(resultingPlayers, botIndex);
    }

    double expected = 0.0;
    for (int dice = 1; dice <= 6; dice++) {
      if (System.nanoTime() > deadlineNs) {
        return expected / Math.max(1, dice - 1);
      }
      List<Integer> legal = new ArrayList<>();
      int[] oppTokens = resultingPlayers.get(nextOpponent).tokens;
      for (int tokenIndex = 0; tokenIndex < oppTokens.length; tokenIndex++) {
        if (canMoveToken(oppTokens[tokenIndex], dice)) {
          legal.add(tokenIndex);
        }
      }
      if (legal.isEmpty()) {
        expected += boardValue(resultingPlayers, botIndex);
        continue;
      }
      double worstForBot = Double.POSITIVE_INFINITY;
      for (int tokenIndex : legal) {
        CandidateMove move =
            candidateMove(resultingPlayers.get(nextOpponent), tokenIndex, dice);
        List<SuperiorPlayerState> after =
            simulateMove(resultingPlayers, nextOpponent, move, dice);
        worstForBot = Math.min(worstForBot, boardValue(after, botIndex));
      }
      expected += worstForBot;
    }
    return expected / 6.0 * 0.15;
  }

  private static double boardValue(List<SuperiorPlayerState> players, int botIndex) {
    SuperiorPlayerState bot = players.get(botIndex);
    if (tokensAllFinished(bot)) {
      return 10_000.0;
    }
    double value = 0.0;
    for (int progress : bot.tokens) {
      if (progress == FINISHED_PROGRESS) {
        value += 180.0;
      } else if (progress >= HOME_LANE_START_PROGRESS) {
        value += 90.0 + (progress - HOME_LANE_START_PROGRESS) * 12.0;
      } else if (progress >= 0) {
        value += progress * 1.6;
      } else {
        value += -8.0;
      }
    }
    for (int index = 0; index < players.size(); index++) {
      if (index == botIndex || players.get(index).abandoned) {
        continue;
      }
      double opp = 0.0;
      for (int progress : players.get(index).tokens) {
        if (progress == FINISHED_PROGRESS) {
          opp += 160.0;
        } else if (progress >= HOME_LANE_START_PROGRESS) {
          opp += 70.0 + (progress - HOME_LANE_START_PROGRESS) * 10.0;
        } else if (progress >= 0) {
          opp += progress * 1.1;
        }
      }
      value -= opp * 0.35;
    }
    return value;
  }

  private static double opponentThreat(
      List<SuperiorPlayerState> players, int botIndex, int opponentIndex) {
    SuperiorPlayerState opponent = players.get(opponentIndex);
    int completed = 0;
    int nearHome = 0;
    double sum = 0.0;
    int activeCount = 0;
    for (int t : opponent.tokens) {
      if (t == FINISHED_PROGRESS) {
        completed++;
      }
      if (t >= 0) {
        sum += Math.min(t, FINISHED_PROGRESS);
        activeCount++;
      }
      if (t >= 40) {
        nearHome++;
      }
    }
    double avgProgress = activeCount > 0 ? sum / activeCount : 0.0;

    int directThreat = 0;
    for (int progress : opponent.tokens) {
      if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
        continue;
      }
      boolean threatens = false;
      for (int botProgress : players.get(botIndex).tokens) {
        if (botProgress < 0 || botProgress > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        int botTile = absoluteMainTile(players.get(botIndex).color, botProgress);
        for (int dice = 1; dice <= 6; dice++) {
          if (canMoveToken(progress, dice)
              && absoluteMainTile(opponent.color, progress + dice) == botTile) {
            threatens = true;
            break;
          }
        }
        if (threatens) {
          break;
        }
      }
      if (threatens) {
        directThreat++;
      }
    }
    return completed * 3.0 + avgProgress / 20.0 + nearHome * 1.5 + directThreat * 2.0;
  }

  /** Each entry is {@code {opponentIndex, opponentProgress}}. */
  private static List<int[]> captureTargets(
      List<SuperiorPlayerState> players, int playerIndex, CandidateMove move) {
    if (move.toProgress < 0 || move.toProgress > MAIN_PATH_LAST_PROGRESS) {
      return List.of();
    }
    SuperiorPlayerState player = players.get(playerIndex);
    if (isSafeMainProgress(player.color, move.toProgress)) {
      return List.of();
    }
    int landingTile = absoluteMainTile(player.color, move.toProgress);
    List<int[]> targets = new ArrayList<>();
    for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
      if (opponentIndex == playerIndex || players.get(opponentIndex).abandoned) {
        continue;
      }
      SuperiorPlayerState opponent = players.get(opponentIndex);
      for (int tokenIndex = 0; tokenIndex < opponent.tokens.length; tokenIndex++) {
        int progress = opponent.tokens[tokenIndex];
        if (progress >= 0
            && progress <= MAIN_PATH_LAST_PROGRESS
            && absoluteMainTile(opponent.color, progress) == landingTile) {
          targets.add(new int[] {opponentIndex, progress});
        }
      }
    }
    return targets;
  }

  private static Integer forwardDistanceToCell(
      LudoColor color, int fromProgress, int targetTile, int maxDistance) {
    if (fromProgress < 0 || fromProgress > MAIN_PATH_LAST_PROGRESS) {
      return null;
    }
    int allowedDistance = Math.min(maxDistance, MAIN_PATH_LAST_PROGRESS - fromProgress);
    for (int distance = 1; distance <= allowedDistance; distance++) {
      if (absoluteMainTile(color, fromProgress + distance) == targetTile) {
        return distance;
      }
    }
    return null;
  }

  private static double exactReachProbability(int distance, int horizonTurns) {
    if (distance <= 0) {
      return 0.0;
    }
    double[] distribution = new double[] {1.0};
    double probability = 0.0;
    for (int turn = 0; turn < horizonTurns; turn++) {
      double[] next = new double[distribution.length + 6];
      for (int sum = 0; sum < distribution.length; sum++) {
        double chance = distribution[sum];
        if (chance == 0.0) {
          continue;
        }
        for (int dice = 1; dice <= 6; dice++) {
          next[sum + dice] += chance / 6.0;
        }
      }
      distribution = next;
      if (distance < distribution.length) {
        probability += distribution[distance];
      }
    }
    return Math.max(0.0, Math.min(1.0, probability));
  }

  private static double captureProbabilityWithinTurns(
      List<SuperiorPlayerState> players,
      int playerIndex,
      int cellTile,
      int horizonTurns) {
    double survivalProbability = 1.0;
    for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
      if (opponentIndex == playerIndex || players.get(opponentIndex).abandoned) {
        continue;
      }
      SuperiorPlayerState opponent = players.get(opponentIndex);
      Set<Integer> distances = new HashSet<>();
      for (int tokenIndex = 0; tokenIndex < opponent.tokens.length; tokenIndex++) {
        Integer distance =
            forwardDistanceToCell(
                opponent.color,
                opponent.tokens[tokenIndex],
                cellTile,
                horizonTurns * 6);
        if (distance != null) {
          distances.add(distance);
        }
      }
      double opponentCaptureProbability = probabilityOfAnyReach(distances, horizonTurns);
      survivalProbability *= 1.0 - opponentCaptureProbability;
    }
    return Math.max(0.0, Math.min(1.0, 1.0 - survivalProbability));
  }

  private static double probabilityOfAnyReach(Set<Integer> distances, int horizonTurns) {
    if (distances == null || distances.isEmpty() || horizonTurns <= 0) {
      return 0.0;
    }
    long[] counts = new long[2];
    visitReach(0, 0, false, horizonTurns, distances, counts);
    return counts[1] == 0 ? 0.0 : (double) counts[0] / (double) counts[1];
  }

  /** counts[0]=successfulPaths, counts[1]=totalPaths */
  private static void visitReach(
      int turn,
      int sum,
      boolean captured,
      int horizonTurns,
      Set<Integer> distances,
      long[] counts) {
    if (turn == horizonTurns) {
      counts[1] += 1;
      if (captured) {
        counts[0] += 1;
      }
      return;
    }
    for (int dice = 1; dice <= 6; dice++) {
      int nextSum = sum + dice;
      visitReach(
          turn + 1, nextSum, captured || distances.contains(nextSum), horizonTurns, distances, counts);
    }
  }

  private static int countAttackers(
      List<SuperiorPlayerState> players, int playerIndex, int cellTile) {
    if (cellTile < 0) {
      return 0;
    }
    int attackers = 0;
    for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
      if (opponentIndex == playerIndex || players.get(opponentIndex).abandoned) {
        continue;
      }
      SuperiorPlayerState opponent = players.get(opponentIndex);
      boolean canReach = false;
      for (int tokenIndex = 0; tokenIndex < opponent.tokens.length; tokenIndex++) {
        int progress = opponent.tokens[tokenIndex];
        if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        for (int dice = 1; dice <= 6; dice++) {
          if (canMoveToken(progress, dice)
              && absoluteMainTile(opponent.color, progress + dice) == cellTile) {
            canReach = true;
            break;
          }
        }
        if (canReach) {
          break;
        }
      }
      if (canReach) {
        attackers++;
      }
    }
    return attackers;
  }

  private static int ownStacks(SuperiorPlayerState player) {
    Map<Integer, Integer> counts = new HashMap<>();
    for (int progress : player.tokens) {
      if (progress >= 0 && progress <= MAIN_PATH_LAST_PROGRESS) {
        int tile = absoluteMainTile(player.color, progress);
        counts.merge(tile, 1, Integer::sum);
      }
    }
    int stacks = 0;
    for (int c : counts.values()) {
      if (c >= 2) {
        stacks++;
      }
    }
    return stacks;
  }

  private static boolean tokensAllFinished(SuperiorPlayerState player) {
    if (player.tokens.length == 0) {
      return false;
    }
    for (int t : player.tokens) {
      if (t != FINISHED_PROGRESS) {
        return false;
      }
    }
    return true;
  }

  private static Integer nextActiveIndex(List<SuperiorPlayerState> players, int start) {
    for (int offset = 0; offset < players.size(); offset++) {
      int index = Math.floorMod(start + offset, players.size());
      if (!players.get(index).abandoned) {
        return index;
      }
    }
    return null;
  }

  private static String summarizeReason(
      double immediate,
      double progress,
      double safety,
      double attack,
      double strategic,
      double risk,
      double future) {
    LinkedHashMap<String, Double> parts = new LinkedHashMap<>();
    parts.put("win/home", immediate);
    parts.put("progress", progress);
    parts.put("safety", safety);
    parts.put("attack", attack);
    parts.put("strategy", strategic);
    parts.put("future", future);
    parts.put("risk", risk);

    String topKey = null;
    double topValue = Double.NEGATIVE_INFINITY;
    for (Map.Entry<String, Double> e : parts.entrySet()) {
      if (e.getValue() > topValue) {
        topValue = e.getValue();
        topKey = e.getKey();
      }
    }
    if (topKey == null) {
      return "Balanced move";
    }
    if (topValue >= 900) {
      return "Winning / finishing priority";
    }
    if ("attack".equals(topKey) && topValue >= 500) {
      return "High-value capture with acceptable future risk";
    }
    if ("safety".equals(topKey) && topValue >= 200) {
      return "Escape or safe positioning";
    }
    if ("progress".equals(topKey)) {
      return "Useful forward progress";
    }
    if ("future".equals(topKey)) {
      return "Better expected future state";
    }
    return "Best overall EV (" + topKey + ")";
  }

  private static MoveEvaluation tieBreak(List<MoveEvaluation> evaluations, Random random) {
    double bestScore = Double.NEGATIVE_INFINITY;
    for (MoveEvaluation e : evaluations) {
      bestScore = Math.max(bestScore, e.finalScore);
    }
    List<MoveEvaluation> tied = new ArrayList<>();
    for (MoveEvaluation e : evaluations) {
      if (Math.abs(e.finalScore - bestScore) < 1e-6) {
        tied.add(e);
      }
    }
    if (tied.size() == 1) {
      return tied.get(0);
    }

    int bestRank = Integer.MAX_VALUE;
    for (MoveEvaluation e : tied) {
      bestRank = Math.min(bestRank, rank(e));
    }
    List<MoveEvaluation> ranked = new ArrayList<>();
    for (MoveEvaluation e : tied) {
      if (rank(e) == bestRank) {
        ranked.add(e);
      }
    }
    return ranked.get(random.nextInt(ranked.size()));
  }

  private static int rank(MoveEvaluation evaluation) {
    CandidateMove move = evaluation.move;
    if (evaluation.immediateReward >= 9_000) {
      return 0;
    }
    if (move.toProgress == FINISHED_PROGRESS) {
      return 1;
    }
    if (move.toProgress >= HOME_LANE_START_PROGRESS
        && move.toProgress <= HOME_LANE_LAST_PROGRESS) {
      return 2;
    }
    if (evaluation.safetyReward >= 400) {
      return 3;
    }
    if (evaluation.attackReward >= 700 && evaluation.riskPenalty > -200) {
      return 4;
    }
    if (evaluation.safetyReward >= 150) {
      return 5;
    }
    if (evaluation.strategicReward >= 100) {
      return 6;
    }
    if (evaluation.progressReward > 0) {
      return 7;
    }
    return 8;
  }
}
