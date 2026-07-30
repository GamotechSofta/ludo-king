package com.ludo.backend.bot;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.toExit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotDynamicAiEngineTest {

  @Test
  void detectsMode1TwoPlayer() {
    GameSnapshot snap = baseSnap(new boolean[] {true, false}, List.of("GREEN", "YELLOW"));
    assertEquals(BotAiMode.MODE_1, BotMatchAnalysis.detectMode(snap));
  }

  @Test
  void detectsMode2OneHumanThreeBots() {
    GameSnapshot snap =
        baseSnap(
            new boolean[] {false, true, true, true},
            List.of("GREEN", "YELLOW", "BLUE", "RED"));
    assertEquals(BotAiMode.MODE_2, BotMatchAnalysis.detectMode(snap));
  }

  @Test
  void detectsMode4ThreeHumansOneBot() {
    GameSnapshot snap =
        baseSnap(
            new boolean[] {true, false, false, false},
            List.of("GREEN", "YELLOW", "BLUE", "RED"));
    assertEquals(BotAiMode.MODE_4, BotMatchAnalysis.detectMode(snap));
  }

  @Test
  void earlyPhaseWhenMostlyJailed() {
    GameSnapshot snap = baseSnap(new boolean[] {true, false}, List.of("GREEN", "YELLOW"));
    Map<String, List<Integer>> pos = new HashMap<>();
    pos.put("GREEN", Arrays.asList(JAIL, JAIL, JAIL, JAIL));
    pos.put("YELLOW", Arrays.asList(JAIL, JAIL, JAIL, JAIL));
    snap.setTokenPositions(pos);
    double tp = BotMatchAnalysis.tableProgressRatio(snap);
    assertEquals(BotGamePhase.EARLY, BotMatchAnalysis.detectPhase(snap, tp));
  }

  @Test
  void endPrefersHomeFinishOverWeakCapture() {
    LudoColor green = LudoColor.GREEN;
    int lastExit = toExit(EXIT_LEN - 1);
    List<Integer> own = Arrays.asList(lastExit, 5);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(6, JAIL, JAIL, JAIL));

    BotMatchAnalysis a =
        analysis(
            BotAiMode.MODE_1,
            BotGamePhase.END,
            0,
            new boolean[] {true, false},
            1,
            new int[] {100, 80},
            true);

    long finish =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 0, lastExit, HOME, 1);
    long capture =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 1, 5, 6, 1);
    assertTrue(finish > capture, "END: finish before unnecessary fights");
  }

  @Test
  void earlyCaptureAggressionLowerThanEnd() {
    LudoColor green = LudoColor.GREEN;
    List<Integer> own = Arrays.asList(5, JAIL, JAIL, JAIL);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(6, JAIL, JAIL, JAIL));

    BotMatchAnalysis early =
        analysis(
            BotAiMode.MODE_1,
            BotGamePhase.EARLY,
            0,
            new boolean[] {true, false},
            1,
            new int[] {10, 12},
            true);
    BotMatchAnalysis end =
        analysis(
            BotAiMode.MODE_1,
            BotGamePhase.END,
            0,
            new boolean[] {true, false},
            1,
            new int[] {10, 200},
            true);

    long e =
        BotMoveScoringEngine.scoreMove(
            early, green, 0, own, all, List.of("GREEN", "YELLOW"), 0, 5, 6, 1);
    long n =
        BotMoveScoringEngine.scoreMove(
            end, green, 0, own, all, List.of("GREEN", "YELLOW"), 0, 5, 6, 1);
    assertTrue(n > e, "END capture weight > EARLY");
  }

  @Test
  void aggressionRatesMode1() {
    BotMatchAnalysis early =
        analysis(BotAiMode.MODE_1, BotGamePhase.EARLY, 0, new boolean[] {true, false}, 1, new int[] {0, 0}, true);
    BotMatchAnalysis mid =
        analysis(BotAiMode.MODE_1, BotGamePhase.MID, 0, new boolean[] {true, false}, 1, new int[] {0, 0}, true);
    BotMatchAnalysis end =
        analysis(BotAiMode.MODE_1, BotGamePhase.END, 0, new boolean[] {true, false}, 1, new int[] {0, 0}, true);
    assertEquals(0.20, BotAggressionPolicy.captureAssistProbability(early, true));
    assertEquals(0.50, BotAggressionPolicy.captureAssistProbability(mid, true));
    assertEquals(0.70, BotAggressionPolicy.captureAssistProbability(end, true));
  }

  @Test
  void midPrefersSafeOverDanger() {
    LudoColor green = LudoColor.GREEN;
    List<Integer> own = Arrays.asList(1, 20);
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", own);
    all.put("YELLOW", Arrays.asList(0, JAIL, JAIL, JAIL));

    BotMatchAnalysis a =
        analysis(
            BotAiMode.MODE_1,
            BotGamePhase.MID,
            0,
            new boolean[] {true, false},
            1,
            new int[] {40, 50},
            true);

    long danger =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 0, 1, 3, 2);
    long safer =
        BotMoveScoringEngine.scoreMove(
            a, green, 0, own, all, List.of("GREEN", "YELLOW"), 1, 20, 21, 1);
    assertTrue(safer > danger);
  }

  private static GameSnapshot baseSnap(boolean[] isBot, List<String> colors) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(isBot);
    snap.setSeatColors(colors);
    Map<String, List<Integer>> pos = new HashMap<>();
    for (String c : colors) {
      pos.put(c, Arrays.asList(JAIL, JAIL, JAIL, JAIL));
    }
    snap.setTokenPositions(pos);
    return snap;
  }

  private static BotMatchAnalysis analysis(
      BotAiMode mode,
      BotGamePhase phase,
      int botSeat,
      boolean[] isBot,
      int leaderSeat,
      int[] progress,
      boolean allowHunt
  ) {
    int humans = 0;
    int bots = 0;
    for (boolean b : isBot) {
      if (b) {
        bots++;
      } else {
        humans++;
      }
    }
    return new BotMatchAnalysis(
        mode,
        phase,
        BotDifficulty.HARD,
        botSeat,
        humans,
        bots,
        isBot.length,
        isBot,
        leaderSeat,
        progress,
        new int[isBot.length],
        new int[isBot.length],
        phase == BotGamePhase.EARLY ? 0.1 : phase == BotGamePhase.MID ? 0.45 : 0.8,
        progress[botSeat] + 40 < progress[leaderSeat],
        botSeat == leaderSeat,
        allowHunt);
  }
}
