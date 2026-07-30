package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production End Game Master Strategy Engine (HARD only).
 *
 * <p>Maximizes win probability — exact finish &gt; home entry &gt; protection &gt; captures.
 * Never changes legality or invents dice.
 */
@Component
public class EndGameEngine {

  private static final Logger log = LoggerFactory.getLogger(EndGameEngine.class);

  private final EndGameConfig config;
  private final EndGameAnalyzer analyzer;
  private final FinishPlanner finishPlanner;
  private final WinningPathCalculator pathCalculator;
  private final RiskAnalyzer riskAnalyzer;

  public EndGameEngine(
      EndGameConfig config,
      EndGameAnalyzer analyzer,
      FinishPlanner finishPlanner,
      WinningPathCalculator pathCalculator,
      RiskAnalyzer riskAnalyzer
  ) {
    this.config = config;
    this.analyzer = analyzer;
    this.finishPlanner = finishPlanner;
    this.pathCalculator = pathCalculator;
    this.riskAnalyzer = riskAnalyzer;
  }

  public boolean enabled() {
    return config.enabled();
  }

  /**
   * Build endgame profile for this turn (activation + per-move decisions).
   * Reuses danger / pawn / opponent data — no duplicate board scans beyond cheap math.
   */
  public EndGameProfile evaluate(
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      BoardAnalysisCache cache,
      List<Integer> ownPositions,
      LudoColor color,
      List<MoveCandidate> candidates,
      DangerMap dangerMap,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality
  ) {
    long t0 = System.nanoTime();
    EndGameAnalyzer.Activation act =
        analyzer.detect(difficulty, analysis, cache, ownPositions, color);
    if (!act.active()) {
      return EndGameProfile.inactive();
    }

    boolean leading =
        (analysis != null && analysis.botIsLeader)
            || (adaptive != null && adaptive.enabled() && adaptive.status() == BotStatus.LEADING);
    boolean behind =
        (analysis != null && analysis.botBehind)
            || (adaptive != null
                && adaptive.enabled()
                && (adaptive.status() == BotStatus.BEHIND
                    || adaptive.status() == BotStatus.CRITICAL));

    int botFinished =
        analysis != null
                && analysis.finishedPawns != null
                && analysis.botSeat < analysis.finishedPawns.length
            ? analysis.finishedPawns[analysis.botSeat]
            : (cache != null ? cache.finishedCount() : 0);
    int maxOpp = 0;
    if (analysis != null && analysis.finishedPawns != null) {
      for (int i = 0; i < analysis.finishedPawns.length; i++) {
        if (i != analysis.botSeat) {
          maxOpp = Math.max(maxOpp, analysis.finishedPawns[i]);
        }
      }
    }

    double homeBias = config.homeBias();
    double safeBias = config.safeBias();
    double captureBias = config.captureBias();
    // Dynamic priority: leading → safer; behind → controlled risk
    if (leading) {
      safeBias = Math.min(1.5, safeBias + 0.15);
      captureBias = Math.max(0.15, captureBias - 0.10);
      homeBias = Math.min(2.0, homeBias + 0.10);
    } else if (behind) {
      captureBias = Math.min(0.70, captureBias + 0.15);
      safeBias = Math.max(0.40, safeBias - 0.05);
    }
    // Personality soft-tint (all converge toward finish — engine still owns finish priority)
    if (personality != null && personality.enabled()) {
      BehaviorWeights w = personality.weights();
      homeBias *= (0.85 + 0.15 * w.home());
      safeBias *= (0.85 + 0.15 * w.safe());
      captureBias *= (0.70 + 0.30 * w.capture());
    }

    boolean anyFinish = finishPlanner.hasExactFinish(candidates);
    List<EndGameDecision> decisions = new ArrayList<>();
    if (candidates != null) {
      double baselineTurns =
          pathCalculator.expectedTurnsAfter(color, ownPositions, null);
      for (MoveCandidate c : candidates) {
        decisions.add(
            evaluateMove(
                c,
                color,
                ownPositions,
                dangerMap,
                opponents,
                leading,
                behind,
                botFinished,
                maxOpp,
                anyFinish,
                baselineTurns,
                homeBias,
                safeBias,
                captureBias));
      }
    }

    EndGameProfile profile =
        new EndGameProfile(
            true,
            act.reason(),
            botFinished,
            maxOpp,
            act.remainingMoves(),
            act.raceRemaining(),
            leading,
            behind,
            anyFinish,
            homeBias,
            safeBias,
            captureBias,
            config.futureDepth(),
            decisions);

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 3_000L && log.isDebugEnabled()) {
      log.debug("EndGameEngine {}µs (budget 3000µs)", us);
    }
    return profile;
  }

  /** Apply endgame deltas onto a move score. */
  public void apply(MoveScore score, MoveCandidate move, EndGameProfile profile) {
    if (score == null || move == null || profile == null || !profile.active()) {
      return;
    }
    EndGameDecision d = profile.forMove(move);
    if (d != null && d.scoreDelta() != 0) {
      score.add("EndGame " + d.priority().name(), d.scoreDelta());
    }
  }

  /** Future simulation depth when endgame is active (2–4). */
  public int futureDepthBoost(EndGameProfile profile) {
    if (profile == null || !profile.active()) {
      return 0;
    }
    return Math.max(0, profile.futureDepth() - 3);
  }

  public int effectiveFutureDepth(EndGameProfile profile, int baseDepth) {
    if (profile == null || !profile.active()) {
      return baseDepth;
    }
    return Math.min(4, Math.max(baseDepth, profile.futureDepth()));
  }

  /**
   * Smart-dice outcome bias: home / exact finish / escape only — never captures.
   */
  public int diceOutcomeBias(EndGameProfile profile, String outcomeKey) {
    if (profile == null || !profile.active() || outcomeKey == null) {
      return 0;
    }
    return switch (outcomeKey) {
      case "home" -> (int) Math.round(55 * (profile.homeBias() - 1.0) + 25);
      case "safe" -> (int) Math.round(30 * profile.safeBias());
      case "escape" -> (int) Math.round(35 * profile.safeBias());
      default -> 0; // never open/capture bias from endgame
    };
  }

  private EndGameDecision evaluateMove(
      MoveCandidate c,
      LudoColor color,
      List<Integer> own,
      DangerMap dangerMap,
      OpponentAnalysisReport opponents,
      boolean leading,
      boolean behind,
      int botFinished,
      int maxOpp,
      boolean anyFinish,
      double baselineTurns,
      double homeBias,
      double safeBias,
      double captureBias
  ) {
    FinishPriority priority = finishPlanner.classify(c, color, anyFinish);
    EndGameRisk risk = riskAnalyzer.classify(c, dangerMap, color, leading);
    double expected = pathCalculator.expectedTurnsAfter(color, own, c);
    boolean exact = priority == FinishPriority.EXACT_FINISH;
    int winProb =
        pathCalculator.winningProbability(
            expected, risk, leading, behind, maxOpp, botFinished, exact);

    int baselineProb =
        pathCalculator.winningProbability(
            baselineTurns,
            EndGameRisk.BALANCED,
            leading,
            behind,
            maxOpp,
            botFinished,
            false);
    int winGain = winProb - baselineProb;

    int delta = 0;
    String reason = priority.name();

    // Exact finish always wins — never delay for capture
    if (exact) {
      delta += (int) Math.round(220 * homeBias);
      reason = "Exact Finish";
    } else if (anyFinish) {
      delta -= 180; // hard prefer finishing now
      reason = "Defer Non-Finish";
    }

    if (priority == FinishPriority.HOME_ENTRY) {
      delta += (int) Math.round(110 * homeBias);
      reason = "Home Entry";
    }
    if (priority == FinishPriority.PROTECT_ADVANCED) {
      delta += (int) Math.round(95 * safeBias);
      reason = "Protect Advanced";
    }
    if (priority == FinishPriority.SAFE_CELL || isSafe(c.to())) {
      delta += (int) Math.round(55 * safeBias);
    }

    // Winning-path: minimize expected turns
    double turnGain = baselineTurns - expected;
    delta += (int) Math.round(turnGain * 28);
    delta += (int) Math.round(pathCalculator.safePathBonus(c.to()) * safeBias);

    // Capture only if it improves win probability
    if (c.capture()) {
      boolean sacrificeNearHome =
          finishPlanner.isNearHomePawn(color, c.from())
              && !isHome(c.to())
              && !isExit(c.to())
              && c.threatCountAtTo() > 0;
      boolean highValue =
          c.victimIsLeader()
              || (c.victimRemaining()
                  <= com.ludo.backend.game.BoardConstants.HOME_STEPS + 8)
              || (opponents != null
                  && opponents.enabled()
                  && c.victimSeat() == opponents.primaryTargetSeat());
      if (sacrificeNearHome && !highValue) {
        delta -= (int) Math.round(140 * homeBias);
        reason = "Reject Near-Home Sacrifice";
      } else if (winGain >= 5 || highValue) {
        delta += (int) Math.round(70 * captureBias * (highValue ? 1.4 : 1.0));
        reason = highValue ? "Win-Prob Capture" : "Useful Capture";
      } else {
        delta -= (int) Math.round(50 * (1.0 - captureBias));
        reason = "Low-Value Capture";
      }
    }

    // Leader response: slow only if mathematically beneficial
    if (c.capture()
        && c.victimIsLeader()
        && maxOpp >= 2
        && winGain >= 4) {
      delta += (int) Math.round(45 * captureBias);
      reason = "Leader Response";
    }

    delta += riskAnalyzer.riskScoreDelta(risk, behind, winGain);

    // Home-path defence
    if (isExit(c.from()) || isExit(c.to())) {
      if (risk.ordinal() >= EndGameRisk.RISKY.ordinal()) {
        delta -= (int) Math.round(60 * safeBias);
      } else {
        delta += (int) Math.round(40 * homeBias);
      }
    }

    return new EndGameDecision(c, priority, expected, winProb, risk, delta, reason);
  }
}
