package com.ludo.backend.bot;

import static com.ludo.backend.bot.superior.ProgressCodec.MAIN_PATH_LAST_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.absoluteMainTile;
import static com.ludo.backend.bot.superior.ProgressCodec.canMoveToken;
import static com.ludo.backend.game.BoardConstants.isSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hunt human tokens across turns rather than snapping to the capture face.
 * Uses progress encoding ({@code -1..56}) and absolute main tiles for safe-cell checks.
 */
public final class BotKillStalk {

  /** Share of engaged hunts that still capture on the spot, so kills stay unpredictable. */
  static final int STALK_INSTANT_KILL_PERCENT = 22;

  /** Farthest gap (in board steps) the bot will start closing on a human token. */
  static final int STALK_MAX_CHASE_STEPS = 12;

  static final int STALK_MIN_PLANNED_ROUNDS = 2;
  static final int STALK_MAX_PLANNED_ROUNDS = 3;

  /** Sixes grant an extra turn and can open yard tokens, so approach steps stay below six. */
  private static final int STALK_MAX_APPROACH_FACE = 5;

  private BotKillStalk() {}

  /** Board main tile of a token worth hunting, or {@code null} when it cannot be captured there. */
  static Integer stalkTargetMainTile(BotPlayerView target, int targetTokenIndex) {
    if (target == null || targetTokenIndex < 0 || targetTokenIndex >= target.tokens().length) {
      return null;
    }
    int progress = target.tokens()[targetTokenIndex];
    if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
      return null;
    }
    int tile = absoluteMainTile(target.color(), progress);
    if (tile < 0 || isSafe(tile)) {
      return null;
    }
    return tile;
  }

  /**
   * Board steps needed for one hunter token to land exactly on {@code targetMainTile},
   * or {@code null} when the cell is behind the token or out of {@code maxSteps} reach.
   */
  static Integer stepsToReachCell(
      BotPlayerView hunter, int hunterTokenIndex, int targetMainTile, int maxSteps) {
    if (hunter == null || hunterTokenIndex < 0 || hunterTokenIndex >= hunter.tokens().length) {
      return null;
    }
    int progress = hunter.tokens()[hunterTokenIndex];
    if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
      return null;
    }
    for (int steps = 1; steps <= maxSteps; steps++) {
      int nextProgress = progress + steps;
      if (nextProgress > MAIN_PATH_LAST_PROGRESS) {
        return null;
      }
      if (absoluteMainTile(hunter.color(), nextProgress) == targetMainTile) {
        return steps;
      }
    }
    return null;
  }

  record StalkCandidate(
      int hunterTokenIndex, int targetPlayerIndex, int targetTokenIndex, int steps) {}

  /** Every hunter/prey pairing within chasing range, including gaps larger than one roll. */
  static List<StalkCandidate> findStalkCandidates(
      List<BotPlayerView> players, int playerIndex, int maxSteps) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return List.of();
    }
    BotPlayerView hunter = players.get(playerIndex);
    if (!hunter.isStalkableHunter()) {
      return List.of();
    }

    List<StalkCandidate> candidates = new ArrayList<>();
    for (int targetPlayerIndex = 0; targetPlayerIndex < players.size(); targetPlayerIndex++) {
      if (targetPlayerIndex == playerIndex) {
        continue;
      }
      BotPlayerView target = players.get(targetPlayerIndex);
      if (!target.isStalkableTarget()) {
        continue;
      }
      for (int targetTokenIndex = 0; targetTokenIndex < target.tokens().length; targetTokenIndex++) {
        Integer targetTile = stalkTargetMainTile(target, targetTokenIndex);
        if (targetTile == null) {
          continue;
        }
        for (int hunterTokenIndex = 0; hunterTokenIndex < hunter.tokens().length; hunterTokenIndex++) {
          Integer steps =
              stepsToReachCell(hunter, hunterTokenIndex, targetTile, maxSteps);
          if (steps == null) {
            continue;
          }
          candidates.add(
              new StalkCandidate(
                  hunterTokenIndex, targetPlayerIndex, targetTokenIndex, steps));
        }
      }
    }
    return candidates;
  }

  /** True when any live opponent token could capture on {@code mainTile} with a single roll. */
  static boolean isCellUnderOpponentFire(
      List<BotPlayerView> players, int playerIndex, int mainTile) {
    if (mainTile < 0 || isSafe(mainTile) || players == null) {
      return false;
    }

    for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
      if (opponentIndex == playerIndex) {
        continue;
      }
      BotPlayerView opponent = players.get(opponentIndex);
      if (opponent.isEffectivelyAbandoned()) {
        continue;
      }
      for (int tokenIndex = 0; tokenIndex < opponent.tokens().length; tokenIndex++) {
        int progress = opponent.tokens()[tokenIndex];
        if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        for (int dice = 1; dice <= 6; dice++) {
          int nextProgress = progress + dice;
          if (nextProgress > MAIN_PATH_LAST_PROGRESS) {
            break;
          }
          if (absoluteMainTile(opponent.color(), nextProgress) == mainTile) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Face that closes part of the gap without landing on the prey yet, splitting {@code steps}
   * across {@code roundsLeft} so the hunt finishes roughly on schedule.
   */
  private static Integer choosePartialFace(
      List<BotPlayerView> players,
      int playerIndex,
      int hunterTokenIndex,
      int steps,
      int roundsLeft,
      Random random) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return null;
    }
    BotPlayerView hunter = players.get(playerIndex);
    if (hunterTokenIndex < 0 || hunterTokenIndex >= hunter.tokens().length) {
      return null;
    }
    int progress = hunter.tokens()[hunterTokenIndex];
    if (progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
      return null;
    }

    int maxFace = Math.min(STALK_MAX_APPROACH_FACE, steps - 1);
    if (maxFace < 1) {
      return null;
    }

    int reserved = Math.max(1, roundsLeft) - 1;
    int idealFace = Math.max(1, Math.min(maxFace, (steps + roundsLeft - 1) / Math.max(1, roundsLeft)));

    List<Integer> window = new ArrayList<>();
    for (int face = Math.max(1, idealFace - 1); face <= Math.min(maxFace, idealFace + 1); face++) {
      if (face <= steps - reserved || face == 1) {
        window.add(face);
      }
    }
    if (window.isEmpty()) {
      window.add(idealFace);
    }

    List<Integer> quietLandings = new ArrayList<>();
    for (int face : window) {
      int landingTile = absoluteMainTile(hunter.color(), progress + face);
      if (!isCellUnderOpponentFire(players, playerIndex, landingTile)) {
        quietLandings.add(face);
      }
    }
    List<Integer> pool = quietLandings.isEmpty() ? window : quietLandings;
    return pool.get(random.nextInt(pool.size()));
  }

  private static BotDiceDecision finishingDecision(int steps, int hunterTokenIndex) {
    return new BotDiceDecision(steps, null, hunterTokenIndex);
  }

  private static boolean canFinishThisTurn(int steps, boolean allowSix) {
    return steps >= 1 && steps <= 6 && (allowSix || steps != 6);
  }

  /** Advance a hunt the bot already committed to, or {@code null} when the prey got away. */
  private static BotDiceDecision continueStalk(
      boolean allowSix,
      List<BotPlayerView> players,
      int playerIndex,
      KillStalkPlan plan,
      Random random) {
    if (plan.hunterPlayerIndex() != playerIndex) {
      return null;
    }
    if (playerIndex < 0 || playerIndex >= players.size()) {
      return null;
    }
    BotPlayerView hunter = players.get(playerIndex);
    if (!hunter.isStalkableHunter()) {
      return null;
    }
    if (plan.targetPlayerIndex() < 0 || plan.targetPlayerIndex() >= players.size()) {
      return null;
    }
    BotPlayerView target = players.get(plan.targetPlayerIndex());
    if (!target.isStalkableTarget()) {
      return null;
    }
    Integer targetTile = stalkTargetMainTile(target, plan.targetTokenIndex());
    if (targetTile == null) {
      return null;
    }
    Integer steps = stepsToReachCell(hunter, plan.hunterTokenIndex(), targetTile, STALK_MAX_CHASE_STEPS);
    if (steps == null) {
      return null;
    }

    int roundsLeft = Math.max(1, plan.plannedRounds() - plan.roundsSpent());
    if (canFinishThisTurn(steps, allowSix) && roundsLeft <= 1) {
      return finishingDecision(steps, plan.hunterTokenIndex());
    }

    Integer partialFace =
        choosePartialFace(
            players,
            playerIndex,
            plan.hunterTokenIndex(),
            steps,
            roundsLeft,
            random);
    if (partialFace == null) {
      return canFinishThisTurn(steps, allowSix)
          ? finishingDecision(steps, plan.hunterTokenIndex())
          : null;
    }

    return new BotDiceDecision(
        partialFace,
        plan.withRoundsSpent(plan.roundsSpent() + 1),
        plan.hunterTokenIndex());
  }

  /** Open a new hunt on the nearest human token, staging it over 2–3 rounds. */
  private static BotDiceDecision startStalk(
      boolean allowSix, List<BotPlayerView> players, int playerIndex, Random random) {
    List<StalkCandidate> candidates = findStalkCandidates(players, playerIndex, STALK_MAX_CHASE_STEPS);
    if (candidates.isEmpty()) {
      return null;
    }

    int nearestSteps =
        candidates.stream().mapToInt(StalkCandidate::steps).min().orElse(Integer.MAX_VALUE);
    List<StalkCandidate> nearest = new ArrayList<>();
    for (StalkCandidate c : candidates) {
      if (c.steps() == nearestSteps) {
        nearest.add(c);
      }
    }
    StalkCandidate chosen = nearest.get(random.nextInt(nearest.size()));
    boolean canFinishNow = canFinishThisTurn(chosen.steps(), allowSix);

    if (canFinishNow && random.nextInt(100) < STALK_INSTANT_KILL_PERCENT) {
      return finishingDecision(chosen.steps(), chosen.hunterTokenIndex());
    }

    int plannedRounds =
        STALK_MIN_PLANNED_ROUNDS
            + random.nextInt(STALK_MAX_PLANNED_ROUNDS - STALK_MIN_PLANNED_ROUNDS + 1);
    Integer partialFace =
        choosePartialFace(
            players,
            playerIndex,
            chosen.hunterTokenIndex(),
            chosen.steps(),
            plannedRounds,
            random);
    if (partialFace == null) {
      return canFinishNow ? finishingDecision(chosen.steps(), chosen.hunterTokenIndex()) : null;
    }

    return new BotDiceDecision(
        partialFace,
        new KillStalkPlan(
            playerIndex,
            chosen.hunterTokenIndex(),
            chosen.targetPlayerIndex(),
            chosen.targetTokenIndex(),
            plannedRounds,
            1),
        chosen.hunterTokenIndex());
  }

  /**
   * Dice face for a bot that hunts human tokens across turns rather than snapping to the capture
   * face. An in-flight hunt always continues; {@code killFavorPercent} only gates whether a new
   * hunt starts. {@code null} means roll normally.
   */
  static BotDiceDecision resolveStalkDice(
      boolean allowSix,
      List<BotPlayerView> players,
      int playerIndex,
      int killFavorPercent,
      KillStalkPlan existingPlan,
      Random random) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return null;
    }
    BotPlayerView hunter = players.get(playerIndex);
    if (!hunter.isStalkableHunter()) {
      return null;
    }

    if (existingPlan != null) {
      BotDiceDecision continued =
          continueStalk(allowSix, players, playerIndex, existingPlan, random);
      if (continued != null) {
        return continued;
      }
    }

    if (random.nextInt(100) >= killFavorPercent) {
      return null;
    }

    return startStalk(allowSix, players, playerIndex, random);
  }

  /** Hunt this bot is currently committed to, if any. */
  public static KillStalkPlan stalkPlanFor(Map<Integer, KillStalkPlan> plans, int playerIndex) {
    if (plans == null) {
      return null;
    }
    return plans.get(playerIndex);
  }

  /** Replace only this bot's hunt, leaving other bots' hunts untouched. */
  public static void upsertStalkPlan(
      Map<Integer, KillStalkPlan> plans, int playerIndex, KillStalkPlan plan) {
    if (plans == null) {
      return;
    }
    plans.remove(playerIndex);
    if (plan != null) {
      plans.put(playerIndex, plan);
    }
  }
}
