package com.ludo.backend.bot.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/** Detects recurring patterns from visible human decision history. */
@Component
public class PatternDetector {

  public record Patterns(
      int favouritePawn,
      boolean capturePreference,
      boolean safePreference,
      boolean homePreference,
      boolean riskTolerance,
      boolean repeatedOpening,
      String favouriteStrategy
  ) {}

  public Patterns detect(PlayerStatistics stats, List<BehaviorEvent> events) {
    if (stats == null || stats.moves() < 3) {
      return new Patterns(0, false, false, false, false, false, "unknown");
    }
    boolean capturePref = stats.captureRate() >= 0.22;
    boolean safePref = stats.safeRate() >= 0.35;
    boolean homePref = stats.homeRate() >= 0.28;
    boolean riskTol = stats.riskRate() >= 0.28;
    boolean opening =
        stats.openingRate() >= 0.20
            || countOpeningStreak(events) >= 2;

    String strategy;
    if (capturePref && riskTol) {
      strategy = "chase captures";
    } else if (homePref) {
      strategy = "race home";
    } else if (safePref) {
      strategy = "play safe";
    } else if (riskTol) {
      strategy = "take risks";
    } else {
      strategy = "balanced development";
    }

    return new Patterns(
        stats.favouritePawn(),
        capturePref,
        safePref,
        homePref,
        riskTol,
        opening,
        strategy);
  }

  private static int countOpeningStreak(List<BehaviorEvent> events) {
    if (events == null) {
      return 0;
    }
    int streak = 0;
    for (int i = events.size() - 1; i >= 0; i--) {
      BehaviorEvent e = events.get(i);
      if (e.kind() != BehaviorEvent.Kind.MOVE) {
        continue;
      }
      if (e.opening()) {
        streak++;
      } else {
        break;
      }
    }
    return streak;
  }
}
