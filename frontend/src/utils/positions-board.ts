import { SIZE_TILE } from "./constants";
import {
  SAFE_TILES,
  HOME_COLUMN_LENGTH,
  COLOR_BOARD,
} from "../config/ludoBoard";
import type {
  IPoint,
  IPositionsItems,
  TExitTilesValues,
  TFinalPositionsValues,
  TLocationBoardElements,
} from "../interfaces";

/**
 * Guarda las valores de las posiciones para la ubicación de las fichas,
 * cuando se ha llevado la misma al punto final (ha terminado)....
 */
const FINAL_POSITIONS_VALUES: TFinalPositionsValues = {
  BOTTOM_LEFT: [
    {
      index: 0,
      coordinate: {
        x: Math.round(SIZE_TILE * 7 - SIZE_TILE / 1.8),
        y: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
      },
    },
    {
      index: 1,
      coordinate: {
        x: Math.round(SIZE_TILE * 7),
        y: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
      },
    },
    {
      index: 2,
      coordinate: {
        x: Math.round(SIZE_TILE * 7 + SIZE_TILE / 1.8),
        y: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
      },
    },
    {
      index: 3,
      coordinate: {
        x: Math.round(SIZE_TILE * 7),
        y: Math.round(SIZE_TILE * 8 - SIZE_TILE / 2.3),
      },
    },
  ],
  TOP_LEFT: [
    {
      index: 0,
      coordinate: {
        x: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7 - SIZE_TILE / 1.8),
      },
    },
    {
      index: 1,
      coordinate: {
        x: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7),
      },
    },
    {
      index: 2,
      coordinate: {
        x: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7 + SIZE_TILE / 1.8),
      },
    },
    {
      index: 3,
      coordinate: {
        x: Math.round(SIZE_TILE * 6 + SIZE_TILE / 2.3),
        y: Math.round(SIZE_TILE * 7),
      },
    },
  ],
  TOP_RIGHT: [
    {
      index: 0,
      coordinate: {
        x: Math.round(SIZE_TILE * 7 + SIZE_TILE / 1.8),
        y: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
      },
    },
    {
      index: 1,
      coordinate: {
        x: Math.round(SIZE_TILE * 7),
        y: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
      },
    },
    {
      index: 2,
      coordinate: {
        x: Math.round(SIZE_TILE * 7 - SIZE_TILE / 1.8),
        y: Math.round(SIZE_TILE * 6 - SIZE_TILE / 8),
      },
    },
    {
      index: 3,
      coordinate: {
        x: Math.round(SIZE_TILE * 7),
        y: Math.round(SIZE_TILE * 6 + SIZE_TILE / 2.3),
      },
    },
  ],
  BOTTOM_RIGHT: [
    {
      index: 0,
      coordinate: {
        x: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7 + SIZE_TILE / 1.8),
      },
    },
    {
      index: 1,
      coordinate: {
        x: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7),
      },
    },
    {
      index: 2,
      coordinate: {
        x: Math.round(SIZE_TILE * 8 + SIZE_TILE / 8),
        y: Math.round(SIZE_TILE * 7 - SIZE_TILE / 1.8),
      },
    },
    {
      index: 3,
      coordinate: {
        x: Math.round(SIZE_TILE * 8 - SIZE_TILE / 2.3),
        y: Math.round(SIZE_TILE * 7),
      },
    },
  ],
};

/**
 * Guarda los valores para calcular la posición de las celdas de salida...
 */
const EXIT_TILES_VALUES: TExitTilesValues = {
  BOTTOM_LEFT: {
    x: 7,
    y: 13,
    increaseX: 0,
    increaseY: -1,
    total: 5,
    indexBase: 0,
  },
  TOP_LEFT: {
    x: 1,
    y: 7,
    increaseX: 1,
    increaseY: 0,
    total: 5,
    indexBase: 0,
  },
  TOP_RIGHT: {
    x: 7,
    y: 1,
    increaseX: 0,
    increaseY: 1,
    total: 5,
    indexBase: 0,
  },
  BOTTOM_RIGHT: {
    x: 13,
    y: 7,
    increaseX: -1,
    increaseY: 0,
    total: 5,
    indexBase: 0,
  },
};

/**
 * Información para generar la data de los tiles en el board...
 */
const POINTS: IPoint[] = [
  {
    x: 6,
    y: 13,
    increaseX: 0,
    increaseY: -1,
    total: 5,
    indexBase: 0,
  },
  {
    x: 5,
    y: 8,
    increaseX: -1,
    increaseY: 0,
    total: 6,
    indexBase: 5,
  },
  {
    x: 0,
    y: 7,
    increaseX: 0,
    increaseY: -1,
    total: 2,
    indexBase: 11,
  },
  {
    x: 1,
    y: 6,
    increaseX: 1,
    increaseY: 0,
    total: 5,
    indexBase: 13,
  },
  {
    x: 6,
    y: 5,
    increaseX: 0,
    increaseY: -1,
    total: 6,
    indexBase: 18,
  },
  {
    x: 7,
    y: 0,
    increaseX: 1,
    increaseY: 0,
    total: 2,
    indexBase: 24,
  },
  {
    x: 8,
    y: 1,
    increaseX: 0,
    increaseY: 1,
    total: 5,
    indexBase: 26,
  },
  {
    x: 9,
    y: 6,
    increaseX: 1,
    increaseY: 0,
    total: 6,
    indexBase: 31,
  },
  {
    x: 14,
    y: 7,
    increaseX: 0,
    increaseY: 1,
    total: 2,
    indexBase: 37,
  },
  {
    x: 13,
    y: 8,
    increaseX: -1,
    increaseY: 0,
    total: 5,
    indexBase: 39,
  },
  {
    x: 8,
    y: 9,
    increaseX: 0,
    increaseY: 1,
    total: 6,
    indexBase: 44,
  },
  {
    x: 7,
    y: 14,
    increaseX: -1,
    increaseY: 0,
    total: 2,
    indexBase: 50,
  },
];

/**
 * Dado un punto determina la información de coordenadas para el tile en el board.
 * @param point
 * @returns
 */
const calculatePosition = (point: IPoint) => {
  const position: IPositionsItems[] = [];

  const { x, y, increaseX, increaseY, total, indexBase } = point;

  for (let i = 0; i < total; i++) {
    const index = indexBase + i;
    const baseX = x + increaseX * i;
    const baseY = y + increaseY * i;

    const coordinate = {
      x: SIZE_TILE * baseX,
      y: SIZE_TILE * baseY,
    };

    position.push({ index, coordinate });
  }

  return position;
};

/**
 * Home-nest soft-pad centers — must match board/styles.css:
 * circle size 1.55 tile, positions at 1.35 / 3.1 → centers 2.125 / 3.875
 * Order: TL, BL, TR, BR (same as previous getStartPositions index order)
 */
const NEST_PAD_CENTERS = [
  { x: 2.125, y: 2.125 },
  { x: 2.125, y: 3.875 },
  { x: 3.875, y: 2.125 },
  { x: 3.875, y: 3.875 },
] as const;

/**
 * Start (jail) positions: token wrapper top-left so the pin tip
 * (at wrapper center) lands on each nest soft-pad center.
 * @param nestOriginX nest top-left tile X (0 or 9)
 * @param nestOriginY nest top-left tile Y (0 or 9)
 */
const getStartPositions = (nestOriginX: number, nestOriginY: number) => {
  const half = SIZE_TILE / 2;

  return NEST_PAD_CENTERS.map((pad, index) => {
    const tipX = SIZE_TILE * (nestOriginX + pad.x);
    const tipY = SIZE_TILE * (nestOriginY + pad.y);

    return {
      index,
      coordinate: {
        x: tipX - half,
        y: tipY - half,
      },
    };
  });
};

/**
 * Indices de las celdas que se consideran seguras...
 * Sourced from shared ludo-board-constants.json (4 starts + 4 stars).
 */
export const SAFE_AREAS = [...SAFE_TILES];

/**
 * Variable que contiene la información de posición de los board en el tablero
 * se aplica flat para que quede un array unidimensional...
 */
export const POSITION_TILES = POINTS.map((point) =>
  calculatePosition(point)
).flat();

/**
 * Devuleve el total de tiles que existen en el board,
 * estos son los tiles normales, no lo de salida, en total son 52
 */
export const TOTAL_TILES = POSITION_TILES.length;

/**
 * Home column length including finish (matches HOME_COLUMN_LENGTH in board JSON).
 */
export const TOTAL_EXIT_TILES = HOME_COLUMN_LENGTH;

/**
 * Objeto que contiene las posiciones del board, dependiendo de la ubicación de cada jugador...
 */
export const POSITION_ELEMENTS_BOARD: TLocationBoardElements = {
  BOTTOM_LEFT: {
    exitTileIndex: COLOR_BOARD.RED.turningTile,
    exitTiles: calculatePosition(EXIT_TILES_VALUES.BOTTOM_LEFT),
    finalPositions: FINAL_POSITIONS_VALUES.BOTTOM_LEFT,
    startPositions: getStartPositions(0, 9),
    startTileIndex: COLOR_BOARD.RED.startTile,
  },
  TOP_LEFT: {
    exitTileIndex: COLOR_BOARD.GREEN.turningTile,
    exitTiles: calculatePosition(EXIT_TILES_VALUES.TOP_LEFT),
    finalPositions: FINAL_POSITIONS_VALUES.TOP_LEFT,
    startPositions: getStartPositions(0, 0),
    startTileIndex: COLOR_BOARD.GREEN.startTile,
  },
  TOP_RIGHT: {
    exitTileIndex: COLOR_BOARD.YELLOW.turningTile,
    exitTiles: calculatePosition(EXIT_TILES_VALUES.TOP_RIGHT),
    finalPositions: FINAL_POSITIONS_VALUES.TOP_RIGHT,
    startPositions: getStartPositions(9, 0),
    startTileIndex: COLOR_BOARD.YELLOW.startTile,
  },
  BOTTOM_RIGHT: {
    exitTileIndex: COLOR_BOARD.BLUE.turningTile,
    exitTiles: calculatePosition(EXIT_TILES_VALUES.BOTTOM_RIGHT),
    finalPositions: FINAL_POSITIONS_VALUES.BOTTOM_RIGHT,
    startPositions: getStartPositions(9, 9),
    startTileIndex: COLOR_BOARD.BLUE.startTile,
  },
};
