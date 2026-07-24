package com.ludo.backend.game;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Color seats loaded from the same board JSON as {@link BoardConstants}.
 * Paths are identical up to a 90° rotation (start tiles every 13 cells).
 */
public enum LudoColor {
  RED,
  GREEN,
  YELLOW,
  BLUE;

  private final int startTile;
  private final int turningTile;

  LudoColor() {
    JsonNode node = BoardColorData.COLORS.get(name());
    this.startTile = node.get("startTile").asInt();
    this.turningTile = node.get("turningTile").asInt();
  }

  public int startTile() {
    return startTile;
  }

  /** Shared-path cell where this color diverts into its private home column. */
  public int exitTile() {
    return turningTile;
  }

  public int turningTile() {
    return turningTile;
  }

  public static List<LudoColor> forPlayerCount(int n) {
    return switch (n) {
      case 2 -> List.of(RED, YELLOW);
      case 3 -> List.of(RED, GREEN, YELLOW);
      default -> Arrays.asList(RED, GREEN, YELLOW, BLUE);
    };
  }

  private static final class BoardColorData {
    static final JsonNode COLORS;

    static {
      try (InputStream in =
          LudoColor.class.getClassLoader().getResourceAsStream("ludo-board-constants.json")) {
        if (in == null) {
          throw new IllegalStateException("Missing ludo-board-constants.json");
        }
        COLORS = new ObjectMapper().readTree(in).get("colors");
      } catch (Exception e) {
        throw new ExceptionInInitializerError(e);
      }
    }
  }
}
