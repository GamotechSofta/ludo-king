package com.ludo.backend.bot.ai;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-board danger view for one bot seat / turn.
 *
 * <p>Reusable by Move Scoring, Future Simulation, Dice Assist, Adaptive Difficulty.
 */
public final class DangerMap {

  private final int botSeat;
  private final Map<Integer, DangerCell> cells;
  private final List<ScannedPawn> allPawns;
  private final List<ScannedPawn> enemyPawns;
  private final long builtAtNanos;

  public DangerMap(
      int botSeat,
      Map<Integer, DangerCell> cells,
      List<ScannedPawn> allPawns,
      List<ScannedPawn> enemyPawns,
      long builtAtNanos
  ) {
    this.botSeat = botSeat;
    this.cells = cells != null ? cells : Map.of();
    this.allPawns = allPawns != null ? allPawns : List.of();
    this.enemyPawns = enemyPawns != null ? enemyPawns : List.of();
    this.builtAtNanos = builtAtNanos;
  }

  public int botSeat() {
    return botSeat;
  }

  public DangerCell cell(int position) {
    DangerCell c = cells.get(position);
    if (c != null) {
      return c;
    }
    return new DangerCell(
        position, 0, ThreatLevel.SAFE, 0, false, false, false, false, false, 0, 0);
  }

  public int dangerAt(int position) {
    return cell(position).dangerScore();
  }

  public Map<Integer, DangerCell> cells() {
    return Collections.unmodifiableMap(cells);
  }

  public List<ScannedPawn> allPawns() {
    return Collections.unmodifiableList(allPawns);
  }

  public List<ScannedPawn> enemyPawns() {
    return Collections.unmodifiableList(enemyPawns);
  }

  public long builtAtNanos() {
    return builtAtNanos;
  }

  /** Enemy pawns that are advanced / near home — capture AI hooks. */
  public List<ScannedPawn> enemyAdvanced() {
    return enemyPawns.stream()
        .filter(p -> !p.inJail() && !p.atHome() && p.remainingToHome() <= 25)
        .toList();
  }

  public List<ScannedPawn> enemyWeak() {
    return enemyPawns.stream()
        .filter(p -> !p.inJail() && !p.atHome() && !p.onSafeCell() && p.progress() < 30)
        .toList();
  }

  static DangerMap empty(int botSeat) {
    return new DangerMap(botSeat, new HashMap<>(), List.of(), List.of(), System.nanoTime());
  }
}
