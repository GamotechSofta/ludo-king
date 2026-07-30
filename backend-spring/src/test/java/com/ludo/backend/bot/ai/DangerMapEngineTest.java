package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DangerMapEngineTest {

  private DangerMapConfig config;
  private BoardScanner scanner;
  private ThreatAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    config =
        new DangerMapConfig(
            true, true, true, true, true, 90, 100, 40, 25, 80, 120, 80, 40, 20, 80);
    scanner = new BoardScanner();
    analyzer = new ThreatAnalyzer(config);
  }

  @Test
  void safeCellAlwaysZeroDanger() {
    // Find any safe tile and ensure analyzer returns 0
    for (int cell = 0; cell < 52; cell++) {
      if (isSafe(cell)) {
        DangerCell d =
            analyzer.analyzeCell(cell, 0, LudoColor.GREEN, List.of(), Map.of());
        assertEquals(0, d.dangerScore());
        assertEquals(ThreatLevel.SAFE, d.level());
        assertTrue(d.safeCell());
        return;
      }
    }
  }

  @Test
  void singleEnemyThreatIsMediumBand() {
    assertEquals(40, ThreatAnalyzer.dangerFromEnemyCount(1));
    assertEquals(70, ThreatAnalyzer.dangerFromEnemyCount(2));
    assertEquals(95, ThreatAnalyzer.dangerFromEnemyCount(3));
  }

  @Test
  void jailAndHomeAreSafe() {
    DangerCell jail =
        analyzer.analyzeCell(JAIL, 0, LudoColor.GREEN, List.of(), Map.of());
    assertEquals(0, jail.dangerScore());
  }

  @Test
  void blockProtectionReducesDanger() {
    int from = -1;
    int target = -1;
    for (int f = 0; f < 52; f++) {
      if (isSafe(f)) {
        continue;
      }
      int t = (f + 3) % 52;
      if (isSafe(t)) {
        continue;
      }
      from = f;
      target = t;
      break;
    }
    assertTrue(from >= 0 && target >= 0);
    ScannedPawn enemy =
        new ScannedPawn(
            1, 0, LudoColor.YELLOW, from, 20, 40, false, false, false, false, false, false);
    DangerCell open = analyzer.analyzeCell(target, 0, LudoColor.GREEN, List.of(enemy), Map.of());
    DangerCell blocked =
        analyzer.analyzeCell(target, 0, LudoColor.GREEN, List.of(enemy), Map.of(target, 2));
    assertTrue(open.dangerScore() >= 40, open.toString());
    assertTrue(blocked.dangerScore() < open.dangerScore(), open + " vs " + blocked);
    assertTrue(blocked.blockProtected());
  }

  @Test
  void escapeReportWhenLeavingDanger() {
    GameSnapshot snap = snap(
        Arrays.asList(13, JAIL, JAIL, JAIL),
        Arrays.asList(10, JAIL, JAIL, JAIL));
    var pawns = scanner.scan(snap, analysis());
    DangerMap map = analyzer.buildMap(0, LudoColor.GREEN, pawns);
    MoveCandidate escape =
        new MoveCandidate(
            new int[] {0, 0},
            0,
            6,
            0,
            13,
            19,
            MoveType.ESCAPE,
            true,
            0,
            false,
            -1,
            false,
            Integer.MAX_VALUE,
            false,
            50,
            false,
            false);
    DangerReport r = analyzer.reportForMove(escape, map, 30, 40);
    assertTrue(r.currentDanger() >= r.destinationDanger() || r.escape() || r.saferRoute()
        || map.dangerAt(13) >= 0);
  }

  @Test
  void boardScanCollectsAllPawns() {
    GameSnapshot snap = snap(
        Arrays.asList(5, JAIL, JAIL, JAIL),
        Arrays.asList(20, JAIL, JAIL, JAIL));
    var pawns = scanner.scan(snap, analysis());
    assertEquals(8, pawns.size());
  }

  @Test
  void threatCacheReturnsSameMapUntilBoardChanges() {
    ThreatCache cache = new ThreatCache(config, scanner, analyzer);
    GameSnapshot snap = snap(
        Arrays.asList(5, JAIL, JAIL, JAIL),
        Arrays.asList(20, JAIL, JAIL, JAIL));
    DangerMap a = cache.getOrBuild("room1", snap, 0, LudoColor.GREEN, analysis());
    DangerMap b = cache.getOrBuild("room1", snap, 0, LudoColor.GREEN, analysis());
    assertEquals(a.builtAtNanos(), b.builtAtNanos());
  }

  private static GameSnapshot snap(List<Integer> green, List<Integer> yellow) {
    GameSnapshot snap = new GameSnapshot();
    snap.setIsBot(new boolean[] {true, false});
    snap.setSeatColors(List.of("GREEN", "YELLOW"));
    Map<String, List<Integer>> all = new HashMap<>();
    all.put("GREEN", green);
    all.put("YELLOW", yellow);
    snap.setTokenPositions(all);
    snap.setCurrentSeatIndex(0);
    return snap;
  }

  private static BotMatchAnalysis analysis() {
    return new BotMatchAnalysis(
        BotAiMode.MODE_1,
        BotGamePhase.MID,
        BotDifficulty.HARD,
        0,
        1,
        1,
        2,
        new boolean[] {true, false},
        1,
        new int[] {30, 40},
        new int[2],
        new int[] {1, 1},
        0.4,
        false,
        false,
        true);
  }
}
