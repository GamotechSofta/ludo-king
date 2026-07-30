package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import org.springframework.stereotype.Component;

/** Picks {@link AdaptiveStrategy} and builds weight deltas for the profile. */
@Component
public class StrategySelector {

  private final AdaptiveConfig config;

  public StrategySelector(AdaptiveConfig config) {
    this.config = config;
  }

  public AdaptiveStrategy select(
      BotStatus status,
      BotGamePhase phase,
      MatchAnalyzer.MatchSnapshot snap,
      BotMatchAnalysis match
  ) {
    if (!config.strategySwitch()) {
      return AdaptiveStrategy.BOARD_CONTROL;
    }
    if (snap != null && snap.endgameFourth) {
      return AdaptiveStrategy.FINISH;
    }
    if (status == BotStatus.LEADING) {
      return phase == BotGamePhase.END
          ? AdaptiveStrategy.FINISH
          : AdaptiveStrategy.DEFENSIVE;
    }
    if (status == BotStatus.CRITICAL || status == BotStatus.BEHIND) {
      return AdaptiveStrategy.RECOVERY;
    }
    if (phase == BotGamePhase.EARLY) {
      return AdaptiveStrategy.EXPANSION;
    }
    if (phase == BotGamePhase.END) {
      return AdaptiveStrategy.FINISH;
    }
    return AdaptiveStrategy.BOARD_CONTROL;
  }

  public DifficultyProfile buildProfile(
      BotStatus status,
      AdaptiveStrategy strategy,
      int aggression,
      MatchAnalyzer.MatchSnapshot snap,
      BotMatchAnalysis match,
      double performanceAssistBoost
  ) {
    double assist = diceAssistFor(status, match, performanceAssistBoost);
    int cap = 0;
    int safe = 0;
    int esc = 0;
    int home = 0;
    int prot = 0;
    int risk = 0;
    int depthBoost = 0;
    double futureMult = 1.0;
    boolean reduceSide = false;
    String reason = status.name() + "/" + strategy.name();

    switch (strategy) {
      case DEFENSIVE -> {
        cap = -15;
        safe = 25;
        esc = 20;
        home = 30;
        prot = 35;
        risk = 25;
        assist = Math.min(assist, config.maxAssist() * 0.5);
      }
      case EXPANSION -> {
        safe = 15;
        home = 5;
        cap = -5;
      }
      case BOARD_CONTROL -> {
        cap = 5;
        safe = 10;
        esc = 10;
      }
      case FINISH -> {
        home = 50;
        safe = 20;
        esc = 15;
        cap = -20;
        reduceSide = true;
        prot = 25;
      }
      case RECOVERY -> {
        cap = status == BotStatus.CRITICAL ? 40 : 30;
        safe = 30;
        esc = 35;
        home = 25;
        prot = 20;
        risk = 15;
        if (config.futureWeight()) {
          depthBoost = status == BotStatus.CRITICAL ? 1 : 0;
          futureMult = status == BotStatus.CRITICAL ? 1.25 : 1.12;
        }
      }
    }

    if (snap != null && snap.humanDominating) {
      cap += 10;
      esc += 10;
      home += 15;
      if (config.futureWeight()) {
        depthBoost = Math.max(depthBoost, 1);
        futureMult = Math.max(futureMult, 1.2);
      }
      assist = Math.min(config.maxAssist(), assist + 0.05);
      reason += "+comeback";
    }

    if (snap != null && snap.maxOwnDanger >= 70) {
      esc += 20;
      safe += 15;
      prot += 15;
    }

    if (!config.dynamicScoring()) {
      cap = safe = esc = home = prot = risk = 0;
    }

    return new DifficultyProfile(
        status,
        strategy,
        aggression,
        assist,
        cap,
        safe,
        esc,
        home,
        prot,
        risk,
        depthBoost,
        futureMult,
        reduceSide,
        reason,
        true);
  }

  private double diceAssistFor(
      BotStatus status, BotMatchAnalysis match, double performanceBoost
  ) {
    double base =
        switch (status) {
          case LEADING -> 0.10;
          case BALANCED -> 0.22;
          case BEHIND -> 0.35;
          case CRITICAL -> config.maxAssist();
        };
    if (match != null && match.mode != null) {
      base *=
          switch (match.mode) {
            case MODE_1 -> 1.05;
            case MODE_2 -> 0.55;
            case MODE_3 -> 0.90;
            case MODE_4 -> 1.10;
            default -> 0.7;
          };
    }
    base += performanceBoost;
    return Math.min(config.maxAssist(), Math.max(0.0, base));
  }
}
