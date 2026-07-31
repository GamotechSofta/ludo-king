package com.ludo.backend.bot.superior;

import static com.ludo.backend.game.BoardConstants.EXIT_BASE;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.game.LudoColor;

/**
 * Converts Ludo King board encoding ↔ LudoGame progress model.
 *
 * <p>Board: JAIL=-1, main 0-51, EXIT=100+exitIndex, HOME=200.
 *
 * <p>Progress: -1 yard, 0-50 main relative, 51-55 home lane, 56 finished.
 */
public final class ProgressCodec {

  public static final int MAIN_PATH_LAST_PROGRESS = 50;
  public static final int HOME_LANE_START_PROGRESS = 51;
  public static final int HOME_LANE_LAST_PROGRESS = 55;
  public static final int FINISHED_PROGRESS = 56;

  private ProgressCodec() {}

  public static int toProgress(LudoColor color, int boardPos) {
    if (isJail(boardPos)) {
      return -1;
    }
    if (isHome(boardPos)) {
      return FINISHED_PROGRESS;
    }
    if (isExit(boardPos)) {
      return HOME_LANE_START_PROGRESS + (boardPos - EXIT_BASE);
    }
    if (isMain(boardPos) && color != null) {
      return Math.floorMod(boardPos - color.startTile(), TOTAL_TILES);
    }
    return -1;
  }

  public static int fromProgress(LudoColor color, int progress) {
    if (progress < 0) {
      return JAIL;
    }
    if (progress >= FINISHED_PROGRESS) {
      return HOME;
    }
    if (progress >= HOME_LANE_START_PROGRESS) {
      return EXIT_BASE + (progress - HOME_LANE_START_PROGRESS);
    }
    if (color == null) {
      return JAIL;
    }
    return Math.floorMod(color.startTile() + progress, TOTAL_TILES);
  }

  /** Absolute shared-path tile for a main-path progress (0..50), or -1 if not on main. */
  public static int absoluteMainTile(LudoColor color, int progress) {
    if (color == null || progress < 0 || progress > MAIN_PATH_LAST_PROGRESS) {
      return -1;
    }
    return Math.floorMod(color.startTile() + progress, TOTAL_TILES);
  }

  public static boolean isSafeMainProgress(LudoColor color, int progress) {
    int tile = absoluteMainTile(color, progress);
    return tile >= 0 && isSafe(tile);
  }

  public static boolean canMoveToken(int progress, int dice) {
    if (progress == -1) {
      return dice == 6;
    }
    if (progress >= FINISHED_PROGRESS) {
      return false;
    }
    return progress + dice <= FINISHED_PROGRESS;
  }
}
