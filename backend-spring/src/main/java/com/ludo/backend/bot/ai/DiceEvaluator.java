package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Scores each legal die face 1–6 for non-kill strategic value.
 *
 * <p><b>Never</b> awards points for capturing — kill assist stays separate.
 */
@Component
public class DiceEvaluator {

  @FunctionalInterface
  public interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  private final SmartDiceConfig config;
  private final DiceStrategy strategy;
  private final DecisionModifier decisionModifier;

  public DiceEvaluator(
      SmartDiceConfig config, DiceStrategy strategy, DecisionModifier decisionModifier
  ) {
    this.config = config;
    this.strategy = strategy;
    this.decisionModifier = decisionModifier;
  }

  public List<DiceCandidate> evaluateAll(
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis analysis,
      MoveLegality legality,
      DiceHistory history,
      String roomId
  ) {
    return evaluateAll(snap, botSeat, analysis, legality, history, roomId, null);
  }

  public List<DiceCandidate> evaluateAll(
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis analysis,
      MoveLegality legality,
      DiceHistory history,
      String roomId,
      PersonalityProfile personality
  ) {
    return evaluateAll(snap, botSeat, analysis, legality, history, roomId, personality, null);
  }

  public List<DiceCandidate> evaluateAll(
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis analysis,
      MoveLegality legality,
      DiceHistory history,
      String roomId,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    List<DiceCandidate> out = new ArrayList<>(6);
    for (int d = 1; d <= 6; d++) {
      DiceScore score = evaluateDie(snap, botSeat, d, analysis, legality, personality, endGame);
      if (history != null && roomId != null) {
        int streak = history.trailingStreak(roomId, botSeat, d);
        if (streak >= 2) {
          score.add("Anti Repeat Streak", -40 * (streak - 1));
        }
        if (d == 6 && streak >= 2) {
          score.add("Anti Six Spam", -60);
        }
      }
      out.add(new DiceCandidate(d, score));
    }
    return out;
  }

  DiceScore evaluateDie(
      GameSnapshot snap,
      int botSeat,
      int dice,
      BotMatchAnalysis analysis,
      MoveLegality legality
  ) {
    return evaluateDie(snap, botSeat, dice, analysis, legality, null, null);
  }

  DiceScore evaluateDie(
      GameSnapshot snap,
      int botSeat,
      int dice,
      BotMatchAnalysis analysis,
      MoveLegality legality,
      PersonalityProfile personality
  ) {
    return evaluateDie(snap, botSeat, dice, analysis, legality, personality, null);
  }

  DiceScore evaluateDie(
      GameSnapshot snap,
      int botSeat,
      int dice,
      BotMatchAnalysis analysis,
      MoveLegality legality,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    DiceScore best = new DiceScore();
    best.add("Base", 0);
    if (snap == null || legality == null) {
      best.add("Useless", -config.uselessPenalty());
      return best;
    }
    List<String> colors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    if (colors == null || all == null || botSeat < 0 || botSeat >= colors.size()) {
      best.add("Useless", -config.uselessPenalty());
      return best;
    }
    LudoColor color = BotBoardMath.parseColor(colors.get(botSeat));
    List<Integer> own = all.get(colors.get(botSeat));
    if (color == null || own == null) {
      best.add("Useless", -config.uselessPenalty());
      return best;
    }

    int active = BotBoardMath.countActive(own);
    int finished = BotBoardMath.countHome(own);
    boolean anyLegal = false;
    DiceScore bestUseful = null;

    for (int token = 0; token < own.size(); token++) {
      if (!legality.canMove(token, dice)) {
        continue;
      }
      anyLegal = true;
      int from = own.get(token) == null ? JAIL : own.get(token);
      int to = BotBoardMath.applySteps(color, from, dice);
      DiceScore s =
          scoreMove(
              from,
              to,
              dice,
              color,
              own,
              token,
              active,
              finished,
              analysis,
              all,
              colors,
              botSeat,
              personality,
              endGame);
      if (bestUseful == null || s.total() > bestUseful.total()) {
        bestUseful = s;
      }
    }

    if (!anyLegal || bestUseful == null) {
      best.add("Useless Dice", -config.uselessPenalty());
      return best;
    }
    return bestUseful;
  }

  private DiceScore scoreMove(
      int from,
      int to,
      int dice,
      LudoColor color,
      List<Integer> own,
      int token,
      int active,
      int finished,
      BotMatchAnalysis analysis,
      Map<String, List<Integer>> all,
      List<String> colors,
      int botSeat,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    DiceScore s = new DiceScore();
    BotGamePhase phase = analysis != null ? analysis.phase : BotGamePhase.MID;

    // Explicitly ignore captures for scoring (no kill assist here)
    boolean[] isBot = analysis != null ? analysis.isBot : null;
    boolean wouldCapture =
        BotBoardMath.findCaptureVictim(botSeat, to, all, colors, isBot) != null;

    if (isHome(to)) {
      s.add("Reach Home", config.homeBonus());
      applyPersonalityBias(s, personality, "home");
      applyEndGameDiceBias(s, endGame, "home");
    } else if (isExit(to) && !isExit(from)) {
      s.add("Enter Home Path", config.homePathBonus());
      applyPersonalityBias(s, personality, "home");
      applyEndGameDiceBias(s, endGame, "home");
    } else if (isExit(to)) {
      s.add("Final Stretch", config.homePathBonus() * 2 / 3);
      applyPersonalityBias(s, personality, "home");
      applyEndGameDiceBias(s, endGame, "home");
    }

    if (isSafe(to) && !isSafe(from)) {
      s.add("Reach Safe Cell", config.safeBonus());
      applyPersonalityBias(s, personality, "safe");
      applyEndGameDiceBias(s, endGame, "safe");
    } else if (isSafe(to)) {
      s.add("Stay Safe", config.safeBonus() / 3);
      applyPersonalityBias(s, personality, "safe");
      applyEndGameDiceBias(s, endGame, "safe");
    }

    boolean fromThreat =
        isMain(from)
            && !isSafe(from)
            && BotBoardMath.isPositionThreatened(botSeat, from, all, colors);
    boolean toThreat =
        isMain(to)
            && !isSafe(to)
            && !isHome(to)
            && BotBoardMath.isPositionThreatened(botSeat, to, all, colors);

    if (fromThreat && !toThreat) {
      s.add("Escape Danger", config.escapeBonus());
      applyPersonalityBias(s, personality, "escape");
      applyEndGameDiceBias(s, endGame, "escape");
    }
    if (toThreat) {
      s.add("Move Into Danger", -config.dangerPenalty());
    }

    if (isJail(from) && dice == 6) {
      // Endgame: do not bias opening pawns — only home/escape/safe
      if (endGame == null || !endGame.active()) {
        if (strategy.preferOpenPawns(phase) || active < 3) {
          s.add("Open Pawn", config.openBonus());
          applyPersonalityBias(s, personality, "open");
        }
        int activeAfter = active + 1;
        if (activeAfter == 2 || activeAfter == 3) {
          s.add("Board Development", config.boardDevBonus());
        }
      }
    }

    if (isMain(to) && !isSafe(to) && countOwnOn(own, token, to) >= 1) {
      s.add("Create Block", config.blockBonus());
      if (analysis != null
          && analysis.leaderSeat >= 0
          && analysis.leaderSeat != botSeat) {
        s.add("Leader Contested Block", config.leaderBonus());
      }
    }

    if (BotBoardMath.isNearHome(color, from) || isExit(from)) {
      s.add("Near Home Priority", config.nearHomeBonus());
      if ((strategy.preferHomeExact(phase) || (endGame != null && endGame.active()))
          && isHome(to)) {
        s.add("Endgame Exact Finish", 40);
        applyPersonalityBias(s, personality, "home");
        applyEndGameDiceBias(s, endGame, "home");
      }
    }

    if (!wouldCapture && !isJail(from) && to != from) {
      int gain = BotBoardMath.pawnProgress(color, to) - BotBoardMath.pawnProgress(color, from);
      if (gain > 0 && !toThreat) {
        s.add("Safe Progress", Math.min(25, gain / 2));
      }
    }

    if (wouldCapture && s.total() <= 0) {
      s.add("Capture Neutral", 0);
    }

    if (s.total() == 0 && !isJail(from)) {
      s.add("Low Value", -config.uselessPenalty() / 2);
    }
    return s;
  }

  private void applyPersonalityBias(
      DiceScore s, PersonalityProfile personality, String outcomeKey
  ) {
    if (decisionModifier == null || personality == null || !personality.enabled()) {
      return;
    }
    int bias = decisionModifier.diceOutcomeBias(personality, outcomeKey);
    if (bias != 0) {
      s.add("Personality " + outcomeKey, bias);
    }
  }

  private void applyEndGameDiceBias(DiceScore s, EndGameProfile endGame, String outcomeKey) {
    if (endGame == null || !endGame.active()) {
      return;
    }
    // Home / exact finish / escape / safe only — never capture bias
    int bias =
        switch (outcomeKey) {
          case "home" -> (int) Math.round(55 * (endGame.homeBias() - 1.0) + 25);
          case "safe" -> (int) Math.round(30 * endGame.safeBias());
          case "escape" -> (int) Math.round(35 * endGame.safeBias());
          default -> 0;
        };
    if (bias != 0) {
      s.add("EndGame " + outcomeKey, bias);
    }
  }

  private static int countOwnOn(List<Integer> own, int exclude, int cell) {
    int n = 0;
    for (int i = 0; i < own.size(); i++) {
      if (i == exclude) {
        continue;
      }
      Integer p = own.get(i);
      if (p != null && p == cell) {
        n++;
      }
    }
    return n;
  }
}
