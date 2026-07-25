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
 * Fixed RGYB board art. Online path cells from the server are absolute for this
 * layout — never CSS-rotate or swap boardColor schemes, or tokens land on the
 * wrong painted paths (e.g. red piece on green home stretch).
 */
const COLOR_CORNER: Record<string, TPositionGame> = {
  RED: EPositionGame.BOTTOM_LEFT,
  GREEN: EPositionGame.TOP_LEFT,
  YELLOW: EPositionGame.TOP_RIGHT,
  BLUE: EPositionGame.BOTTOM_RIGHT,
};

/** Backend seat order (`LudoColor.forPlayerCount`). */
const SEAT_COLOR_ORDER = ["RED", "GREEN", "YELLOW", "BLUE"] as const;

/**
 * Profile slot index (0-based) for each house on the fixed board.
 * Matches ProfileSection DISTRIBUTION_PROFILES (1-based there).
 * 2p: BL + TR only. 3p: BL + TL + TR. 4p: all four.
 */
const PROFILE_INDEX_BY_CORNER: Record<
  number,
  Partial<Record<TPositionGame, number>>
> = {
  2: {
    [EPositionGame.BOTTOM_LEFT]: 0,
    [EPositionGame.TOP_RIGHT]: 1,
  },
  3: {
    [EPositionGame.BOTTOM_LEFT]: 0,
    [EPositionGame.TOP_LEFT]: 1,
    [EPositionGame.TOP_RIGHT]: 2,
  },
  4: {
    [EPositionGame.BOTTOM_LEFT]: 0,
    [EPositionGame.TOP_LEFT]: 1,
    [EPositionGame.TOP_RIGHT]: 2,
    [EPositionGame.BOTTOM_RIGHT]: 3,
  },
};

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

/** Always default art — do not remap. */
export function boardColorForSeatColor(
  _color?: string,
  _totalPlayers?: number
): TBoardColors {
  return EBoardColors.RGYB;
}

/** Always 0 — do not rotate. */
export function boardRotationDegForColor(_color?: string): number {
  return 0;
}

export function visualSeatIndex(
  serverSeat: number,
  _mySeat: number,
  _totalPlayers: number
): number {
  // Profiles follow house color, not "local player = 0".
  return serverSeat;
}

function decodeServerPos(
  serverPos: number,
  tokenIndex: number
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
  return { typeTile: EtypeTile.NORMAL, positionTile: serverPos };
}

export function seatColorsFromSnapshot(snapshot: IGameSnapshot): TColors[] {
  const positions = snapshot.tokenPositions || {};
  const ordered = SEAT_COLOR_ORDER.filter((c) =>
    Object.prototype.hasOwnProperty.call(positions, c)
  ) as TColors[];
  if (ordered.length > 0) return ordered;
  return Object.keys(positions) as TColors[];
}

function profileIndexForColor(color: string, totalPlayers: number): number {
  const corner = COLOR_CORNER[color] || EPositionGame.BOTTOM_LEFT;
  const map = PROFILE_INDEX_BY_CORNER[totalPlayers] || PROFILE_INDEX_BY_CORNER[4];
  const idx = map[corner];
  return typeof idx === "number" ? idx : 0;
}

/** Players in server seat order (engine / capture). */
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
 * Players ordered for ProfileSection slots so each profile sits by its house
 * (red bottom-left, green top-left, …). No fake "you are always bottom" rotate.
 */
export function playersForView(
  snapshot: IGameSnapshot,
  _mySeat: number
): IPlayer[] {
  const server = playersFromSnapshot(snapshot);
  const n = server.length;
  if (n === 0) return server;

  const slots: IPlayer[] = new Array(n);
  server.forEach((p) => {
    const slot = profileIndexForColor(p.color, n);
    slots[slot] = { ...p, index: slot };
  });

  // Fill any hole (shouldn't happen) from leftover seats.
  let next = 0;
  server.forEach((p) => {
    if (slots.some((s) => s && s.id === p.id)) return;
    while (next < n && slots[next]) next += 1;
    if (next < n) {
      slots[next] = { ...p, index: next };
      next += 1;
    }
  });

  return slots.filter(Boolean);
}

/** Profile currentTurn index for a server seat. */
export function profileTurnIndex(
  snapshot: IGameSnapshot,
  serverSeat: number
): number {
  const colors = seatColorsFromSnapshot(snapshot);
  const color = colors[serverSeat];
  if (!color) return serverSeat;
  return profileIndexForColor(color, colors.length);
}

export function listTokensFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number,
  canMove: boolean
): IListTokens[] {
  const colors = seatColorsFromSnapshot(snapshot);

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
    const positionGame = COLOR_CORNER[color] || EPositionGame.BOTTOM_LEFT;
    const positions = snapshot.tokenPositions[color] || [-1, -1, -1, -1];
    const tokens: IToken[] = positions.map((serverPos, tokenIndex) => {
      const { typeTile, positionTile } = decodeServerPos(serverPos, tokenIndex);
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
  prev?: IActionsTurn
): IActionsTurn {
  const isMyTurn = snapshot.currentSeatIndex === mySeat;
  const diceList: IDiceList[] = (snapshot.diceList || []).map((value, i) => ({
    key: i + 1,
    value: value as TDicevalues,
  }));

  const awaitingRoll = snapshot.phase === "AWAITING_ROLL";
  const awaitingMove = snapshot.phase === "AWAITING_MOVE";

  return {
    timerActivated: awaitingRoll || awaitingMove,
    disabledDice: !(isMyTurn && awaitingRoll),
    showDice: true,
    diceValue: (prev?.diceValue || 0) as IActionsTurn["diceValue"],
    diceList,
    diceRollNumber: prev?.diceRollNumber || 0,
    isDisabledUI: !isMyTurn || snapshot.phase === "FINISHED",
    actionsBoardGame: awaitingMove
      ? EActionsBoardGame.SELECT_TOKEN
      : EActionsBoardGame.ROLL_DICE,
    consecutiveSixes: snapshot.consecutiveSixes || 0,
    rollId: prev?.rollId,
    turnSecondsRemaining: snapshot.turnSecondsRemaining ?? 20,
    turnTimeoutSeconds: snapshot.turnTimeoutSeconds ?? 20,
  };
}

export function totalPlayersFromSnapshot(
  snapshot: IGameSnapshot
): TTotalPlayers {
  const n = seatColorsFromSnapshot(snapshot).length;
  if (n === 2 || n === 3 || n === 4) return n;
  return 4;
}

export const ONLINE_BOARD_COLOR = EBoardColors.RGYB;
