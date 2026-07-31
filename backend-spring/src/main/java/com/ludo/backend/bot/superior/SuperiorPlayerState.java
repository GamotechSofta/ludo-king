package com.ludo.backend.bot.superior;

import com.ludo.backend.game.LudoColor;
import java.util.Arrays;

/** Per-seat bot view: color + progress-encoded tokens (LudoGame MatchPlayerState analogue). */
public final class SuperiorPlayerState {
  public final LudoColor color;
  public final int[] tokens;
  public final boolean abandoned;

  public SuperiorPlayerState(LudoColor color, int[] tokens, boolean abandoned) {
    this.color = color;
    this.tokens = tokens != null ? tokens.clone() : new int[0];
    this.abandoned = abandoned;
  }

  public SuperiorPlayerState withTokens(int[] nextTokens) {
    return new SuperiorPlayerState(color, nextTokens, abandoned);
  }

  public SuperiorPlayerState copy() {
    return new SuperiorPlayerState(color, tokens, abandoned);
  }

  @Override
  public String toString() {
    return "SuperiorPlayerState{color="
        + color
        + ", tokens="
        + Arrays.toString(tokens)
        + ", abandoned="
        + abandoned
        + '}';
  }
}
