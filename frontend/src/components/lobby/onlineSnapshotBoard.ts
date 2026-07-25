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
  ESufixColors,
  EtypeTile,
} from "../../utils/constants";
import { getOneBotName } from "../../data/botNames";
import type { IGameSnapshot } from "./types";
import { applyTokenCell, recomputeStacking } from "../game/rules";

/** Default RGYB art: which house each server color owns. */
const DEFAULT_COLOR_CORNER: Record<string, TPositionGame> = {
  RED: EPositionGame.BOTTOM_LEFT,
  GREEN: EPositionGame.TOP_LEFT,
  YELLOW: EPositionGame.TOP_RIGHT,
  BLUE: EPositionGame.BOTTOM_RIGHT,
};

/** Seat order used by the Spring backend (`LudoColor.forPlayerCount`). */
const SEAT_COLOR_ORDER = ["RED", "GREEN", "YELLOW", "BLUE"] as const;

const CORNERS_CW: TPositionGame[] = [
  EPositionGame.BOTTOM_LEFT,
  EPositionGame.TOP_LEFT,
  EPositionGame.TOP_RIGHT,
  EPositionGame.BOTTOM_RIGHT,
];

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

/**
 * Map boardColor scheme (e.g. YBRG) → each paint color's house corner.
 * Must stay in sync with `.game-board.YBRG` CSS variables.
 */
export function cornerMapForBoardColor(
  boardColor: TBoardColors
): Record<string, TPositionGame> {
  const letters = String(boardColor).split("");
  const map: Record<string, TPositionGame> = {};
  letters.forEach((letter, i) => {
    const color = ESufixColors[letter as keyof typeof ESufixColors];
    if (color && CORNERS_CW[i]) {
      map[color] = CORNERS_CW[i];
    }
  });
  return map;
}

/**
 * Board CSS class so `myColor` is painted at bottom-left.
 * 3-player online (R,G,Y) can't remapping cleanly onto BL/TL/TR profiles —
 * those matches use CSS rotate instead (see OnlineGame).
 */
export function boardColorForSeatColor(
  color?: string,
  totalPlayers?: number
): TBoardColors {
  if (totalPlayers === 3) {
    return EBoardColors.RGYB;
  }
  switch ((color || "RED").toUpperCase()) {
    case "GREEN":
      return EBoardColors.GYBR;
    case "YELLOW":
      return EBoardColors.YBRG;
    case "BLUE":
      return EBoardColors.BRGY;
    default:
      return EBoardColors.RGYB;
  }
}

/** Degrees to rotate the whole board so `color`'s house sits at bottom-left. */
export function boardRotationDegForColor(color?: string): number {
  const corner =
    DEFAULT_COLOR_CORNER[(color || "RED").toUpperCase()] ||
    EPositionGame.BOTTOM_LEFT;
  const idx = CORNERS_CW.indexOf(corner);
  return idx <= 0 ? 0 : -idx * 90;
}

export function visualSeatIndex(
  serverSeat: number,
  mySeat: number,
  totalPlayers: number
): number {
  if (totalPlayers <= 0 || mySeat < 0) return serverSeat;
  return (serverSeat - mySeat + totalPlayers) % totalPlayers;
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

/**
 * Colors in backend seat order (never Object.keys — that can desync seats).
 */
export function seatColorsFromSnapshot(snapshot: IGameSnapshot): TColors[] {
  const positions = snapshot.tokenPositions || {};
  const ordered = SEAT_COLOR_ORDER.filter((c) =>
    Object.prototype.hasOwnProperty.call(positions, c)
  ) as TColors[];
  if (ordered.length > 0) return ordered;
  return Object.keys(positions) as TColors[];
}

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

/** Local seat becomes profile index 0 (bottom-left). */
export function playersForView(
  snapshot: IGameSnapshot,
  mySeat: number
): IPlayer[] {
  const server = playersFromSnapshot(snapshot);
  const n = server.length;
  if (n === 0 || mySeat < 0) return server;
  return server.map((_, i) => {
    const seat = (mySeat + i) % n;
    return { ...server[seat], index: i };
  });
}

export function listTokensFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number,
  canMove: boolean
): IListTokens[] {
  const colors = seatColorsFromSnapshot(snapshot);
  const n = colors.length;
  const myColor = mySeat >= 0 && mySeat < n ? colors[mySeat] : colors[0];
  const boardColor = boardColorForSeatColor(myColor, n);
  const cornerMap = cornerMapForBoardColor(boardColor);

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
    // Always place tokens in the house that matches this color on the
    // current board scheme (never a different seat's corner).
    const positionGame =
      cornerMap[color] ||
      DEFAULT_COLOR_CORNER[color] ||
      EPositionGame.BOTTOM_LEFT;
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
