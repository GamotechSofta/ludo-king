package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chooses who to attack / ignore from ranked opponent profiles.
 * Bots never cooperate — each HARD bot evaluates independently.
 */
@Component
public class TargetSelector {

  private final OpponentAnalysisConfig config;

  public TargetSelector(OpponentAnalysisConfig config) {
    this.config = config;
  }

  /**
   * Marks preferredTarget / ignoreForAttack on copies and returns updated list.
   */
  public List<OpponentProfile> select(
      List<OpponentProfile> profiles, int botSeat, BotMatchAnalysis match
  ) {
    if (profiles == null || profiles.isEmpty()) {
      return List.of();
    }
    ThreatRanking ranking = new ThreatRanking(profiles);
    OpponentProfile leader = ranking.currentLeader();
    // Prefer non-self leader; if self is leader, hunt next threat
    OpponentProfile primary = null;
    for (OpponentProfile p : ranking.byLeaderScore()) {
      if (p.seat() != botSeat) {
        primary = p;
        break;
      }
    }
    OpponentProfile secondary = null;
    for (OpponentProfile p : ranking.byThreat()) {
      if (p.seat() != botSeat && (primary == null || p.seat() != primary.seat())) {
        secondary = p;
        break;
      }
    }

    // Behind → more aggressive toward leader; leading → still track but less forced
    boolean behind = match != null && match.botBehind;
    boolean leading = match != null && match.botIsLeader;

    List<OpponentProfile> out = new ArrayList<>(profiles.size());
    for (OpponentProfile p : profiles) {
      boolean ignore =
          p.seat() == botSeat
              || (config.targetPriority() && p.weak() && !p.winningCritical() && !p.futureLeaderRisk());
      boolean target = false;
      if (config.targetPriority() && p.seat() != botSeat && !ignore) {
        if (primary != null && p.seat() == primary.seat()) {
          target = true;
        } else if (primary != null
            && primary.seat() == botSeat
            && secondary != null
            && p.seat() == secondary.seat()) {
          target = true;
        } else if (behind
            && leader != null
            && p.seat() == leader.seat()
            && leader.seat() != botSeat) {
          target = true;
        } else if (!leading && secondary != null && p.seat() == secondary.seat() && p.threatScore() >= 60) {
          target = true;
        } else if (p.winningCritical()) {
          target = true;
        }
      }
      out.add(copyFlags(p, target, ignore));
    }
    return out;
  }

  public int attackPrioritySeat(List<OpponentProfile> selected, int botSeat) {
    int best = -1;
    int bestThreat = -1;
    for (OpponentProfile p : selected) {
      if (p.seat() == botSeat || p.ignoreForAttack()) {
        continue;
      }
      if (p.preferredTarget() && p.threatScore() > bestThreat) {
        bestThreat = p.threatScore();
        best = p.seat();
      }
    }
    if (best >= 0) {
      return best;
    }
    for (OpponentProfile p : selected) {
      if (p.seat() == botSeat || p.ignoreForAttack()) {
        continue;
      }
      if (p.threatScore() > bestThreat) {
        bestThreat = p.threatScore();
        best = p.seat();
      }
    }
    return best;
  }

  private static OpponentProfile copyFlags(
      OpponentProfile p, boolean preferredTarget, boolean ignoreForAttack
  ) {
    return new OpponentProfile(
        p.seat(),
        p.colorName(),
        p.color(),
        p.bot(),
        p.finishedPawns(),
        p.activePawns(),
        p.jailPawns(),
        p.safePawns(),
        p.totalProgress(),
        p.averageProgress(),
        p.leaderPawnIndex(),
        p.leaderPawnProgress(),
        p.mostDangerousPawnIndex(),
        p.mostExposedPawnIndex(),
        p.weakestPawnIndex(),
        p.leaderScore(),
        p.threatScore(),
        p.threat(),
        p.winningProbability(),
        p.weak(),
        p.winningCritical(),
        p.futureLeaderRisk(),
        p.playStyle(),
        preferredTarget,
        ignoreForAttack);
  }
}
