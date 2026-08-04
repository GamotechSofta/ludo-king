package com.ludo.backend.bot.superior;

import com.ludo.backend.game.LudoColor;
import java.util.Arrays;

/** Per-seat bot view: color + progress-encoded tokens (LudoGame MatchPlayerState analogue). */
public final class SuperiorPlayerState {
  public final LudoColor color;
  public final int[] tokens;
  public final boolean abandoned;
  public final boolean isBot;

  public SuperiorPlayerState(LudoColor color, int[] tokens, boolean abandoned) {
    this(color, tokens, abandoned, false);
  }

  public SuperiorPlayerState(LudoColor color, int[] tokens, boolean abandoned, boolean isBot) {
    this.color = color;
    this.tokens = tokens != null ? tokens.clone() : new int[0];
    this.abandoned = abandoned;
    this.isBot = isBot;
  }

  public SuperiorPlayerState withTokens(int[] nextTokens) {
    return new SuperiorPlayerState(color, nextTokens, abandoned, isBot);
  }

  public SuperiorPlayerState copy() {
    return new SuperiorPlayerState(color, tokens, abandoned, isBot);
  }

  @Override
  public String toString() {
    return "SuperiorPlayerState{color="
        + color
        + ", tokens="
        + Arrays.toString(tokens)
        + ", abandoned="
        + abandoned
        + ", isBot="
        + isBot
        + '}';
  }
}
