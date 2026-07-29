package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hard-bot home-finish dice assist: when a bot pawn can reach center (HOME)
 * with an exact roll of 1–6, usually force that die. Humans never use this path.
 */
final class BotHomeDiceAssist {

  /** Default chance to roll the exact finish die when reachable. */
  static final double DEFAULT_HOME_ASSIST_PROBABILITY = 0.75;

  private BotHomeDiceAssist() {}

  @FunctionalInterface
  interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  static final class HomeOpportunity {
    final int dice;
    final int tokenIndex;
    final int remaining;

    HomeOpportunity(int dice, int tokenIndex, int remaining) {
      this.dice = dice;
      this.tokenIndex = tokenIndex;
      this.remaining = remaining;
    }
  }

  static Integer maybePickHomeDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality
  ) {
    return maybePickHomeDice(
        snap, botSeat, legality, ThreadLocalRandom.current(), DEFAULT_HOME_ASSIST_PROBABILITY);
  }

  static Integer maybePickHomeDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng,
      double homeAssistProbability
  ) {
    Integer best = pickBestHomeDice(snap, botSeat, legality, rng);
    if (best == null || rng == null) {
      return best;
    }
    double chance = Math.max(0.0, Math.min(1.0, homeAssistProbability));
    if (chance <= 0.0 || rng.nextDouble() >= chance) {
      return null;
    }
    return best;
  }

  /** Exact 1–6 needed to land on HOME, preferring the closest finish. */
  static Integer pickBestHomeDice(
      GameSnapshot snap,
      int botSeat,
      MoveLegality legality,
      Random rng
  ) {
    if (snap == null || legality == null || botSeat < 0) {
      return null;
    }
    if (snap.getIsBot() == null
        || botSeat >= snap.getIsBot().length
        || !Boolean.TRUE.equals(snap.getIsBot()[botSeat])) {
      return null;
    }

    String colorName = seatColor(snap, botSeat);
    if (colorName == null) {
      return null;
    }
    LudoColor color;
    try {
      color = LudoColor.valueOf(colorName);
    } catch (RuntimeException ex) {
      return null;
    }

    List<Integer> own = snap.getTokenPositions().get(colorName);
    if (own == null) {
      return null;
    }

    List<HomeOpportunity> opportunities = new ArrayList<>();
    for (int t = 0; t < own.size(); t++) {
      Integer fromObj = own.get(t);
      int from = fromObj == null ? JAIL : fromObj;
      if (isJail(from) || isHome(from)) {
        continue;
      }
      int remaining = BotMoveEvaluator.remainingDistance(color, from);
      if (remaining < 1 || remaining > 6) {
        continue;
      }
      if (!legality.canMove(t, remaining)) {
        continue;
      }
      int land = BotMoveEvaluator.applySteps(color, from, remaining);
      if (land != HOME) {
        continue;
      }
      opportunities.add(new HomeOpportunity(remaining, t, remaining));
    }

    if (opportunities.isEmpty()) {
      return null;
    }

    opportunities.sort(
        Comparator
            .comparingInt((HomeOpportunity o) -> o.remaining)
            .thenComparingInt(o -> o.tokenIndex));
    HomeOpportunity best = opportunities.get(0);
    List<HomeOpportunity> ties = new ArrayList<>();
    for (HomeOpportunity o : opportunities) {
      if (o.remaining == best.remaining) {
        ties.add(o);
      }
    }
    Random pickRng = rng != null ? rng : ThreadLocalRandom.current();
    return ties.get(pickRng.nextInt(ties.size())).dice;
  }

  private static String seatColor(GameSnapshot snap, int seat) {
    List<String> colors = snap.getSeatColors();
    if (colors != null && seat >= 0 && seat < colors.size()) {
      return colors.get(seat);
    }
    return snap.getCurrentColor();
  }
}
