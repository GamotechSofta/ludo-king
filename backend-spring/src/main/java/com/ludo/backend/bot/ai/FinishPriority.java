package com.ludo.backend.bot.ai;

/** Finish / endgame move priority ladder (highest first). */
public enum FinishPriority {
  EXACT_FINISH,
  HOME_ENTRY,
  PROTECT_ADVANCED,
  SAFE_CELL,
  CAPTURE,
  BOARD_EXPANSION,
  OTHER;

  public int rank() {
    return ordinal();
  }
}
