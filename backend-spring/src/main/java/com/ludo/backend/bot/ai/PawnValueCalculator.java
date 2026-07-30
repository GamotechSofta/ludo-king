package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes base + dynamic pawn values from board state, phase, danger, and history.
 */
@Component
public class PawnValueCalculator {

  private final PawnValueConfig config;

  public PawnValueCalculator(PawnValueConfig config) {
    this.config = config;
  }

  public PawnState buildState(
      int pawnIndex,
      int pos,
      LudoColor color,
      int bestProgress,
      int dangerScore,
      int finishedSiblings,
      int startTile
  ) {
    boolean jail = isJail(pos);
    boolean home = isHome(pos);
    boolean safe = isSafe(pos);
    boolean exit = isExit(pos);
    int progress = BotBoardMath.pawnProgress(color, pos);
    int rem = BotBoardMath.remainingDistance(color, pos);
    if (rem == Integer.MAX_VALUE && !jail && !home) {
      rem = BotBoardMath.MAX_PAWN_PROGRESS;
    }
    boolean nearHome = !jail && !home && (exit || (rem != Integer.MAX_VALUE && rem <= HOME_STEPS + 10));
    boolean finalStretch =
        !jail && !home && (exit || (rem != Integer.MAX_VALUE && rem <= 3));
    boolean advanced =
        !jail
            && !home
            && progress >= Math.max(40, (int) (BotBoardMath.MAX_PAWN_PROGRESS * 0.55));
    boolean justReleased =
        !jail
            && !home
            && isMainNearStart(color, pos, startTile);
    boolean leader = !jail && !home && progress >= bestProgress && progress > 0;
    return new PawnState(
        pawnIndex,
        pos,
        progress,
        rem == Integer.MAX_VALUE ? -1 : rem,
        jail,
        home,
        safe,
        exit,
        leader,
        nearHome,
        finalStretch,
        advanced,
        justReleased,
        dangerScore,
        finishedSiblings);
  }

  public PawnPriority calculate(
      PawnState state,
      PawnStatistics stats,
      BotMatchAnalysis analysis
  ) {
    List<String> labels = PawnPriority.mutableLabels();
    int base = baseValue(state, labels);
    int value = base;

    if (state.safe() && !state.jail() && !state.home()) {
      value += config.safeCellBonus();
      labels.add("Safe");
    }
    if (state.exit()) {
      value += config.homePathBonus();
      labels.add("Home Path");
    }
    if (state.leader()) {
      value += config.leaderPawnBonus();
      labels.add("Leader Pawn");
    }
    if (state.advanced() && !state.nearHome()) {
      value += config.advancedPawnBonus();
      labels.add("Advanced");
    }
    if (state.nearHome() || state.finalStretch()) {
      value += Math.max(0, config.homePawnBonus() - base) / 4;
      labels.add("Near Home");
    }
    if (state.home()) {
      value = Math.max(value, config.homePawnBonus());
      labels.add("Finished");
    }

    // History adaptations
    if (stats != null) {
      if (stats.timesReachedSafe() > 0) {
        value += Math.min(15, stats.timesReachedSafe() * 3);
      }
      if (stats.timesEscapedDanger() > 0) {
        value += Math.min(12, stats.timesEscapedDanger() * 4);
      }
      if (stats.wasteStreak() >= 2 && !state.nearHome()) {
        value -= config.wasteTurnPenalty() * Math.min(3, stats.wasteStreak());
        labels.add("Waste");
      }
      if (state.jail() && stats.timesMoved() == 0) {
        value = Math.min(value, config.jailValue());
      }
    }

    // Phase adaptation
    if (analysis != null) {
      if (analysis.phase == BotGamePhase.EARLY) {
        if (state.jail()) {
          value = Math.min(value, config.jailValue() + 5);
          labels.add("Opening");
        } else if (!state.advanced()) {
          value += 10; // slight boost to keep 2–3 active
        }
      } else if (analysis.phase == BotGamePhase.END) {
        if (state.finishedSiblings() >= 3 && !state.home() && !state.jail()) {
          value += config.homePawnBonus();
          labels.add("Fourth Pawn");
        }
        if (state.nearHome() || state.finalStretch()) {
          value += 30;
        }
      } else if (analysis.phase == BotGamePhase.MID && state.advanced()) {
        value += 10;
      }
    }

    boolean escapeNeeded =
        state.dangerScore() >= 40 && !state.safe() && !state.jail() && !state.home();
    if (escapeNeeded) {
      // Urgency raises effective priority for protection, not raw board worth alone
      value += Math.min(60, (state.dangerScore() / 2) * config.dangerUrgencyFactor());
      labels.add("Danger");
    }

    value = Math.max(0, value);
    PawnImportance importance = PawnImportance.fromValue(value);
    if (state.finishedSiblings() >= 3 && !state.home() && !state.jail()) {
      importance = PawnImportance.HIGHEST;
    } else if (state.finalStretch() || (state.nearHome() && escapeNeeded)) {
      importance = max(importance, PawnImportance.HIGHEST);
    } else if (state.leader() && !state.jail()) {
      importance = max(importance, PawnImportance.HIGH);
    }

    boolean neverSacrifice =
        state.nearHome() || state.finalStretch() || state.exit() || importance == PawnImportance.HIGHEST;

    return new PawnPriority(
        state.pawnIndex(),
        value,
        base,
        importance,
        state,
        PawnPriority.freeze(labels),
        escapeNeeded,
        neverSacrifice);
  }

  private int baseValue(PawnState state, List<String> labels) {
    if (state.jail()) {
      return config.jailValue();
    }
    if (state.home()) {
      return config.homePawnBonus();
    }
    if (state.finalStretch()) {
      return config.finalStretchValue();
    }
    if (state.nearHome() || state.exit()) {
      return config.nearHomeValue();
    }
    if (state.advanced()) {
      return config.advancedValue();
    }
    int rem = state.remaining();
    int prog = state.progress();
    if (state.justReleased() || prog <= 8) {
      return config.justReleasedValue();
    }
    if (prog < BotBoardMath.MAX_PAWN_PROGRESS * 0.25
        || (rem > 0 && rem > BotBoardMath.MAX_PAWN_PROGRESS * 0.75)) {
      return config.earlyValue();
    }
    if (prog < BotBoardMath.MAX_PAWN_PROGRESS * 0.55) {
      return config.midValue();
    }
    return config.advancedValue();
  }

  private static boolean isMainNearStart(LudoColor color, int pos, int startTile) {
    if (color == null || !com.ludo.backend.game.BoardConstants.isMain(pos)) {
      return false;
    }
    int dist =
        (pos - startTile + com.ludo.backend.game.BoardConstants.TOTAL_TILES)
            % com.ludo.backend.game.BoardConstants.TOTAL_TILES;
    return dist <= 6;
  }

  private static PawnImportance max(PawnImportance a, PawnImportance b) {
    return a.ordinal() <= b.ordinal() ? a : b;
  }
}
