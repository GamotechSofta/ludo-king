package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for the Danger Map & Threat Analysis Engine. */
@Component
public class DangerMapConfig {

  private final boolean enabled;
  private final boolean futureThreat;
  private final boolean trapDetection;
  private final boolean cacheDangerMap;
  private final boolean safeRoute;
  private final int escapeBonus;
  private final int trapPenalty;
  private final int futureOnePenalty;
  private final int futureTwoPenalty;
  private final int safeRouteBonus;
  private final int criticalPenalty;
  private final int highPenalty;
  private final int mediumPenalty;
  private final int lowBonus;
  private final int safeBonus;

  public DangerMapConfig(
      @Value("${ludo.bot.danger.enabled:true}") boolean enabled,
      @Value("${ludo.bot.futureThreat:true}") boolean futureThreat,
      @Value("${ludo.bot.trapDetection:true}") boolean trapDetection,
      @Value("${ludo.bot.cacheDangerMap:true}") boolean cacheDangerMap,
      @Value("${ludo.bot.safeRoute:true}") boolean safeRoute,
      @Value("${ludo.bot.danger.escapeBonus:90}") int escapeBonus,
      @Value("${ludo.bot.danger.trapPenalty:100}") int trapPenalty,
      @Value("${ludo.bot.danger.futureOnePenalty:40}") int futureOnePenalty,
      @Value("${ludo.bot.danger.futureTwoPenalty:25}") int futureTwoPenalty,
      @Value("${ludo.bot.danger.safeRouteBonus:80}") int safeRouteBonus,
      @Value("${ludo.bot.danger.criticalPenalty:120}") int criticalPenalty,
      @Value("${ludo.bot.danger.highPenalty:80}") int highPenalty,
      @Value("${ludo.bot.danger.mediumPenalty:40}") int mediumPenalty,
      @Value("${ludo.bot.danger.lowBonus:20}") int lowBonus,
      @Value("${ludo.bot.danger.safeBonus:80}") int safeBonus
  ) {
    this.enabled = enabled;
    this.futureThreat = futureThreat;
    this.trapDetection = trapDetection;
    this.cacheDangerMap = cacheDangerMap;
    this.safeRoute = safeRoute;
    this.escapeBonus = escapeBonus;
    this.trapPenalty = trapPenalty;
    this.futureOnePenalty = futureOnePenalty;
    this.futureTwoPenalty = futureTwoPenalty;
    this.safeRouteBonus = safeRouteBonus;
    this.criticalPenalty = criticalPenalty;
    this.highPenalty = highPenalty;
    this.mediumPenalty = mediumPenalty;
    this.lowBonus = lowBonus;
    this.safeBonus = safeBonus;
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean futureThreat() {
    return futureThreat;
  }

  public boolean trapDetection() {
    return trapDetection;
  }

  public boolean cacheDangerMap() {
    return cacheDangerMap;
  }

  public boolean safeRoute() {
    return safeRoute;
  }

  public int escapeBonus() {
    return escapeBonus;
  }

  public int trapPenalty() {
    return trapPenalty;
  }

  public int futureOnePenalty() {
    return futureOnePenalty;
  }

  public int futureTwoPenalty() {
    return futureTwoPenalty;
  }

  public int safeRouteBonus() {
    return safeRouteBonus;
  }

  public int criticalPenalty() {
    return criticalPenalty;
  }

  public int highPenalty() {
    return highPenalty;
  }

  public int mediumPenalty() {
    return mediumPenalty;
  }

  public int lowBonus() {
    return lowBonus;
  }

  public int safeBonus() {
    return safeBonus;
  }

  /** Map danger 0–100 → move score delta. */
  public int scoreDeltaForDanger(int danger) {
    ThreatLevel level = ThreatLevel.fromScore(danger);
    return switch (level) {
      case SAFE -> safeBonus;
      case LOW -> lowBonus;
      case MEDIUM -> -mediumPenalty;
      case HIGH -> -highPenalty;
      case CRITICAL -> -criticalPenalty;
    };
  }
}
