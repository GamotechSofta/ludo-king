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

/** Absolute house corners on the default RGYB art (server color → art corner). */
const COLOR_CORNER: Record<string, TPositionGame> = {
  RED: EPositionGame.BOTTOM_LEFT,
  GREEN: EPositionGame.TOP_LEFT,
  YELLOW: EPositionGame.TOP_RIGHT,
  BLUE: EPositionGame.BOTTOM_RIGHT,
};

const COLOR_ORDER = ["RED", "GREEN", "YELLOW", "BLUE"] as const;

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
 * Board CSS scheme so `color` sits at bottom-left (same as offline).
 * Avoids CSS rotate() which flips the board ("ulata").
 */
export function boardColorForSeatColor(color?: string): TBoardColors {
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

/** @deprecated No longer rotates — use boardColorForSeatColor. */
export function boardRotationDegForColor(_color: string): number {
  return 0;
}

/** View corners for player index 0..n-1 (0 = you at bottom-left). */
function viewCornersForPlayerCount(totalPlayers: number): TPositionGame[] {
  if (totalPlayers === 2) {
    return [EPositionGame.BOTTOM_LEFT, EPositionGame.TOP_RIGHT];
  }
  if (totalPlayers === 3) {
    return [
      EPositionGame.BOTTOM_LEFT,
      EPositionGame.TOP_LEFT,
      EPositionGame.TOP_RIGHT,
    ];
  }
  return [
    EPositionGame.BOTTOM_LEFT,
    EPositionGame.TOP_LEFT,
    EPositionGame.TOP_RIGHT,
    EPositionGame.BOTTOM_RIGHT,
  ];
}

/** Map a server seat index into view order where `mySeat` is always 0. */
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

export function seatColorsFromSnapshot(snapshot: IGameSnapshot): TColors[] {
  const positions = snapshot.tokenPositions || {};
  const present = COLOR_ORDER.filter((c) =>
    Object.prototype.hasOwnProperty.call(positions, c)
  ) as TColors[];
  if (present.length > 0) return present;
  return Object.keys(positions) as TColors[];
}

/** Players in server seat order. */
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

/** Players rotated so local seat is index 0 (bottom-left profile). */
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
  const corners = viewCornersForPlayerCount(n);
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
    // View corner for this seat (you = bottom-left); boardColor remaps house colors.
    const visual = visualSeatIndex(seat, mySeat, n);
    const positionGame =
      corners[visual] || COLOR_CORNER[color] || EPositionGame.BOTTOM_LEFT;
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
