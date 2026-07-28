import type { TBoardColors, TColors, TPositionGame, TTotalPlayers } from "../../interfaces";
import { EBoardColors, EPositionGame } from "../../utils/constants";
import type { IGameSnapshot } from "./types";

/**
 * UI-only local perspective for online multiplayer.
 *
 * Server keeps absolute RGYB geometry + seat indices. Each client maps:
 *   serverSeat / serverColor → viewCorner → profileIndex / boardScheme
 *
 * Never mutate server snapshot data — read-only transforms for rendering.
 */

export type ViewCorner = 0 | 1 | 2 | 3;

const ABS_CORNER_INDEX: Record<string, ViewCorner> = {
  RED: 0,
  GREEN: 1,
  YELLOW: 2,
  BLUE: 3,
};

export const VIEW_CORNER_TO_POSITION: TPositionGame[] = [
  EPositionGame.BOTTOM_LEFT,
  EPositionGame.TOP_LEFT,
  EPositionGame.TOP_RIGHT,
  EPositionGame.BOTTOM_RIGHT,
];

const SCHEME_BY_OFFSET: TBoardColors[] = [
  EBoardColors.RGYB,
  EBoardColors.GYBR,
  EBoardColors.YBRG,
  EBoardColors.BRGY,
];

/** Active view corners per player count (matches ProfileSection layout). */
export const VIEW_CORNERS_BY_PLAYER_COUNT: Record<TTotalPlayers, ViewCorner[]> = {
  2: [0, 2],
  3: [0, 1, 2],
  4: [0, 1, 2, 3],
};

const CELLS_PER_ARM = 13;
const TOTAL_CELLS = 52;
const JAIL = -1;
const EXIT_BASE = 100;
const HOME = 200;

export function seatColorsFromSnapshot(snapshot: IGameSnapshot): TColors[] {
  const seats = snapshot.userIds?.length || 0;
  if (
    snapshot.seatColors?.length &&
    (seats === 0 || snapshot.seatColors.length === seats)
  ) {
    return snapshot.seatColors as TColors[];
  }
  const positions = snapshot.tokenPositions || {};
  return Object.keys(positions) as TColors[];
}

/** Resolve this device's server seat index — never guess seat 0. */
export function resolveLocalSeatIndex(
  snapshot: IGameSnapshot | null | undefined,
  guestId: string,
  guestName?: string,
  guestUsername?: string
): number {
  if (!snapshot) return -1;
  if (snapshot.userIds?.length) {
    const byId = snapshot.userIds.findIndex(
      (id) =>
        id === guestId ||
        (id && guestId && id.toLowerCase() === guestId.toLowerCase())
    );
    if (byId >= 0) return byId;
  }
  const aliases = [guestUsername, guestName].filter(
    (n): n is string => !!n && n.trim().length > 0
  );
  if (snapshot.usernames?.length && aliases.length) {
    for (const alias of aliases) {
      const idx = snapshot.usernames.findIndex((u) => u === alias);
      if (idx >= 0) return idx;
    }
  }
  return -1;
}

export function playerCountFromSnapshot(snapshot: IGameSnapshot): TTotalPlayers {
  const n = seatColorsFromSnapshot(snapshot).length;
  if (n === 2 || n === 3 || n === 4) return n;
  return 4;
}

/** k = how many corners clockwise to rotate so my color sits at bottom-left. */
export function cornerOffsetForSeat(
  snapshot: IGameSnapshot,
  mySeat: number
): ViewCorner {
  const colors = seatColorsFromSnapshot(snapshot);
  const myColor = mySeat >= 0 && mySeat < colors.length ? colors[mySeat] : null;
  if (!myColor) return 0;
  return ABS_CORNER_INDEX[myColor] ?? 0;
}

export function boardColorForOffset(k: ViewCorner): TBoardColors {
  return SCHEME_BY_OFFSET[k];
}

export function boardColorForSnapshot(
  snapshot: IGameSnapshot | null,
  mySeat: number
): TBoardColors {
  if (!snapshot || mySeat < 0) return EBoardColors.RGYB;
  return boardColorForOffset(cornerOffsetForSeat(snapshot, mySeat));
}

export function colorToViewCorner(color: string, k: ViewCorner): ViewCorner {
  const abs = ABS_CORNER_INDEX[color] ?? 0;
  return ((abs - k + 4) % 4) as ViewCorner;
}

export function serverSeatToViewCorner(
  serverSeat: number,
  snapshot: IGameSnapshot,
  mySeat: number
): ViewCorner {
  const colors = seatColorsFromSnapshot(snapshot);
  const color = colors[serverSeat];
  if (!color) return 0;
  return colorToViewCorner(color, cornerOffsetForSeat(snapshot, mySeat));
}

export function viewCornerToProfileIndex(
  corner: ViewCorner,
  playerCount: TTotalPlayers
): number {
  const corners = VIEW_CORNERS_BY_PLAYER_COUNT[playerCount] ?? [0, 1, 2, 3];
  const idx = corners.indexOf(corner);
  return idx >= 0 ? idx : 0;
}

export function profileIndexForServerSeat(
  serverSeat: number,
  snapshot: IGameSnapshot,
  mySeat: number
): number {
  const corner = serverSeatToViewCorner(serverSeat, snapshot, mySeat);
  return viewCornerToProfileIndex(corner, playerCountFromSnapshot(snapshot));
}

export function viewPositionGameForColor(
  color: string,
  snapshot: IGameSnapshot,
  mySeat: number
): TPositionGame {
  const k = cornerOffsetForSeat(snapshot, mySeat);
  return VIEW_CORNER_TO_POSITION[colorToViewCorner(color, k)];
}

export function rotateSharedPathCell(serverPos: number, k: ViewCorner): number {
  return (serverPos - k * CELLS_PER_ARM + TOTAL_CELLS * 2) % TOTAL_CELLS;
}

export function decodeServerPosForView(
  serverPos: number,
  tokenIndex: number,
  k: ViewCorner
): { typeTile: "JAIL" | "NORMAL" | "EXIT" | "END"; positionTile: number } {
  if (serverPos === JAIL || serverPos < 0) {
    return { typeTile: "JAIL", positionTile: tokenIndex };
  }
  if (serverPos >= HOME) {
    return { typeTile: "END", positionTile: tokenIndex };
  }
  if (serverPos >= EXIT_BASE) {
    return { typeTile: "EXIT", positionTile: serverPos - EXIT_BASE };
  }
  return {
    typeTile: "NORMAL",
    positionTile: rotateSharedPathCell(serverPos, k),
  };
}

/** Stable key to detect when this device's perspective must be recomputed. */
export function perspectiveKey(
  roomId: string,
  snapshot: IGameSnapshot,
  mySeat: number
): string {
  const colors = seatColorsFromSnapshot(snapshot);
  const myColor = mySeat >= 0 ? colors[mySeat] ?? "" : "";
  return `${roomId}|${mySeat}|${myColor}|${playerCountFromSnapshot(snapshot)}`;
}
