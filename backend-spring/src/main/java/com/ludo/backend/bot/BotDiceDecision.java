package com.ludo.backend.bot;

/**
 * Dice face for a bot turn plus the hunt state that produced it.
 *
 * <p>{@code forcedTokenIndex} must be moved for the hunt to stay on track, so it is carried on
 * the match until the bot's move is applied.
 */
public record BotDiceDecision(int dice, KillStalkPlan stalkPlan, Integer forcedTokenIndex) {

  public BotDiceDecision(int dice) {
    this(dice, null, null);
  }

  public BotDiceDecision(int dice, Integer forcedTokenIndex) {
    this(dice, null, forcedTokenIndex);
  }
}
