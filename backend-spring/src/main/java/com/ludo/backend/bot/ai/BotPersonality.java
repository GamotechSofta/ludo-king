package com.ludo.backend.bot.ai;

/** HARD-bot personality archetypes. */
public enum BotPersonality {
  BALANCED,
  AGGRESSIVE,
  DEFENSIVE,
  SPEED_RUNNER,
  OPPORTUNIST;

  public static BotPersonality fromConfig(String name) {
    if (name == null || name.isBlank()) {
      return BALANCED;
    }
    String n = name.trim().toUpperCase().replace('-', '_').replace(' ', '_');
    return switch (n) {
      case "AGGRESSIVE" -> AGGRESSIVE;
      case "DEFENSIVE" -> DEFENSIVE;
      case "SPEED_RUNNER", "SPEEDRUNNER", "SPEED" -> SPEED_RUNNER;
      case "OPPORTUNIST", "OPPORTUNE" -> OPPORTUNIST;
      default -> BALANCED;
    };
  }

  public String displayName() {
    return switch (this) {
      case SPEED_RUNNER -> "Speed Runner";
      default -> name().charAt(0) + name().substring(1).toLowerCase();
    };
  }
}
