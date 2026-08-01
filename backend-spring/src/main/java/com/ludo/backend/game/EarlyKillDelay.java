package com.ludo.backend.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Early-game kill delay for Bot vs Human matches only.
 *
 * <p>Each seat has its own counter. The first three legal cross-side capture
 * opportunities are skipped (capture moves removed from the legal set); the
 * fourth is allowed. Counter resets after a successful capture.
 *
 * <p>Does not change dice, movement physics, or capture resolution — only which
 * otherwise-legal moves are offered during the delayed opportunities.
 */
final class EarlyKillDelay {

  /** Opportunities that must be skipped before a capture is allowed. */
  static final int SKIPS_BEFORE_ALLOW = 3;

  private EarlyKillDelay() {}

  static boolean isBotVsHumanMatch(boolean[] isBot) {
    if (isBot == null || isBot.length == 0) {
      return false;
    }
    boolean hasBot = false;
    boolean hasHuman = false;
    for (boolean bot : isBot) {
      if (bot) {
        hasBot = true;
      } else {
        hasHuman = true;
      }
    }
    return hasBot && hasHuman;
  }

  /**
   * Called once per roll that yields legal moves: if this seat has a cross-side
   * capture opportunity and still has skips remaining, increment the counter and
   * arm suppress for this move phase.
   */
  static void noteOpportunity(
      GameEngineService.MatchRuntime rt, int seat, List<int[]> rawMoves, CaptureProbe probe
  ) {
    if (rt == null || probe == null || seat < 0 || seat >= rt.maxPlayers) {
      return;
    }
    rt.earlyKillSuppressActive[seat] = false;
    if (!isBotVsHumanMatch(rt.isBot)) {
      return;
    }
    if (rawMoves == null || rawMoves.isEmpty()) {
      return;
    }
    if (!hasCrossSideCapture(rt, seat, rawMoves, probe)) {
      return;
    }
    if (rt.earlyKillSkipCount[seat] < SKIPS_BEFORE_ALLOW) {
      rt.earlyKillSkipCount[seat] += 1;
      rt.earlyKillSuppressActive[seat] = true;
    }
  }

  /** Filter out cross-side capture moves while suppress is armed for this seat. */
  static List<int[]> filterMoves(
      GameEngineService.MatchRuntime rt, int seat, List<int[]> rawMoves, CaptureProbe probe
  ) {
    if (rt == null
        || rawMoves == null
        || rawMoves.isEmpty()
        || seat < 0
        || seat >= rt.maxPlayers
        || !rt.earlyKillSuppressActive[seat]
        || probe == null) {
      return rawMoves;
    }
    List<int[]> kept = new ArrayList<>(rawMoves.size());
    for (int[] m : rawMoves) {
      if (m == null || m.length < 2) {
        continue;
      }
      int dice = rt.diceList.get(m[1]);
      if (!probe.wouldCaptureCrossSide(seat, m[0], dice)) {
        kept.add(m);
      }
    }
    return kept;
  }

  static void onSuccessfulCapture(GameEngineService.MatchRuntime rt, int seat) {
    if (rt == null || seat < 0 || seat >= rt.maxPlayers) {
      return;
    }
    rt.earlyKillSkipCount[seat] = 0;
    rt.earlyKillSuppressActive[seat] = false;
  }

  private static boolean hasCrossSideCapture(
      GameEngineService.MatchRuntime rt, int seat, List<int[]> moves, CaptureProbe probe
  ) {
    for (int[] m : moves) {
      if (m == null || m.length < 2) {
        continue;
      }
      int dice = rt.diceList.get(m[1]);
      if (probe.wouldCaptureCrossSide(seat, m[0], dice)) {
        return true;
      }
    }
    return false;
  }

  @FunctionalInterface
  interface CaptureProbe {
    boolean wouldCaptureCrossSide(int seat, int tokenIndex, int dice);
  }
}
