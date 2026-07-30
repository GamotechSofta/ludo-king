package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import org.springframework.stereotype.Component;

/**
 * Pure scoring: {@link MoveCandidate} + cached board → {@link MoveScore}.
 *
 * <p>Central reusable brain for HARD-bot move selection and future AI modules.
 * Always consults {@link DangerMap} when provided (HARD path).
 */
@Component
public class MoveScoreCalculator {

  private final AIScoreConfig config;
  private final DangerMapConfig dangerConfig;
  private final ThreatAnalyzer threatAnalyzer;
  private final PawnValueConfig pawnValueConfig;
  private final PawnDecision pawnDecision;
  private final OpponentAnalysisConfig opponentConfig;
  private final DecisionModifier decisionModifier;
  private final EndGameEngine endGameEngine;
  private final HumanBehaviorEngine humanBehaviorEngine;

  public MoveScoreCalculator(
      AIScoreConfig config,
      DangerMapConfig dangerConfig,
      ThreatAnalyzer threatAnalyzer,
      PawnValueConfig pawnValueConfig,
      PawnDecision pawnDecision,
      OpponentAnalysisConfig opponentConfig,
      DecisionModifier decisionModifier,
      EndGameEngine endGameEngine,
      HumanBehaviorEngine humanBehaviorEngine
  ) {
    this.config = config;
    this.dangerConfig = dangerConfig;
    this.threatAnalyzer = threatAnalyzer;
    this.pawnValueConfig = pawnValueConfig;
    this.pawnDecision = pawnDecision;
    this.opponentConfig = opponentConfig;
    this.decisionModifier = decisionModifier;
    this.endGameEngine = endGameEngine;
    this.humanBehaviorEngine = humanBehaviorEngine;
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history
  ) {
    return score(c, cache, analysis, roomId, history, null, null, null, null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap
  ) {
    return score(c, cache, analysis, roomId, history, dangerMap, null, null, null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues
  ) {
    return score(c, cache, analysis, roomId, history, dangerMap, pawnValues, null, null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    return score(c, cache, analysis, roomId, history, dangerMap, pawnValues, opponents, null, null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive
  ) {
    return score(
        c, cache, analysis, roomId, history, dangerMap, pawnValues, opponents, adaptive, null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality
  ) {
    return score(
        c,
        cache,
        analysis,
        roomId,
        history,
        dangerMap,
        pawnValues,
        opponents,
        adaptive,
        personality,
        null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame
  ) {
    return score(
        c,
        cache,
        analysis,
        roomId,
        history,
        dangerMap,
        pawnValues,
        opponents,
        adaptive,
        personality,
        endGame,
        null);
  }

  public MoveScore score(
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMatchAnalysis analysis,
      String roomId,
      BotMoveHistoryStore history,
      DangerMap dangerMap,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents,
      DifficultyProfile adaptive,
      PersonalityProfile personality,
      EndGameProfile endGame,
      BehaviorProfile behavior
  ) {
    MoveScore s = new MoveScore();
    applyHome(s, c, cache);
    applySafe(s, c, cache);
    applyEscape(s, c);
    applyOpenPawn(s, c, cache, analysis);
    applyCapture(s, c, analysis, opponents);
    applyProtectAdvanced(s, c, cache);
    applyAdvanceStrong(s, c, cache);
    applyBoardControl(s, c, cache, history, roomId);
    applyBlock(s, c);
    if (dangerMap == null || !dangerConfig.enabled()) {
      applyRisk(s, c);
    } else {
      applyDangerMap(s, c, cache, dangerMap);
    }
    applyLeaveSafe(s, c, cache);
    applyUseless(s, c);
    applyRepeat(s, c, history, roomId);
    applyLeader(s, c, opponents);
    applyEndgame(s, c, cache, analysis);
    applyOpening(s, c, cache, history, roomId, analysis);
    applyPawnValueIntegration(s, c, cache, dangerMap, pawnValues);
    applyOpponentStrategy(s, c, analysis, opponents, dangerMap);
    applyAdaptiveDifficulty(s, c, adaptive);
    applyPersonality(s, c, personality);
    applyEndGameMaster(s, c, endGame);
    applyHumanBehavior(s, c, behavior);
    return s;
  }

  private void applyHumanBehavior(MoveScore s, MoveCandidate c, BehaviorProfile behavior) {
    if (humanBehaviorEngine == null || behavior == null || !behavior.enabled()) {
      return;
    }
    humanBehaviorEngine.apply(s, c, behavior, config);
  }

  private void applyEndGameMaster(MoveScore s, MoveCandidate c, EndGameProfile endGame) {
    if (endGame == null || !endGame.active() || endGameEngine == null) {
      return;
    }
    endGameEngine.apply(s, c, endGame);
  }

  private void applyPersonality(
      MoveScore s, MoveCandidate c, PersonalityProfile personality
  ) {
    if (personality == null || !personality.enabled() || decisionModifier == null) {
      return;
    }
    decisionModifier.apply(s, c, personality);
  }

  private void applyAdaptiveDifficulty(
      MoveScore s, MoveCandidate c, DifficultyProfile adaptive
  ) {
    if (adaptive == null || !adaptive.enabled()) {
      return;
    }
    if (c.capture()) {
      if (adaptive.reduceSideCaptures() && !c.victimIsLeader()) {
        s.add("Adaptive Skip Side Capture", -Math.max(20, adaptive.captureWeightDelta()));
      } else if (adaptive.captureWeightDelta() != 0) {
        s.add("Adaptive Capture Weight", adaptive.captureWeightDelta());
      }
    }
    if (isSafe(c.to()) && adaptive.safeWeightDelta() != 0) {
      s.add("Adaptive Safe Weight", adaptive.safeWeightDelta());
    }
    if (c.underThreatAtFrom()
        && c.threatCountAtTo() == 0
        && adaptive.escapeWeightDelta() != 0) {
      s.add("Adaptive Escape Weight", adaptive.escapeWeightDelta());
    }
    if ((isHome(c.to()) || isExit(c.to())) && adaptive.homeWeightDelta() != 0) {
      s.add("Adaptive Home Weight", adaptive.homeWeightDelta());
    }
    if (c.underThreatAtFrom() && adaptive.protectWeightDelta() != 0) {
      s.add("Adaptive Protect Weight", adaptive.protectWeightDelta() / 2);
    }
    if (c.threatCountAtTo() > 0
        && !isSafe(c.to())
        && !isHome(c.to())
        && adaptive.riskWeightDelta() != 0) {
      s.add("Adaptive Risk Weight", -Math.abs(adaptive.riskWeightDelta()));
    }
  }

  private void applyPawnValueIntegration(
      MoveScore s,
      MoveCandidate c,
      BoardAnalysisCache cache,
      DangerMap dangerMap,
      PawnValueReport pawnValues
  ) {
    if (pawnValueConfig == null || !pawnValueConfig.enabled()) {
      if (dangerMap == null || !dangerConfig.enabled()) {
        applyPawnValueRisk(s, c);
      }
      return;
    }
    PawnPriority priority =
        pawnValues != null ? pawnValues.get(c.pawnIndex()) : null;
    if (priority == null) {
      applyPawnValueRisk(s, c);
      return;
    }
    int destDanger = c.threatCountAtTo() > 0 ? 40 + c.threatCountAtTo() * 30 : 0;
    if (dangerMap != null && dangerConfig.enabled()) {
      destDanger = dangerMap.dangerAt(c.to());
      if (isSafe(c.to()) || isHome(c.to())) {
        destDanger = 0;
      }
    }
    pawnDecision.applyToMoveScore(s, c, priority, destDanger);

    // Opening: prefer releasing / diversifying low-value jail pawns when few active
    if (priority.state() != null
        && priority.state().jail()
        && c.diceValue() == 6
        && cache.activeCount() < 3) {
      s.add("Opening Low-Value Release", 25);
    }

    // Sacrifice awareness: moving a never-sacrifice pawn into danger is extra bad
    if (priority.neverSacrifice() && destDanger >= 70) {
      s.add("Never Sacrifice Near-Home", -80);
    }
  }

  /**
   * Integrates Danger Map: destination danger, escape, trap, future threat, safe route.
   * Bot must never ignore this when the map is present.
   */
  private void applyDangerMap(
      MoveScore s, MoveCandidate c, BoardAnalysisCache cache, DangerMap map
  ) {
    int prog = BotBoardMath.pawnProgress(cache.color(), c.from());
    DangerReport report =
        threatAnalyzer.reportForMove(c, map, prog, cache.bestOwnProgress());
    int dest = report.destinationDanger();
    ThreatLevel level = ThreatLevel.fromScore(dest);

    int band = dangerConfig.scoreDeltaForDanger(dest);
    s.add("DangerMap " + level + " (" + dest + ")", band);

    if (report.escape()) {
      s.add("Danger Escape", dangerConfig.escapeBonus());
    }
    if (report.trap()) {
      s.add("Trap Detected", -dangerConfig.trapPenalty());
    }
    DangerCell destCell = map.cell(c.to());
    if (dangerConfig.futureThreat()) {
      if (destCell.futureOneTurn() > 0 && dest < 40) {
        s.add("Future Threat 1T", -dangerConfig.futureOnePenalty() * destCell.futureOneTurn());
      }
      if (destCell.futureTwoTurn() > 1) {
        s.add("Future Threat 2T", -dangerConfig.futureTwoPenalty());
      }
    }
    if (dangerConfig.safeRoute() && report.saferRoute() && dest <= 20) {
      s.add("Safe Route", dangerConfig.safeRouteBonus() / 2);
    }
    // Survival over progress when leaving safety into critical danger
    if (report.currentDanger() <= 20 && dest >= 70) {
      s.add("Smart Defense", -60);
    }
  }

  private void applyHome(MoveScore s, MoveCandidate c, BoardAnalysisCache cache) {
    if (isHome(c.to())) {
      s.add("Home", config.homeBonus());
      return;
    }
    if (isExit(c.from()) || isExit(c.to())) {
      s.add("Home Stretch", config.homeStretchBonus());
    }
    int remFrom = BotBoardMath.remainingDistance(cache.color(), c.from());
    int remTo = BotBoardMath.remainingDistance(cache.color(), c.to());
    if (remFrom != Integer.MAX_VALUE && remTo != Integer.MAX_VALUE && remTo < remFrom) {
      s.add("Closer To Home", config.closerToHomeBonus());
    }
  }

  private void applySafe(MoveScore s, MoveCandidate c, BoardAnalysisCache cache) {
    if (isSafe(c.to())) {
      s.add("Safe Cell", config.safeBonus());
    } else if (c.threatCountAtTo() == 0 && !isJail(c.to()) && !isHome(c.to())) {
      // Destination not currently killable next turn
      if (cache.isThreatened(c.from())) {
        s.add("Becomes Safe", config.becomesSafeBonus());
      }
    }
  }

  private void applyEscape(MoveScore s, MoveCandidate c) {
    if (c.underThreatAtFrom() && c.threatCountAtTo() == 0) {
      s.add("Escape Danger", config.escapeBonus());
    }
  }

  private void applyOpenPawn(
      MoveScore s, MoveCandidate c, BoardAnalysisCache cache, BotMatchAnalysis analysis
  ) {
    if (!isJail(c.from()) || c.diceValue() != 6) {
      return;
    }
    int active = cache.activeCount();
    boolean needProtect =
        analysis != null
            && analysis.phase == BotGamePhase.END
            && cache.bestOwnProgress() >= BotBoardMath.MAX_PAWN_PROGRESS * 0.6;
    if (needProtect && active >= 2) {
      s.add("Skip Extra Open", -config.openPawnBonus() / 2);
      return;
    }
    if (active < 2) {
      s.add("Open New Pawn", config.openPawnBonus());
    } else if (active < 3) {
      s.add("Open New Pawn", config.openPawnBonus() * 3 / 4);
    }
  }

  private void applyCapture(
      MoveScore s, MoveCandidate c, BotMatchAnalysis analysis, OpponentAnalysisReport opponents
  ) {
    if (!c.capture()) {
      return;
    }
    boolean endIgnore =
        analysis != null
            && analysis.phase == BotGamePhase.END
            && analysis.botIsLeader
            && !c.victimIsLeader()
            && c.victimRemaining() > HOME_STEPS + 16;
    if (endIgnore) {
      s.add("Ignore Weak Capture", -config.captureBonus() / 2);
      return;
    }

    boolean isLeader =
        c.victimIsLeader()
            || (opponents != null && opponents.enabled() && opponents.isLeader(c.victimSeat()));
    boolean preferred =
        opponents != null && opponents.enabled() && opponents.isPreferredTarget(c.victimSeat());
    boolean ignore =
        opponents != null && opponents.enabled() && opponents.shouldIgnore(c.victimSeat());

    if (ignore && !isLeader) {
      s.add("Ignore Weak Player", -opponentConfig.ignoreWeakPenalty());
      return;
    }

    if (opponentConfig != null && opponentConfig.enabled() && isLeader) {
      s.add("Capture Leader", opponentConfig.captureLeaderBonus());
    } else if (isLeader) {
      s.add("Capture Leader", config.captureLeaderBonus());
    } else if (preferred && opponentConfig != null && opponentConfig.enabled()) {
      s.add("Capture Priority Target", config.captureBonus() + opponentConfig.targetThreatBonus());
    } else {
      s.add("Capture", config.captureBonus());
    }
    if (c.victimRemaining() <= HOME_STEPS + 10) {
      s.add("Capture Near Home", config.captureNearHomeBonus());
    }
    if (c.victimJustOut()) {
      s.add("Capture Just Out", config.captureJustOutBonus());
    }
    if (opponents != null && opponents.enabled()) {
      OpponentProfile vp = opponents.get(c.victimSeat());
      if (vp != null && vp.winningCritical()) {
        s.add("Stop Winning Player", opponentConfig.criticalThreeHomeBonus());
      }
      if (vp != null && vp.futureLeaderRisk()) {
        s.add("Stop Future Leader", opponentConfig.reduceLeaderBonus());
      }
    }
  }

  private void applyLeader(MoveScore s, MoveCandidate c, OpponentAnalysisReport opponents) {
    boolean isLeader =
        c.capture()
            && (c.victimIsLeader()
                || (opponents != null
                    && opponents.enabled()
                    && opponents.isLeader(c.victimSeat())));
    if (!isLeader) {
      return;
    }
    int bonus =
        opponentConfig != null && opponentConfig.enabled()
            ? opponentConfig.slowLeaderBonus()
            : config.slowLeaderBonus();
    s.add("Slow Leader", bonus);
    if (opponentConfig != null && opponentConfig.enabled()) {
      s.add("Reduce Leader Progress", opponentConfig.reduceLeaderBonus());
    }
  }

  private void applyOpponentStrategy(
      MoveScore s,
      MoveCandidate c,
      BotMatchAnalysis analysis,
      OpponentAnalysisReport opponents,
      DangerMap dangerMap
  ) {
    if (opponents == null || !opponents.enabled() || opponentConfig == null) {
      return;
    }
    boolean behind = opponents.botBehind() || (analysis != null && analysis.botBehind);
    boolean leading = opponents.botLeading() || (analysis != null && analysis.botIsLeader);

    if (behind && c.capture() && opponents.isPreferredTarget(c.victimSeat())) {
      s.add("Behind Attack Leader", opponentConfig.behindAggressionBonus());
    }
    if (leading && !c.capture() && c.threatCountAtTo() == 0) {
      s.add("Lead Defensive Play", opponentConfig.leadDefenseBonus() / 2);
    }
    if (leading && c.capture() && opponents.shouldIgnore(c.victimSeat())) {
      s.add("Lead Skip Side Fight", -opponentConfig.ignoreWeakPenalty() / 2);
    }

    // Leader pawn sitting in danger zone → higher attack opportunity already via capture;
    // if we're escaping while leader threatens, prefer safe.
    if (dangerMap != null && c.capture()) {
      OpponentProfile vp = opponents.get(c.victimSeat());
      if (vp != null && opponents.isLeader(vp.seat()) && !isSafe(c.to())) {
        // Capturing leader who was exposed — already scored; small boost if land safe
      }
    }

    // Endgame: leader one move from home → max attack; else home priority
    if (analysis != null && analysis.phase == BotGamePhase.END) {
      OpponentProfile leader = opponents.get(opponents.currentLeaderSeat());
      if (leader != null
          && leader.seat() != opponents.botSeat()
          && leader.leaderPawnProgress() >= BotBoardMath.MAX_PAWN_PROGRESS - 8) {
        if (c.capture() && c.victimSeat() == leader.seat()) {
          s.add("Endgame Stop Near-Home Leader", opponentConfig.criticalThreeHomeBonus());
        } else if (isHome(c.to())) {
          s.add("Endgame Race Home", config.homeBonus() / 4);
        }
      }
    }
  }

  private void applyProtectAdvanced(MoveScore s, MoveCandidate c, BoardAnalysisCache cache) {
    int prog = BotBoardMath.pawnProgress(cache.color(), c.from());
    boolean advanced = prog >= cache.bestOwnProgress() - 3 && prog >= 40;
    if (advanced && c.underThreatAtFrom() && c.threatCountAtTo() == 0) {
      s.add("Protect Advanced", config.protectAdvancedBonus());
    }
  }

  private void applyAdvanceStrong(MoveScore s, MoveCandidate c, BoardAnalysisCache cache) {
    if (c.pawnIndex() != cache.strongestPawnIndex()) {
      return;
    }
    if (c.threatCountAtTo() == 0 && !isJail(c.from())) {
      s.add("Advance Strong", config.advanceStrongBonus());
    }
  }

  private void applyBoardControl(
      MoveScore s,
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMoveHistoryStore history,
      String roomId
  ) {
    int activeAfter = cache.activeCount();
    if (isJail(c.from()) && c.diceValue() == 6) {
      activeAfter++;
    }
    if (activeAfter == 2 || activeAfter == 3) {
      s.add("Board Control 2-3", config.boardControlBonus());
    }
    if (history != null && history.lastWasPawn(roomId, c.pawnIndex()) && !isJail(c.from())) {
      s.add("Same Pawn Repeat", -config.samePawnRepeatPenalty());
    }
  }

  private void applyBlock(MoveScore s, MoveCandidate c) {
    if (!c.createsBlock()) {
      return;
    }
    s.add("Create Block", config.blockBonus());
    if (c.blockProtectsAdvanced()) {
      s.add("Block Protects Advanced", config.blockProtectsBonus());
    }
  }

  private void applyRisk(MoveScore s, MoveCandidate c) {
    int threats = c.threatCountAtTo();
    if (threats <= 0 || isHome(c.to()) || isSafe(c.to())) {
      return;
    }
    if (threats >= 2) {
      s.add("Multi Threat Risk", -config.multiThreatPenalty());
    } else {
      s.add("Risk Capture", -config.riskPenalty());
    }
    // Obvious: leaving safety into a kill window
    if (isSafe(c.from()) && threats > 0) {
      s.add("Obvious Danger", -config.obviousDangerPenalty());
    }
  }

  private void applyLeaveSafe(MoveScore s, MoveCandidate c, BoardAnalysisCache cache) {
    if (isSafe(c.from()) && !isSafe(c.to()) && c.threatCountAtTo() > 0) {
      s.add("Leave Safe Cell", -config.leaveSafePenalty());
    }
  }

  private void applyUseless(MoveScore s, MoveCandidate c) {
    boolean useful =
        c.capture()
            || isHome(c.to())
            || isSafe(c.to())
            || c.underThreatAtFrom()
            || c.createsBlock()
            || isJail(c.from())
            || isExit(c.from())
            || isExit(c.to());
    if (!useful && c.threatCountAtTo() == 0) {
      // Mild progress still has closer-to-home; only flag pure lateral / no gain
      s.add("Useless Move", -config.uselessMovePenalty() / 2);
    }
  }

  private void applyRepeat(
      MoveScore s, MoveCandidate c, BotMoveHistoryStore history, String roomId
  ) {
    if (history == null) {
      return;
    }
    int streak = history.consecutiveSamePawn(roomId, c.pawnIndex());
    if (streak >= config.continuousRepeatLimit()) {
      s.add("Repeated Move 5+", -config.continuousRepeatPenalty());
    }
  }

  private void applyEndgame(
      MoveScore s, MoveCandidate c, BoardAnalysisCache cache, BotMatchAnalysis analysis
  ) {
    if (cache.finishedCount() >= 3 && !isJail(c.from())) {
      s.add("Fourth Pawn Finish", config.endgameFourthBonus());
      if (isHome(c.to())) {
        s.add("Finish Match", config.homeBonus() / 3);
      }
      if (c.capture() && !c.victimIsLeader() && analysis != null && analysis.botIsLeader) {
        s.add("Skip Side Capture", -config.captureBonus());
      }
    }
  }

  private void applyOpening(
      MoveScore s,
      MoveCandidate c,
      BoardAnalysisCache cache,
      BotMoveHistoryStore history,
      String roomId,
      BotMatchAnalysis analysis
  ) {
    int turns = history != null ? history.turnCount(roomId) : 0;
    boolean opening =
        turns < config.openingTurnWindow()
            || (analysis != null && analysis.phase == BotGamePhase.EARLY);
    if (!opening) {
      return;
    }
    if (isJail(c.from()) && c.diceValue() == 6 && cache.activeCount() < 3) {
      s.add("Opening Spread", config.openPawnBonus() / 2);
    }
    if (isSafe(c.to())) {
      s.add("Opening Safe", config.safeBonus() / 5);
    }
    if (history != null && history.lastWasPawn(roomId, c.pawnIndex())) {
      s.add("Opening Avoid Repeat", -config.samePawnRepeatPenalty());
    }
  }

  private void applyPawnValueRisk(MoveScore s, MoveCandidate c) {
    if (c.threatCountAtTo() <= 0 || isHome(c.to()) || isSafe(c.to())) {
      return;
    }
    // Scale extra risk by pawn value (valuable pawns hurt more)
    int extra = Math.min(80, c.pawnValue() / 2);
    if (extra > 0) {
      s.add("Valuable Pawn Risk", -extra);
    }
  }
}
