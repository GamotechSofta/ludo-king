package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import org.springframework.stereotype.Component;

/** Derives {@link BotStatus} from match snapshot + performance. */
@Component
public class AdaptiveAnalyzer {

  private final AdaptiveConfig config;
  private final PerformanceTracker performance;

  public AdaptiveAnalyzer(AdaptiveConfig config, PerformanceTracker performance) {
    this.config = config;
    this.performance = performance;
  }

  public BotStatus detectStatus(MatchAnalyzer.MatchSnapshot snap, String roomId) {
    if (snap == null) {
      return BotStatus.BALANCED;
    }
    int gap = snap.progressGap;
    boolean leading =
        snap.rank == 1 && gap <= -config.behindGap() / 2
            || (snap.match != null && snap.match.botIsLeader && gap <= 0);

    if (snap.rank == 1 && snap.botProgress >= snap.bestOpponentProgress) {
      leading = true;
    }

    if (gap >= config.criticalGap() || snap.rank >= Math.max(3, snap.playerCount)) {
      return BotStatus.CRITICAL;
    }
    if (gap >= config.behindGap() || snap.rank > 1 && gap > 0) {
      // Sustained behind from tracker
      if (roomId != null && performance.behindTurnRatioPct(roomId, snap.botSeat) >= 60) {
        return gap >= config.criticalGap() / 2 ? BotStatus.CRITICAL : BotStatus.BEHIND;
      }
      return BotStatus.BEHIND;
    }
    if (leading) {
      return BotStatus.LEADING;
    }
    return BotStatus.BALANCED;
  }

  public int baseAggression(BotStatus status, BotMatchAnalysis match, BotGamePhase phase) {
    int a =
        switch (status) {
          case LEADING -> 25;
          case BALANCED -> 50;
          case BEHIND -> 75;
          case CRITICAL -> 95;
        };
    if (match != null) {
      a = applyMode(a, match.mode);
    }
    if (phase == BotGamePhase.EARLY) {
      a = Math.min(a, status == BotStatus.CRITICAL ? 80 : 60);
    }
    if (phase == BotGamePhase.END && status == BotStatus.LEADING) {
      a = Math.min(a, 20);
    }
    return Math.max(0, Math.min(100, a));
  }

  private static int applyMode(int aggression, BotAiMode mode) {
    if (mode == null) {
      return aggression;
    }
    return switch (mode) {
      case MODE_1 -> Math.min(100, aggression + 10); // most aggressive table
      case MODE_2 -> Math.max(0, aggression - 20); // don't gang up
      case MODE_3 -> aggression;
      case MODE_4 -> Math.min(100, aggression + 5); // smarter, not reckless
      default -> aggression;
    };
  }
}
