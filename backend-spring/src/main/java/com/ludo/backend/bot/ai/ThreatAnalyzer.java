package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.game.LudoColor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Computes per-cell danger scores (0–100) from scanned enemy pawns.
 *
 * <p>Formula (immediate): enemy seats that can land on cell with dice 1–6 →
 * 1→40, 2→70, 3+→95; leader adds +10; block reduces; safe/home-path/jail → 0.
 */
@Component
public class ThreatAnalyzer {

  private final DangerMapConfig config;

  public ThreatAnalyzer(DangerMapConfig config) {
    this.config = config;
  }

  public DangerMap buildMap(int botSeat, LudoColor botColor, List<ScannedPawn> scanned) {
    long t0 = System.nanoTime();
    List<ScannedPawn> enemies =
        scanned.stream().filter(p -> p.seat() != botSeat && !p.inJail() && !p.atHome()).toList();
    List<ScannedPawn> own =
        scanned.stream().filter(p -> p.seat() == botSeat).toList();

    // Precompute own stacking (blocks) on main cells
    Map<Integer, Integer> ownStack = new HashMap<>();
    for (ScannedPawn p : own) {
      if (isMain(p.position()) && !isSafe(p.position())) {
        ownStack.merge(p.position(), 1, Integer::sum);
      }
    }

    // Cells of interest: all main tiles enemies can threaten + own positions
    Set<Integer> interest = new HashSet<>();
    for (int i = 0; i < TOTAL_TILES; i++) {
      interest.add(i);
    }
    for (ScannedPawn p : own) {
      interest.add(p.position());
    }

    Map<Integer, DangerCell> cells = new HashMap<>(interest.size());
    for (int cell : interest) {
      cells.put(cell, analyzeCell(cell, botSeat, botColor, enemies, ownStack));
    }

    return new DangerMap(botSeat, cells, scanned, enemies, t0);
  }

  public DangerCell analyzeCell(
      int cell,
      int botSeat,
      LudoColor botColor,
      List<ScannedPawn> enemies,
      Map<Integer, Integer> ownStack
  ) {
    if (isJail(cell) || isHome(cell)) {
      return new DangerCell(cell, 0, ThreatLevel.SAFE, 0, false, isHome(cell), false, false, false, 0, 0);
    }
    if (isExit(cell)) {
      // Home path — only owner enters
      return new DangerCell(cell, 5, ThreatLevel.SAFE, 0, false, true, false, false, false, 0, 0);
    }
    if (isSafe(cell)) {
      return new DangerCell(cell, 0, ThreatLevel.SAFE, 0, true, false, false, false, false, 0, 0);
    }
    if (!isMain(cell)) {
      return new DangerCell(cell, 0, ThreatLevel.SAFE, 0, false, false, false, false, false, 0, 0);
    }

    Set<Integer> threatSeats = new HashSet<>();
    boolean leaderThreat = false;
    for (ScannedPawn e : enemies) {
      if (!isMain(e.position())) {
        continue;
      }
      int dist = stepsOnMain(e.position(), cell);
      if (dist >= 1 && dist <= 6) {
        // Legal reach in one die (ignore exact capture legality edge-cases: safe already handled)
        threatSeats.add(e.seat());
        if (e.leader()) {
          leaderThreat = true;
        }
      }
    }

    int count = threatSeats.size();
    int danger = dangerFromEnemyCount(count);
    if (leaderThreat && danger > 0) {
      danger = Math.min(100, danger + 10);
    }

    boolean blocked = ownStack.getOrDefault(cell, 0) >= 2;
    if (blocked && danger > 0) {
      danger = Math.max(0, danger - 40); // e.g. 60 → 20 style reduction
    }

    int futureOne = 0;
    int futureTwo = 0;
    boolean trap = false;
    if (config.futureThreat() && danger < 40) {
      futureOne = countFutureThreats(cell, enemies, 1, 6);
      futureTwo = countFutureThreats(cell, enemies, 7, 12);
      if (config.trapDetection() && danger <= 20 && futureTwo >= 2) {
        trap = true;
        danger = Math.max(danger, 55);
      }
    }

    return new DangerCell(
        cell,
        danger,
        ThreatLevel.fromScore(danger),
        count,
        false,
        false,
        blocked,
        leaderThreat,
        trap,
        futureOne,
        futureTwo);
  }

  /** 1 enemy→40, 2→70, 3+→95 */
  static int dangerFromEnemyCount(int enemySeats) {
    if (enemySeats <= 0) {
      return 0;
    }
    if (enemySeats == 1) {
      return 40;
    }
    if (enemySeats == 2) {
      return 70;
    }
    return 95;
  }

  private static int countFutureThreats(int cell, List<ScannedPawn> enemies, int minDist, int maxDist) {
    Set<Integer> seats = new HashSet<>();
    for (ScannedPawn e : enemies) {
      if (!isMain(e.position())) {
        continue;
      }
      int dist = stepsOnMain(e.position(), cell);
      if (dist >= minDist && dist <= maxDist) {
        seats.add(e.seat());
      }
    }
    return seats.size();
  }

  static int stepsOnMain(int from, int to) {
    if (!isMain(from) || !isMain(to)) {
      return -1;
    }
    return (to - from + TOTAL_TILES) % TOTAL_TILES;
  }

  /**
   * Builds a {@link DangerReport} for one candidate move (escape / trap / score delta).
   */
  public DangerReport reportForMove(
      MoveCandidate c,
      DangerMap map,
      int pawnProgress,
      int maxProgress
  ) {
    int fromD = dangerForPawnPosition(c.from(), map, pawnProgress, maxProgress);
    int toD = dangerForPawnPosition(c.to(), map, pawnProgress, maxProgress);
    boolean escape = fromD >= 40 && toD + 15 < fromD;
    boolean trap = map.cell(c.to()).trap();
    boolean safer = toD < fromD;
    int delta = config.scoreDeltaForDanger(toD);
    if (escape) {
      delta += config.escapeBonus();
    }
    if (trap) {
      delta -= config.trapPenalty();
    }
    if (config.futureThreat()) {
      DangerCell dest = map.cell(c.to());
      if (dest.futureOneTurn() > 0 && dest.dangerScore() < 40) {
        delta -= config.futureOnePenalty() * dest.futureOneTurn();
      }
      if (dest.futureTwoTurn() > 1) {
        delta -= config.futureTwoPenalty();
      }
    }
    if (config.safeRoute() && safer && toD <= 20) {
      delta += config.safeRouteBonus() / 4;
    }
    // Survival preference: heavy weight on destination danger vs small progress
    if (fromD <= 20 && toD >= 70) {
      delta -= 50;
    }
    return new DangerReport(
        c.pawnIndex(), c.from(), c.to(), fromD, toD, escape, trap, safer, delta);
  }

  /** Apply advanced-pawn multiplier when evaluating risk at a pawn's cell. */
  int dangerForPawnPosition(int pos, DangerMap map, int pawnProgress, int maxProgress) {
    DangerCell cell = map.cell(pos);
    int d = cell.dangerScore();
    if (isJail(pos) || isHome(pos) || isSafe(pos) || isExit(pos)) {
      return cell.dangerScore();
    }
    if (maxProgress > 0 && pawnProgress >= maxProgress * 0.55) {
      d = Math.min(100, (int) Math.round(d * 1.5));
    }
    return d;
  }
}
