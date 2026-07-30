package com.ludo.backend.bot.ai;

/** Classification of a legal bot move for scoring. */
public enum MoveType {
  JAIL_EXIT,
  HOME_FINISH,
  HOME_COLUMN,
  CAPTURE,
  SAFE_LAND,
  ADVANCE,
  ESCAPE,
  BLOCK,
  OTHER
}
