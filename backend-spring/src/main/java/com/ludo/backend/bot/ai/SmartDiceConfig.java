package com.ludo.backend.bot.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Tunables for HARD-bot Smart Dice Assistance (non-kill). */
@Component
public class SmartDiceConfig {

  private final boolean enabled;
  private final boolean weighted;
  private final double maxAssist;
  private final double early;
  private final double mid;
  private final double end;
  private final double losing;
  private final double winning;
  private final double mode1Mult;
  private final double mode2Mult;
  private final double mode3Mult;
  private final double mode4Mult;
  private final double badDiceChance;
  private final int historySize;
  private final int homeBonus;
  private final int homePathBonus;
  private final int safeBonus;
  private final int escapeBonus;
  private final int openBonus;
  private final int boardDevBonus;
  private final int blockBonus;
  private final int nearHomeBonus;
  private final int leaderBonus;
  private final int dangerPenalty;
  private final int uselessPenalty;

  public SmartDiceConfig(
      @Value("${ludo.bot.smartDice.enabled:true}") boolean enabled,
      @Value("${ludo.bot.smartDice.weighted:true}") boolean weighted,
      @Value("${ludo.bot.smartDice.maxAssist:0.45}") double maxAssist,
      @Value("${ludo.bot.smartDice.early:0.15}") double early,
      @Value("${ludo.bot.smartDice.mid:0.25}") double mid,
      @Value("${ludo.bot.smartDice.end:0.35}") double end,
      @Value("${ludo.bot.smartDice.losing:0.45}") double losing,
      @Value("${ludo.bot.smartDice.winning:0.10}") double winning,
      @Value("${ludo.bot.smartDice.mode1Mult:1.0}") double mode1Mult,
      @Value("${ludo.bot.smartDice.mode2Mult:0.40}") double mode2Mult,
      @Value("${ludo.bot.smartDice.mode3Mult:0.85}") double mode3Mult,
      @Value("${ludo.bot.smartDice.mode4Mult:1.15}") double mode4Mult,
      @Value("${ludo.bot.smartDice.badDiceChance:0.12}") double badDiceChance,
      @Value("${ludo.bot.smartDice.historySize:20}") int historySize,
      @Value("${ludo.bot.smartDice.homeBonus:180}") int homeBonus,
      @Value("${ludo.bot.smartDice.homePathBonus:120}") int homePathBonus,
      @Value("${ludo.bot.smartDice.safeBonus:90}") int safeBonus,
      @Value("${ludo.bot.smartDice.escapeBonus:100}") int escapeBonus,
      @Value("${ludo.bot.smartDice.openBonus:50}") int openBonus,
      @Value("${ludo.bot.smartDice.boardDevBonus:40}") int boardDevBonus,
      @Value("${ludo.bot.smartDice.blockBonus:60}") int blockBonus,
      @Value("${ludo.bot.smartDice.nearHomeBonus:35}") int nearHomeBonus,
      @Value("${ludo.bot.smartDice.leaderBonus:40}") int leaderBonus,
      @Value("${ludo.bot.smartDice.dangerPenalty:100}") int dangerPenalty,
      @Value("${ludo.bot.smartDice.uselessPenalty:40}") int uselessPenalty
  ) {
    this.enabled = enabled;
    this.weighted = weighted;
    this.maxAssist = clamp01(maxAssist);
    this.early = clamp01(early);
    this.mid = clamp01(mid);
    this.end = clamp01(end);
    this.losing = clamp01(losing);
    this.winning = clamp01(winning);
    this.mode1Mult = Math.max(0, mode1Mult);
    this.mode2Mult = Math.max(0, mode2Mult);
    this.mode3Mult = Math.max(0, mode3Mult);
    this.mode4Mult = Math.max(0, mode4Mult);
    this.badDiceChance = clamp01(badDiceChance);
    this.historySize = Math.max(1, historySize);
    this.homeBonus = homeBonus;
    this.homePathBonus = homePathBonus;
    this.safeBonus = safeBonus;
    this.escapeBonus = escapeBonus;
    this.openBonus = openBonus;
    this.boardDevBonus = boardDevBonus;
    this.blockBonus = blockBonus;
    this.nearHomeBonus = nearHomeBonus;
    this.leaderBonus = leaderBonus;
    this.dangerPenalty = dangerPenalty;
    this.uselessPenalty = uselessPenalty;
  }

  private static double clamp01(double v) {
    return Math.max(0.0, Math.min(1.0, v));
  }

  public boolean enabled() {
    return enabled;
  }

  public boolean weighted() {
    return weighted;
  }

  public double maxAssist() {
    return maxAssist;
  }

  public double early() {
    return early;
  }

  public double mid() {
    return mid;
  }

  public double end() {
    return end;
  }

  public double losing() {
    return losing;
  }

  public double winning() {
    return winning;
  }

  public double mode1Mult() {
    return mode1Mult;
  }

  public double mode2Mult() {
    return mode2Mult;
  }

  public double mode3Mult() {
    return mode3Mult;
  }

  public double mode4Mult() {
    return mode4Mult;
  }

  public double badDiceChance() {
    return badDiceChance;
  }

  public int historySize() {
    return historySize;
  }

  public int homeBonus() {
    return homeBonus;
  }

  public int homePathBonus() {
    return homePathBonus;
  }

  public int safeBonus() {
    return safeBonus;
  }

  public int escapeBonus() {
    return escapeBonus;
  }

  public int openBonus() {
    return openBonus;
  }

  public int boardDevBonus() {
    return boardDevBonus;
  }

  public int blockBonus() {
    return blockBonus;
  }

  public int nearHomeBonus() {
    return nearHomeBonus;
  }

  public int leaderBonus() {
    return leaderBonus;
  }

  public int dangerPenalty() {
    return dangerPenalty;
  }

  public int uselessPenalty() {
    return uselessPenalty;
  }
}
