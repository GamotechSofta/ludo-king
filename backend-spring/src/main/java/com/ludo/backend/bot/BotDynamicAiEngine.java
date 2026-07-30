package com.ludo.backend.bot;

import com.ludo.backend.bot.ai.AdaptiveDifficultyEngine;
import com.ludo.backend.bot.ai.BotDecisionEngine;
import com.ludo.backend.bot.ai.BotPersonalityEngine;
import com.ludo.backend.bot.ai.DiceEvaluator;
import com.ludo.backend.bot.ai.DifficultyProfile;
import com.ludo.backend.bot.ai.EndGameAnalyzer;
import com.ludo.backend.bot.ai.EndGameEngine;
import com.ludo.backend.bot.ai.EndGameProfile;
import com.ludo.backend.bot.ai.PersonalityProfile;
import com.ludo.backend.bot.ai.SmartDiceEngine;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Dynamic AI facade: dice assists + move selection.
 *
 * <p>HARD move selection is delegated to {@link BotDecisionEngine} (production
 * scoring). EASY / MEDIUM keep the lightweight baseline path.
 */
@Service
public class BotDynamicAiEngine {

  private final BotMatchAnalyzer matchAnalyzer;
  private final BotDecisionEngine decisionEngine;
  private final SmartDiceEngine smartDiceEngine;
  private final AdaptiveDifficultyEngine adaptiveDifficultyEngine;
  private final BotPersonalityEngine personalityEngine;
  private final EndGameEngine endGameEngine;
  private final EndGameAnalyzer endGameAnalyzer;
  private final boolean dynamicEnabled;
  private final boolean phaseEnabled;
  private final boolean leaderDetection;
  private final boolean moveScoring;
  private final boolean riskAnalysis;
  private final boolean comebackEnabled;

  public BotDynamicAiEngine(
      BotMatchAnalyzer matchAnalyzer,
      BotDecisionEngine decisionEngine,
      SmartDiceEngine smartDiceEngine,
      AdaptiveDifficultyEngine adaptiveDifficultyEngine,
      BotPersonalityEngine personalityEngine,
      EndGameEngine endGameEngine,
      EndGameAnalyzer endGameAnalyzer,
      @Value("${ludo.bot.ai.dynamic:true}") boolean dynamicEnabled,
      @Value("${ludo.bot.phase.enabled:true}") boolean phaseEnabled,
      @Value("${ludo.bot.leader.detection:true}") boolean leaderDetection,
      @Value("${ludo.bot.move.scoring:true}") boolean moveScoring,
      @Value("${ludo.bot.risk.analysis:true}") boolean riskAnalysis,
      @Value("${ludo.bot.comeback.enabled:true}") boolean comebackEnabled
  ) {
    this.matchAnalyzer = matchAnalyzer;
    this.decisionEngine = decisionEngine;
    this.smartDiceEngine = smartDiceEngine;
    this.adaptiveDifficultyEngine = adaptiveDifficultyEngine;
    this.personalityEngine = personalityEngine;
    this.endGameEngine = endGameEngine;
    this.endGameAnalyzer = endGameAnalyzer;
    this.dynamicEnabled = dynamicEnabled;
    this.phaseEnabled = phaseEnabled;
    this.leaderDetection = leaderDetection;
    this.moveScoring = moveScoring;
    this.riskAnalysis = riskAnalysis;
    this.comebackEnabled = comebackEnabled;
  }

  public BotMatchAnalysis analyze(String roomId, GameSnapshot snap, int botSeat, BotDifficulty diff) {
    BotMatchAnalysis raw = matchAnalyzer.analyze(roomId, snap, botSeat, diff);
    if (!dynamicEnabled || diff != BotDifficulty.HARD) {
      return new BotMatchAnalysis(
          BotAiMode.OTHER,
          BotGamePhase.MID,
          diff == null ? BotDifficulty.HARD : diff,
          botSeat,
          raw.humanCount,
          raw.botCount,
          raw.playerCount,
          raw.isBot,
          leaderDetection ? raw.leaderSeat : botSeat,
          raw.seatProgress,
          raw.finishedPawns,
          raw.activePawns,
          raw.tableProgress,
          false,
          false,
          true);
    }
    BotGamePhase phase = phaseEnabled ? raw.phase : BotGamePhase.MID;
    boolean behind = comebackEnabled && raw.botBehind;
    return new BotMatchAnalysis(
        raw.mode,
        phase,
        raw.difficulty,
        raw.botSeat,
        raw.humanCount,
        raw.botCount,
        raw.playerCount,
        raw.isBot,
        leaderDetection ? raw.leaderSeat : -1,
        raw.seatProgress,
        raw.finishedPawns,
        raw.activePawns,
        raw.tableProgress,
        behind,
        raw.botIsLeader,
        raw.allowAggressiveLeaderHunt);
  }

  /**
   * Kill assist first (unchanged). If it declines, Smart Dice may assist non-kill faces.
   */
  public Integer maybeAssistDice(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty diff,
      BotKillDiceAssist.MoveLegality legality,
      Random rng
  ) {
    Integer kill = maybeAssistCaptureDice(roomId, snap, botSeat, diff, legality, rng);
    if (kill != null) {
      return kill;
    }
    return maybeAssistSmartDice(roomId, snap, botSeat, diff, legality, rng);
  }

  public Integer maybeAssistCaptureDice(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty diff,
      BotKillDiceAssist.MoveLegality legality,
      Random rng
  ) {
    if (!dynamicEnabled || diff != BotDifficulty.HARD) {
      return null;
    }
    BotMatchAnalysis analysis = analyze(roomId, snap, botSeat, diff);
    if (analysis.mode == BotAiMode.OTHER) {
      return null;
    }
    return BotKillDiceAssist.maybePickCaptureDice(
        snap, botSeat, legality, rng != null ? rng : ThreadLocalRandom.current(), analysis);
  }

  /** Non-kill strategic dice assist (HARD only). */
  public Integer maybeAssistSmartDice(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty diff,
      BotKillDiceAssist.MoveLegality legality,
      Random rng
  ) {
    if (!dynamicEnabled || diff != BotDifficulty.HARD || smartDiceEngine == null) {
      return null;
    }
    BotMatchAnalysis analysis = analyze(roomId, snap, botSeat, diff);
    DiceEvaluator.MoveLegality bridge =
        legality == null ? null : (token, dice) -> legality.canMove(token, dice);
    DifficultyProfile adaptive =
        adaptiveDifficultyEngine != null
            ? adaptiveDifficultyEngine.evaluate(
                roomId, snap, botSeat, diff, analysis, null, null)
            : DifficultyProfile.disabled();
    PersonalityProfile personality =
        personalityEngine != null
            ? personalityEngine.evaluate(roomId, botSeat, diff, analysis, adaptive)
            : PersonalityProfile.disabled();
    EndGameProfile endGame = EndGameProfile.inactive();
    if (endGameEngine != null && endGameAnalyzer != null) {
      List<String> colors = snap.getSeatColors();
      Map<String, List<Integer>> all = snap.getTokenPositions();
      LudoColor color =
          colors != null && botSeat < colors.size()
              ? BotBoardMath.parseColor(colors.get(botSeat))
              : null;
      List<Integer> own =
          color != null && all != null ? all.get(colors.get(botSeat)) : null;
      EndGameAnalyzer.Activation act =
          endGameAnalyzer.detect(diff, analysis, null, own, color);
      if (act.active()) {
        endGame =
            endGameEngine.evaluate(
                diff, analysis, null, own, color, List.of(), null, null, adaptive, personality);
        if (endGame.active() && personalityEngine != null) {
          personality = personalityEngine.applyEndGameConvergence(personality, endGame);
        }
      }
    }
    return smartDiceEngine.maybePick(
        roomId,
        snap,
        botSeat,
        diff,
        analysis,
        bridge,
        rng != null ? rng : ThreadLocalRandom.current(),
        adaptive,
        personality,
        endGame);
  }

  public int[] pickBestMove(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotDifficulty diff,
      LudoColor color,
      List<Integer> ownPositions,
      List<int[]> moves
  ) {
    BotMatchAnalysis analysis = analyze(roomId, snap, botSeat, diff);

    // HARD + production scoring engine
    if (diff == BotDifficulty.HARD
        && moveScoring
        && decisionEngine.usesProductionScoring(() -> true)) {
      int[] chosen =
          decisionEngine.decide(roomId, snap, botSeat, color, ownPositions, moves, analysis);
      if (chosen != null) {
        return chosen;
      }
    }

    return pickBaseline(analysis, snap, botSeat, color, ownPositions, moves, diff);
  }

  private int[] pickBaseline(
      BotMatchAnalysis analysis,
      GameSnapshot snap,
      int botSeat,
      LudoColor color,
      List<Integer> ownPositions,
      List<int[]> moves,
      BotDifficulty diff
  ) {
    Map<String, List<Integer>> all = snap.getTokenPositions();
    List<String> colors = snap.getSeatColors();
    List<Scored> scored = new ArrayList<>();
    for (int[] m : moves) {
      if (m == null || m.length < 2 || snap.getDiceList() == null) {
        continue;
      }
      int token = m[0];
      int diceIndex = m[1];
      if (token < 0 || token >= ownPositions.size()) {
        continue;
      }
      if (diceIndex < 0 || diceIndex >= snap.getDiceList().size()) {
        continue;
      }
      int from =
          ownPositions.get(token) == null
              ? com.ludo.backend.game.BoardConstants.JAIL
              : ownPositions.get(token);
      int dice = snap.getDiceList().get(diceIndex);
      int to = BotBoardMath.applySteps(color, from, dice);
      long value =
          BotMoveScoringEngine.scoreMove(
              analysis, color, botSeat, ownPositions, all, colors, token, from, to, dice);
      if (!riskAnalysis) {
        value += Math.max(0, BotBoardMath.pawnProgress(color, to));
      }
      scored.add(new Scored(m, value));
    }
    if (scored.isEmpty()) {
      return moves.get(0);
    }
    scored.sort((a, b) -> Long.compare(b.score, a.score));
    long best = scored.get(0).score;
    int mistakePct = mistakePercent(diff);
    if (mistakePct > 0
        && scored.size() > 1
        && ThreadLocalRandom.current().nextInt(100) < mistakePct) {
      int pick = 1 + ThreadLocalRandom.current().nextInt(Math.min(3, scored.size() - 1));
      return scored.get(pick).move;
    }
    List<int[]> ties = new ArrayList<>();
    for (Scored s : scored) {
      if (s.score == best) {
        ties.add(s.move);
      } else {
        break;
      }
    }
    return ties.get(ThreadLocalRandom.current().nextInt(ties.size()));
  }

  private static int mistakePercent(BotDifficulty diff) {
    if (diff == BotDifficulty.EASY) {
      return 30;
    }
    if (diff == BotDifficulty.MEDIUM) {
      return 12;
    }
    return 5;
  }

  private static final class Scored {
    final int[] move;
    final long score;

    Scored(int[] move, long score) {
      this.move = move;
      this.score = score;
    }
  }
}
