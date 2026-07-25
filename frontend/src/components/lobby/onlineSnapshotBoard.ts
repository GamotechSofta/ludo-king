import type {
  IActionsTurn,
  IDiceList,
  IListTokens,
  IPlayer,
  IToken,
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
import type { IGameSnapshot } from "./types";
import { applyTokenCell, recomputeStacking } from "../game/rules";

const COLOR_CORNER: Record<string, TPositionGame> = {
  RED: EPositionGame.BOTTOM_LEFT,
  GREEN: EPositionGame.TOP_LEFT,
  YELLOW: EPositionGame.TOP_RIGHT,
  BLUE: EPositionGame.BOTTOM_RIGHT,
};

/** Clockwise from bottom-left — matches RGYB board house order. */
const CORNERS_CW: TPositionGame[] = [
  EPositionGame.BOTTOM_LEFT,
  EPositionGame.TOP_LEFT,
  EPositionGame.TOP_RIGHT,
  EPositionGame.BOTTOM_RIGHT,
];

const JAIL = -1;
const EXIT_BASE = 100;
const HOME = 200;

/**
 * CSS degrees to rotate the board so `color`'s house sits at bottom-left.
 * (Negative = counter-clockwise.)
 */
export function boardRotationDegForColor(color: string): number {
  const corner = COLOR_CORNER[color] || EPositionGame.BOTTOM_LEFT;
  const idx = CORNERS_CW.indexOf(corner);
  return idx <= 0 ? 0 : -idx * 90;
}

/** Map a server seat index into view order where `mySeat` is always 0 (bottom-left). */
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
  return Object.keys(snapshot.tokenPositions || {}) as TColors[];
}

/** Players in server seat order (for engine / capture logic). */
export function playersFromSnapshot(snapshot: IGameSnapshot): IPlayer[] {
  const colors = seatColorsFromSnapshot(snapshot);
  return colors.map((color, i) => ({
    id: snapshot.userIds?.[i] || `seat-${i}`,
    name: snapshot.usernames?.[i] || `Player ${i + 1}`,
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
  }));
}

/**
 * Players rotated so the local seat is index 0 (bottom-left profile).
 * Use this for ProfileSection, not for token/capture logic.
 */
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
            diceAvailable: movesForToken.map((m) => diceList[m.diceIndex]).filter(Boolean),
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
