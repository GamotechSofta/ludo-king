/**
 * Board geometry — imported from the shared JSON (keep in sync with
 * `shared/ludo-board-constants.json` and Spring classpath resource).
 */
import boardJson from "./ludo-board-constants.json";

export const LUDO_BOARD = boardJson;

export const TOTAL_SHARED_PATH_CELLS = boardJson.totalSharedPathCells as 52;
export const CELLS_PER_ARM = boardJson.cellsPerArm;
export const HOME_COLUMN_LENGTH = boardJson.homeColumnLength;
export const TOKENS_PER_COLOR = boardJson.tokensPerColor;
export const MAX_STACK_SAME_COLOR = boardJson.maxStackSameColor;
export const STEPS_AFTER_ENTRY_TO_HOME = boardJson.stepsAfterEntryToHome;
export const TOTAL_JOURNEY_STEPS = boardJson.totalJourneyStepsIncludingYardExit;

/** Absolute shared-path indices that are always safe (4 starts + 4 stars). */
export const SAFE_TILES: readonly number[] = boardJson.safeTiles;
export const START_TILES: readonly number[] = boardJson.startTiles;
export const STAR_TILES: readonly number[] = boardJson.starTiles;

export const COLOR_BOARD = boardJson.colors;

export const isSafeTile = (tileIndex: number) =>
  SAFE_TILES.includes(tileIndex);

export const isStarTile = (tileIndex: number) =>
  STAR_TILES.includes(tileIndex);

export const absoluteFromRelative = (startTile: number, relative: number) =>
  (((startTile + relative) % TOTAL_SHARED_PATH_CELLS) +
    TOTAL_SHARED_PATH_CELLS) %
  TOTAL_SHARED_PATH_CELLS;
