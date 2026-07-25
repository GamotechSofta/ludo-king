import type {
  IActionsTurn,
  IDiceList,
  IListTokens,
  IPlayer,
  IToken,
  TBoardColors,
  TColors,
  TDicevalues,
  TPositionGame,
  TTotalPlayers,
  TtypeTile,
} from "../../interfaces";
import {
  EActionsBoardGame,
  EBoardColors,
  EPositionGame,
  EtypeTile,
} from "../../utils/constants";
import { getOneBotName } from "../../data/botNames";
import type { IGameSnapshot } from "./types";
import { applyTokenCell, recomputeStacking } from "../game/rules";

/**
 * Local-player-at-bottom-left view.
 *
 * The server uses absolute RGYB geometry (RED start = shared cell 0, corners
 * clockwise RED→GREEN→YELLOW→BLUE, 13 cells per arm). To show *my* house at
 * bottom-left on every device we rotate EVERYTHING by the same offset k:
 *  - board paint scheme (RGYB/GYBR/YBRG/BRGY)
 *  - token corner (view corner = absolute corner - k)
 *  - shared path cells (view cell = server cell - 13k)
 *  - profile slots (slot = view corner)
 * Rotating all four together keeps colors on their own painted houses.
 */

const ABS_CORNER_INDEX: Record<string, number> = {
  RED: 0,
  GREEN: 1,
  YELLOW: 2,
  BLUE: 3,
};

const CORNERS_CW: TPositionGame[] = [
  EPositionGame.BOTTOM_LEFT,
  EPositionGame.TOP_LEFT,
  EPositionGame.TOP_RIGHT,
  EPositionGame.BOTTOM_RIGHT,
];

/** Scheme whose bottom-left color is RED/GREEN/YELLOW/BLUE respectively. */
const SCHEME_BY_OFFSET: TBoardColors[] = [
  EBoardColors.RGYB,
  EBoardColors.GYBR,
  EBoardColors.YBRG,
  EBoardColors.BRGY,
];

/** Backend seat order (`LudoColor.forPlayerCount`). */
const SEAT_COLOR_ORDER = ["RED", "GREEN", "YELLOW", "BLUE"] as const;

const CELLS_PER_ARM = 13;
const TOTAL_CELLS = 52;

const JAIL = -1;
const EXIT_BASE = 100;
const HOME = 200;

const isGenericBotLabel = (name?: string) =>
  !!name && /^Bot\s*\d+$/i.test(name.trim());

const displayNameCache = new Map<string, string>();

export function displayPlayerName(
  rawName: string | undefined,
  seatKey: string,
  usedNames: string[] = []
): string {
  if (rawName && !isGenericBotLabel(rawName)) {
    return rawName;
  }
  const cached = displayNameCache.get(seatKey);
  if (cached) return cached;
  const fresh = getOneBotName([
    ...usedNames,
    ...Array.from(displayNameCache.values()),
  ]);
  displayNameCache.set(seatKey, fresh);
  return fresh;
}

export function seatColorsFromSnapshot(snapshot: IGameSnapshot): TColors[] {
  const positions = snapshot.tokenPositions || {};
  const ordered = SEAT_COLOR_ORDER.filter((c) =>
    Object.prototype.hasOwnProperty.call(positions, c)
  ) as TColors[];
  if (ordered.length > 0) return ordered;
  return Object.keys(positions) as TColors[];
}

/** Rotation offset so the local player's house lands bottom-left. */
export function cornerOffsetForSeat(
  snapshot: IGameSnapshot,
  mySeat: number
): number {
  const colors = seatColorsFromSnapshot(snapshot);
  const myColor = mySeat >= 0 && mySeat < colors.length ? colors[mySeat] : null;
  if (!myColor) return 0;
  return ABS_CORNER_INDEX[myColor] ?? 0;
}

/** Board paint scheme for this device (my color painted bottom-left). */
export function boardColorForSnapshot(
  snapshot: IGameSnapshot | null,
  mySeat: number
): TBoardColors {
  if (!snapshot) return EBoardColors.RGYB;
  return SCHEME_BY_OFFSET[cornerOffsetForSeat(snapshot, mySeat)];
}

/** View corner slot (0=BL, 1=TL, 2=TR, 3=BR) for a color under offset k. */
function viewSlotForColor(color: string, k: number): number {
  const abs = ABS_CORNER_INDEX[color] ?? 0;
  return (abs - k + 4) % 4;
}

function decodeServerPos(
  serverPos: number,
  tokenIndex: number,
  k: number
): { typeTile: TtypeTile; positionTile: number } {
  if (serverPos === JAIL || serverPos < 0) {
    return { typeTile: EtypeTile.JAIL, positionTile: tokenIndex };
  }
  if (serverPos >= HOME) {
    return { typeTile: EtypeTile.END, positionTile: tokenIndex };
  }
  if (serverPos >= EXIT_BASE) {
    return {
      typeTile: EtypeTile.EXIT,
      positionTile: serverPos - EXIT_BASE,
    };
  }
  // Shared path cell — rotate into view space.
  const rotated =
    (serverPos - k * CELLS_PER_ARM + TOTAL_CELLS * 2) % TOTAL_CELLS;
  return { typeTile: EtypeTile.NORMAL, positionTile: rotated };
}

/** Players in server seat order (engine / results logic). */
export function playersFromSnapshot(snapshot: IGameSnapshot): IPlayer[] {
  const colors = seatColorsFromSnapshot(snapshot);
  const used: string[] = [];
  return colors.map((color, i) => {
    const raw = snapshot.usernames?.[i];
    const seatKey = `${snapshot.roomId || "room"}:${snapshot.userIds?.[i] || i}`;
    const name = displayPlayerName(raw, seatKey, used);
    used.push(name);
    return {
      id: snapshot.userIds?.[i] || `seat-${i}`,
      name,
      index: i,
      color,
      isBot: !!snapshot.isBot?.[i],
      isOnline: false,
      isOffline: false,
      finished: !!snapshot.finished?.[i],
      ranking: snapshot.standings?.[i] || 0,
      chatMessage: "",
      counterMessage: 0,
      isMuted: false,
    };
  });
}

/**
 * 4-slot profile array (BL, TL, TR, BR view corners). The local player's
 * house is rotated to slot 0 (bottom-left). Empty corners stay undefined —
 * ProfileSection skips them. Always render with totalPlayers=4.
 */
export function playersForView(
  snapshot: IGameSnapshot,
  mySeat: number
): IPlayer[] {
  const server = playersFromSnapshot(snapshot);
  const k = cornerOffsetForSeat(snapshot, mySeat);
  const slots: IPlayer[] = new Array(4);
  server.forEach((p) => {
    const slot = viewSlotForColor(p.color, k);
    slots[slot] = { ...p, index: slot };
  });
  return slots;
}

/** Profile slot (0..3) that currently has the turn. */
export function profileTurnIndex(
  snapshot: IGameSnapshot,
  serverSeat: number,
  mySeat: number
): number {
  const colors = seatColorsFromSnapshot(snapshot);
  const color = colors[serverSeat];
  if (!color) return 0;
  return viewSlotForColor(color, cornerOffsetForSeat(snapshot, mySeat));
}

export function listTokensFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number,
  canMove: boolean
): IListTokens[] {
  const colors = seatColorsFromSnapshot(snapshot);
  const k = cornerOffsetForSeat(snapshot, mySeat);

  const legal =
    snapshot.legalMoves?.length
      ? snapshot.legalMoves
      : (snapshot.legalTokenIndexes || []).map((tokenIndex) => ({
          tokenIndex,
          diceIndex: 0,
        }));

  const diceList: IDiceList[] = (snapshot.diceList || []).map((value, i) => ({
    key: i + 1,
    value: value as TDicevalues,
  }));

  const groups: IListTokens[] = colors.map((color, seat) => {
    const positionGame =
      CORNERS_CW[viewSlotForColor(color, k)] || EPositionGame.BOTTOM_LEFT;
    const positions = snapshot.tokenPositions[color] || [-1, -1, -1, -1];
    const tokens: IToken[] = positions.map((serverPos, tokenIndex) => {
      const { typeTile, positionTile } = decodeServerPos(
        serverPos,
        tokenIndex,
        k
      );
      let token = applyTokenCell(
        {
          color: color as TColors,
          coordinate: { x: 0, y: 0 },
          typeTile: EtypeTile.JAIL,
          positionTile: tokenIndex,
          index: tokenIndex,
          diceAvailable: [],
          canSelectToken: false,
          totalTokens: 1,
          position: 1,
          enableTooltip: false,
          isMoving: false,
          animated: false,
        },
        positionGame,
        typeTile,
        positionTile,
        false
      );

      if (canMove && seat === mySeat && snapshot.phase === "AWAITING_MOVE") {
        const movesForToken = legal.filter((m) => m.tokenIndex === tokenIndex);
        if (movesForToken.length) {
          token = {
            ...token,
            canSelectToken: true,
            enableTooltip: movesForToken.length > 1,
            diceAvailable: movesForToken
              .map((m) => diceList[m.diceIndex])
              .filter(Boolean),
            animated: true,
          };
        }
      }

      return token;
    });

    return { index: seat, positionGame, tokens };
  });

  return recomputeStacking(groups);
}

export function actionsTurnFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number,
  prev?: IActionsTurn,
  previousSeatIndex?: number
): IActionsTurn {
  const isMyTurn = snapshot.currentSeatIndex === mySeat;
  const diceList: IDiceList[] = (snapshot.diceList || []).map((value, i) => ({
    key: i + 1,
    value: value as TDicevalues,
  }));

  const awaitingRoll = snapshot.phase === "AWAITING_ROLL";
  const awaitingMove = snapshot.phase === "AWAITING_MOVE";
  const seatChanged =
    previousSeatIndex != null &&
    previousSeatIndex !== snapshot.currentSeatIndex;
  const noDiceYet = (snapshot.diceList?.length || 0) === 0;
  /** Fresh turn — show idle die on the active profile, not the last roll value. */
  const resetDiceVisual =
    seatChanged || (awaitingRoll && noDiceYet && !awaitingMove);

  return {
    timerActivated: awaitingRoll || awaitingMove,
    disabledDice: !(isMyTurn && awaitingRoll),
    showDice: true,
    diceValue: resetDiceVisual
      ? 0
      : ((prev?.diceValue || 0) as IActionsTurn["diceValue"]),
    diceList,
    diceRollNumber: resetDiceVisual ? 0 : prev?.diceRollNumber || 0,
    isDisabledUI: !isMyTurn || snapshot.phase === "FINISHED",
    actionsBoardGame: awaitingMove
      ? EActionsBoardGame.SELECT_TOKEN
      : EActionsBoardGame.ROLL_DICE,
    consecutiveSixes: snapshot.consecutiveSixes || 0,
    rollId: resetDiceVisual ? undefined : prev?.rollId,
    turnSecondsRemaining: snapshot.turnSecondsRemaining ?? 20,
    turnTimeoutSeconds: snapshot.turnTimeoutSeconds ?? 20,
  };
}

export function snapshotTokenPositionsEqual(
  a: IGameSnapshot | null | undefined,
  b: IGameSnapshot | null | undefined
): boolean {
  if (!a || !b) return false;
  const pa = a.tokenPositions || {};
  const pb = b.tokenPositions || {};
  const keysA = Object.keys(pa);
  const keysB = Object.keys(pb);
  if (keysA.length !== keysB.length) return false;
  return keysA.every((k) => {
    const ta = pa[k];
    const tb = pb[k];
    if (!ta || !tb || ta.length !== tb.length) return false;
    return ta.every((v, i) => v === tb[i]);
  });
}

export function totalPlayersFromSnapshot(
  snapshot: IGameSnapshot
): TTotalPlayers {
  const n = seatColorsFromSnapshot(snapshot).length;
  if (n === 2 || n === 3 || n === 4) return n;
  return 4;
}

export const ONLINE_BOARD_COLOR = EBoardColors.RGYB;
