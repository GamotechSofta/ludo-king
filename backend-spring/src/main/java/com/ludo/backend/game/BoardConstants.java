package com.ludo.backend.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Board geometry loaded from {@code classpath:ludo-board-constants.json}
 * (must match {@code shared/ludo-board-constants.json} and the frontend copy).
 */
public final class BoardConstants {
  public static final int TOTAL_TILES;
  public static final int CELLS_PER_ARM;
  public static final int EXIT_LEN;
  public static final int HOME_STEPS;
  public static final int MAX_STACK;
  public static final int TOKENS_PER_COLOR;
  public static final int STEPS_AFTER_ENTRY_TO_HOME;
  public static final int TOTAL_JOURNEY_STEPS;

  public static final int JAIL = -1;
  public static final int EXIT_BASE = 100;
  public static final int HOME = 200;

  public static final Set<Integer> SAFE_AREAS;
  public static final Set<Integer> START_TILES;
  public static final Set<Integer> STAR_TILES;

  static {
    try (InputStream in =
        BoardConstants.class.getClassLoader().getResourceAsStream("ludo-board-constants.json")) {
      if (in == null) {
        throw new IllegalStateException("Missing classpath resource ludo-board-constants.json");
      }
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(in);

      TOTAL_TILES = root.get("totalSharedPathCells").asInt();
      CELLS_PER_ARM = root.get("cellsPerArm").asInt();
      HOME_STEPS = root.get("homeColumnLength").asInt();
      // Exit lane cells before center (HOME_STEPS includes the finish step)
      EXIT_LEN = HOME_STEPS - 1;
      MAX_STACK = root.get("maxStackSameColor").asInt();
      TOKENS_PER_COLOR = root.get("tokensPerColor").asInt();
      STEPS_AFTER_ENTRY_TO_HOME = root.get("stepsAfterEntryToHome").asInt();
      TOTAL_JOURNEY_STEPS = root.get("totalJourneyStepsIncludingYardExit").asInt();

      START_TILES = readIntSet(root.get("startTiles"));
      STAR_TILES = readIntSet(root.get("starTiles"));
      SAFE_AREAS = readIntSet(root.get("safeTiles"));

      if (TOTAL_TILES != 52) {
        throw new IllegalStateException("Shared path must be exactly 52 cells");
      }
      if (SAFE_AREAS.size() != 8) {
        throw new IllegalStateException("Expected 8 safe cells (4 start + 4 star)");
      }
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  private BoardConstants() {
  }

  private static Set<Integer> readIntSet(JsonNode arr) {
    Set<Integer> set = new HashSet<>();
    for (JsonNode n : arr) {
      set.add(n.asInt());
    }
    return Collections.unmodifiableSet(set);
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

  public static boolean isSafe(int pos) {
    return isMain(pos) && SAFE_AREAS.contains(pos);
  }

  public static int exitIndex(int pos) {
    return pos - EXIT_BASE;
  }

  public static int toExit(int index) {
    return EXIT_BASE + index;
  }

  /** Absolute shared-path index for a color-relative position (0..50 on path). */
  public static int absoluteFromRelative(int startTile, int relative) {
    return Math.floorMod(startTile + relative, TOTAL_TILES);
  }
}
