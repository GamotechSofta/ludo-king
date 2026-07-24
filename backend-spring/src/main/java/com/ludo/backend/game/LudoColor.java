package com.ludo.backend.game;

import java.util.Arrays;
import java.util.List;

public enum LudoColor {
  RED(0, 50),
  GREEN(13, 11),
  YELLOW(26, 24),
  BLUE(39, 37);

  private final int startTile;
  private final int exitTile;

  LudoColor(int startTile, int exitTile) {
    this.startTile = startTile;
    this.exitTile = exitTile;
  }

  public int startTile() {
    return startTile;
  }

  public int exitTile() {
    return exitTile;
  }

  public static List<LudoColor> forPlayerCount(int n) {
    return switch (n) {
      case 2 -> List.of(RED, YELLOW);
      case 3 -> List.of(RED, GREEN, YELLOW);
      default -> Arrays.asList(RED, GREEN, YELLOW, BLUE);
    };
  }
}
