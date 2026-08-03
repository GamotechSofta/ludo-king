package com.ludo.backend.game;

import java.util.List;

/**
 * Early-game kill delay hooks for Bot vs Human matches.
 *
 * <p>Legal capture moves are <strong>never</strong> filtered out — if a kill
 * chance exists (1st, 2nd, or later opportunity), it stays selectable and bots
 * already prioritize capture via {@code BotService}. Counters remain available
 * for future tuning but do not suppress kills.
 *
 * <p>Does not change dice, movement physics, or capture resolution.
 */
final class EarlyKillDelay {

  /** Kept at 0 so kill opportunities are never suppressed from the legal set. */
  static final int SKIPS_BEFORE_ALLOW = 0;

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
   * Tracks cross-side capture opportunities per seat but does not arm suppress —
   * kill moves always remain in the legal set (kill priority).
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
    // Count opportunities for diagnostics only — never suppress kills.
    if (SKIPS_BEFORE_ALLOW > 0 && rt.earlyKillSkipCount[seat] < SKIPS_BEFORE_ALLOW) {
      rt.earlyKillSkipCount[seat] += 1;
      rt.earlyKillSuppressActive[seat] = true;
    }
  }

  /** No-op while {@link #SKIPS_BEFORE_ALLOW} is 0 — returns moves unchanged. */
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
    java.util.ArrayList<int[]> kept = new java.util.ArrayList<>(rawMoves.size());
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
