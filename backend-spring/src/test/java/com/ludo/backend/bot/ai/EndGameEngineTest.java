package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EndGameEngineTest {

  private EndGameConfig config;
  private EndGameEngine engine;
  private EndGameAnalyzer analyzer;
  private FinishPlanner finishPlanner;
  private WinningPathCalculator pathCalculator;
  private RiskAnalyzer riskAnalyzer;

  @BeforeEach
  void setUp() {
    config = new EndGameConfig(true, 20, 4, 0.70, 1.40, 0.35, 0.25);
    analyzer = new EndGameAnalyzer(config);
    finishPlanner = new FinishPlanner();
    pathCalculator = new WinningPathCalculator();
    riskAnalyzer = new RiskAnalyzer();
    engine = new EndGameEngine(config, analyzer, finishPlanner, pathCalculator, riskAnalyzer);
  }

  @Test
  void activatesOnTwoFinishedPawns() {
    BotMatchAnalysis a = analysis(BotGamePhase.MID, 0.4, new int[] {2, 0}, false, true);
    EndGameAnalyzer.Activation act =
        analyzer.detect(BotDifficulty.HARD, a, null, List.of(HOME, HOME, 10, JAIL), LudoColor.GREEN);
    assertTrue(act.active());
    assertTrue(act.reason().contains("Finished"));
  }

  @Test
  void activatesOnHomePath() {
    int exit = toExit(2);
    BotMatchAnalysis a = analysis(BotGamePhase.MID, 0.4, new int[] {0, 0}, false, false);
    EndGameAnalyzer.Activation act =
        analyzer.detect(
            BotDifficulty.HARD, a, null, List.of(exit, JAIL, JAIL, JAIL), LudoColor.GREEN);
    assertTrue(act.active());
    assertTrue(act.reason().contains("Home Path"));
  }

  @Test
  void activatesOnLowRaceRemaining() {
    BotMatchAnalysis a = analysis(BotGamePhase.MID, 0.80, new int[] {1, 1}, false, false);
    EndGameAnalyzer.Activation act =
        analyzer.detect(BotDifficulty.HARD, a, null, List.of(20, JAIL, JAIL, JAIL), LudoColor.GREEN);
    assertTrue(act.active());
  }

  @Test
  void easyBotSkipped() {
    BotMatchAnalysis a = analysis(BotGamePhase.END, 0.9, new int[] {3, 0}, true, true);
    EndGameAnalyzer.Activation act =
        analyzer.detect(BotDifficulty.EASY, a, null, List.of(HOME, HOME, HOME, 5), LudoColor.GREEN);
    assertFalse(act.active());
  }

  @Test
  void exactFinishOutranksCapture() {
    MoveCandidate finish =
        move(0, toExit(4), HOME, MoveType.HOME_FINISH, false, false);
    MoveCandidate capture =
        move(1, 10, 14, MoveType.CAPTURE, true, false);
    List<MoveCandidate> cands = List.of(finish, capture);
    EndGameProfile profile =
        engine.evaluate(
            BotDifficulty.HARD,
            analysis(BotGamePhase.END, 0.85, new int[] {3, 1}, false, true),
            null,
            List.of(toExit(4), 10, JAIL, JAIL),
            LudoColor.GREEN,
            cands,
            null,
            null,
            null,
            null);
    assertTrue(profile.active());
    assertTrue(profile.anyExactFinishAvailable());
    EndGameDecision fd = profile.forMove(finish);
    EndGameDecision cd = profile.forMove(capture);
    assertEquals(FinishPriority.EXACT_FINISH, fd.priority());
    assertTrue(fd.scoreDelta() > cd.scoreDelta(), fd.scoreDelta() + " vs " + cd.scoreDelta());
    assertTrue(fd.winningProbability() >= cd.winningProbability());
  }

  @Test
  void homePathProtectPreferredOverRiskyCapture() {
    int exit = toExit(3);
    MoveCandidate protect =
        move(0, exit, toExit(4), MoveType.HOME_COLUMN, false, true);
    MoveCandidate risky =
        move(0, exit, 22, MoveType.CAPTURE, true, true);
    // Force risky destination classification by high threat on capture move
    MoveCandidate riskyCap =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            4,
            0,
            exit,
            22,
            MoveType.CAPTURE,
            true,
            2,
            true,
            1,
            false,
            40,
            false,
            150,
            false,
            false);
    EndGameProfile profile =
        engine.evaluate(
            BotDifficulty.HARD,
            analysis(BotGamePhase.END, 0.8, new int[] {2, 1}, false, true),
            null,
            List.of(exit, JAIL, JAIL, JAIL),
            LudoColor.GREEN,
            List.of(protect, riskyCap),
            null,
            null,
            null,
            null);
    EndGameDecision pd = profile.forMove(protect);
    EndGameDecision rd = profile.forMove(riskyCap);
    assertTrue(pd.scoreDelta() > rd.scoreDelta(), pd.debugLine(false) + " | " + rd.debugLine(false));
  }

  @Test
  void winningPathMinimizesExpectedTurns() {
    int near = toExit(4);
    // Three finished — exact finish ends the match
    List<Integer> own = List.of(near, HOME, HOME, HOME);
    MoveCandidate nearFinish = move(0, near, HOME, MoveType.HOME_FINISH, false, false);
    MoveCandidate leaveOnExit =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            0,
            0,
            near,
            toExit(3),
            MoveType.HOME_COLUMN,
            false,
            0,
            false,
            -1,
            false,
            Integer.MAX_VALUE,
            false,
            200,
            false,
            false);
    double finishTurns = pathCalculator.expectedTurnsAfter(LudoColor.GREEN, own, nearFinish);
    double otherTurns = pathCalculator.expectedTurnsAfter(LudoColor.GREEN, own, leaveOnExit);
    assertEquals(0.0, finishTurns, 0.001);
    assertTrue(otherTurns > finishTurns, finishTurns + " vs " + otherTurns);
  }

  @Test
  void riskClassifierMarksHomeVerySafe() {
    MoveCandidate home = move(0, toExit(4), HOME, MoveType.HOME_FINISH, false, false);
    assertEquals(EndGameRisk.VERY_SAFE, riskAnalyzer.classify(home, null, LudoColor.GREEN, true));
  }

  @Test
  void controlledRiskWhenBehindSoftensPenalty() {
    int soft = riskAnalyzer.riskScoreDelta(EndGameRisk.RISKY, true, 15);
    int hard = riskAnalyzer.riskScoreDelta(EndGameRisk.RISKY, false, 0);
    assertTrue(soft > hard, soft + " vs " + hard);
  }

  @Test
  void applyAddsScoreDelta() {
    MoveCandidate finish =
        move(0, toExit(4), HOME, MoveType.HOME_FINISH, false, false);
    EndGameProfile profile =
        engine.evaluate(
            BotDifficulty.HARD,
            analysis(BotGamePhase.END, 0.9, new int[] {3, 0}, true, true),
            null,
            List.of(toExit(4), JAIL, JAIL, JAIL),
            LudoColor.GREEN,
            List.of(finish),
            null,
            null,
            null,
            null);
    MoveScore score = new MoveScore();
    engine.apply(score, finish, profile);
    assertTrue(score.total() > 0, score.toString());
  }

  @Test
  void futureDepthUpToFour() {
    EndGameProfile p =
        new EndGameProfile(
            true, "test", 2, 1, 10, 0.2, true, false, false, 1.4, 0.7, 0.35, 4, List.of());
    assertEquals(4, engine.effectiveFutureDepth(p, 3));
  }

  @Test
  void diceBiasNeverCapture() {
    EndGameProfile p =
        new EndGameProfile(
            true, "test", 2, 1, 10, 0.2, false, false, true, 1.4, 0.7, 0.35, 4, List.of());
    assertTrue(engine.diceOutcomeBias(p, "home") > 0);
    assertTrue(engine.diceOutcomeBias(p, "escape") > 0);
    assertEquals(0, engine.diceOutcomeBias(p, "capture"));
    assertEquals(0, engine.diceOutcomeBias(p, "open"));
  }

  private static MoveCandidate move(
      int pawn, int from, int to, MoveType type, boolean capture, boolean threatFrom
  ) {
    return new MoveCandidate(
        new int[] {pawn, 0},
        pawn,
        1,
        0,
        from,
        to,
        type,
        threatFrom,
        0,
        capture,
        capture ? 1 : -1,
        false,
        20,
        false,
        80,
        false,
        false);
  }

  private static BotMatchAnalysis analysis(
      BotGamePhase phase,
      double tableProgress,
      int[] finished,
      boolean behind,
      boolean leader
  ) {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        phase,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        leader ? 0 : 1,
        new int[] {100, 80},
        finished,
        new int[] {2, 2},
        tableProgress,
        behind,
        leader,
        true);
  }
}
