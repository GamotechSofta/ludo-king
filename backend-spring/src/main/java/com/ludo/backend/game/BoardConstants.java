package com.ludo.backend.game;

import java.util.Set;

public final class BoardConstants {
  public static final int TOTAL_TILES = 52;
  public static final int EXIT_LEN = 5;
  public static final int HOME_STEPS = 6;

  public static final int JAIL = -1;
  public static final int EXIT_BASE = 100;
  public static final int HOME = 200;

  public static final Set<Integer> SAFE_AREAS = Set.of(0, 8, 13, 21, 26, 34, 39, 47);

  private BoardConstants() {
  }

  public static boolean isJail(int pos) {
    return pos == JAIL;
  }

  public static boolean isHome(int pos) {
    return pos >= HOME;
  }

  public static boolean isExit(int pos) {
    return pos >= EXIT_BASE && pos < HOME;
  }

  public static boolean isMain(int pos) {
    return pos >= 0 && pos < TOTAL_TILES;
  }

  public static int exitIndex(int pos) {
    return pos - EXIT_BASE;
  }

  public static int toExit(int index) {
    return EXIT_BASE + index;
  }
}
