package com.ludo.backend.bot;

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
 * Production-grade Dynamic AI Difficulty Engine — decision making only.
 *
 * <p>Orchestrates mode detection, phase, leader, scoring, risk, comeback,
 * aggression (legal capture-die bias), and anti-gang-up.
 */
@Service
public class BotDynamicAiEngine {

  private final BotMatchAnalyzer matchAnalyzer;
  private final boolean dynamicEnabled;
  private final boolean phaseEnabled;
  private final boolean leaderDetection;
  private final boolean moveScoring;
  private final boolean riskAnalysis;
  private final boolean comebackEnabled;

  public BotDynamicAiEngine(
      BotMatchAnalyzer matchAnalyzer,
      @Value("${ludo.bot.ai.dynamic:true}") boolean dynamicEnabled,
      @Value("${ludo.bot.phase.enabled:true}") boolean phaseEnabled,
      @Value("${ludo.bot.leader.detection:true}") boolean leaderDetection,
      @Value("${ludo.bot.move.scoring:true}") boolean moveScoring,
      @Value("${ludo.bot.risk.analysis:true}") boolean riskAnalysis,
      @Value("${ludo.bot.comeback.enabled:true}") boolean comebackEnabled
  ) {
    this.matchAnalyzer = matchAnalyzer;
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
      int from = ownPositions.get(token) == null
          ? com.ludo.backend.game.BoardConstants.JAIL
          : ownPositions.get(token);
      int dice = snap.getDiceList().get(diceIndex);
      int to = BotBoardMath.applySteps(color, from, dice);

      long value;
      if (moveScoring) {
        value =
            BotMoveScoringEngine.scoreMove(
                analysis, color, botSeat, ownPositions, all, colors, token, from, to, dice);
        if (!riskAnalysis) {
          // Risk terms are embedded; when disabled, prefer progress-only soft boost
          value += Math.max(0, BotBoardMath.pawnProgress(color, to));
        }
      } else {
        value = token + dice;
      }
      scored.add(new Scored(m, value));
    }

    if (scored.isEmpty()) {
      return moves.get(0);
    }
    scored.sort((a, b) -> Long.compare(b.score, a.score));
    long best = scored.get(0).score;

    int mistakePct = mistakePercent(analysis);
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

  private static int mistakePercent(BotMatchAnalysis a) {
    if (a == null || a.difficulty == BotDifficulty.EASY) {
      return 30;
    }
    if (a.difficulty == BotDifficulty.MEDIUM) {
      return 12;
    }
    if (!a.hardDynamic()) {
      return 5;
    }
    if (a.phase == BotGamePhase.EARLY) {
      return 28;
    }
    if (a.phase == BotGamePhase.MID) {
      return 8;
    }
    return 0;
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
