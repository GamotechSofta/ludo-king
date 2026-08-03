package com.ludo.backend.bot;

import com.ludo.backend.game.LudoColor;

/** Per-seat progress-space view for dice bias / kill stalk (no Kotlin MatchPlayerState). */
public record BotPlayerView(
    LudoColor color,
    int[] tokens,
    boolean isBot,
    boolean abandoned,
    int matchDiceRollCount,
    int matchSixCount) {

  public boolean isStalkableHunter() {
    return isBot && !abandoned;
  }

  public boolean isStalkableTarget() {
    return !isBot && !abandoned;
  }

  public boolean isEffectivelyAbandoned() {
    return abandoned;
  }
}
