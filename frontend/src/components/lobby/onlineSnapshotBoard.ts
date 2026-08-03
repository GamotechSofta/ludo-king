import type {
  IActionsTurn,
  IDiceList,
  IListTokens,
  IPlayer,
  IToken,
  TColors,
  TDicevalues,
  TtypeTile,
} from "../../interfaces";
import {
  EActionsBoardGame,
  EBoardColors,
  EtypeTile,
  MAX_PLAYER_CHANCES,
} from "../../utils/constants";
import { getOneBotName } from "../../data/botNames";
import type { IGameSnapshot } from "./types";
import { onlineDiceDisabled } from "./diceTurnLogic";
import { applyTokenCell, recomputeStacking } from "../game/rules";
import {
  boardColorForSnapshot,
  colorToViewCorner,
  cornerOffsetForSeat,
  decodeServerPosForView,
  playerCountFromSnapshot,
  profileIndexForServerSeat,
  seatColorsFromSnapshot,
  viewPositionGameForColor,
  VIEW_CORNERS_BY_PLAYER_COUNT,
} from "./onlineViewMapping";

export {
  boardColorForSnapshot,
  cornerOffsetForSeat,
  seatColorsFromSnapshot,
  playerCountFromSnapshot as totalPlayersFromSnapshot,
  resolveLocalSeatIndex,
  profileIndexForServerSeat,
  perspectiveKey,
} from "./onlineViewMapping";

const JAIL_POSITION = -1;

const isGenericBotLabel = (name?: string) =>
  !!name && /^Bot\s*\d+$/i.test(name.trim());
const isGenericPlayerLabel = (name?: string) =>
  !!name && /^Player(?:\s*\d+)?$/i.test(name.trim());

const displayNameCache = new Map<string, string>();

export function clearDisplayNameCache(roomId?: string) {
  if (!roomId) {
    displayNameCache.clear();
    return;
  }
  const prefix = `${roomId}:`;
  for (const key of displayNameCache.keys()) {
    if (key.startsWith(prefix)) {
      displayNameCache.delete(key);
    }
  }
}

export function seatDisplayKey(
  roomId: string,
  userId: string | undefined,
  seatIndex: number
): string {
  return `${roomId}:${userId || `seat-${seatIndex}`}`;
}

export function displayPlayerName(
  rawName: string | undefined,
  seatKey: string,
  usedNames: string[] = [],
  isBot = false
): string {
  // A later authoritative snapshot may replace the initial generic fallback
  // with the authenticated platform name. Never let the cache hide that update.
  if (rawName && !isGenericBotLabel(rawName) && !isGenericPlayerLabel(rawName)) {
    const stable = rawName.trim();
    displayNameCache.set(seatKey, stable);
    return stable;
  }

  const cached = displayNameCache.get(seatKey);
  if (cached) return cached;

  if (!isBot) {
    const base = rawName?.trim() || "Player";
    const fallback = isGenericPlayerLabel(base)
      ? `Player ${usedNames.length + 1}`
      : base;
    displayNameCache.set(seatKey, fallback);
    return fallback;
  }

  const fresh = getOneBotName([
    ...usedNames,
    ...Array.from(displayNameCache.values()),
  ]);
  displayNameCache.set(seatKey, fresh);
  return fresh;
}

export function viewTileFromServerPos(
  serverPos: number,
  tokenIndex: number,
  snapshot: IGameSnapshot,
  mySeat: number
): { typeTile: TtypeTile; positionTile: number } {
  const k = cornerOffsetForSeat(snapshot, mySeat);
  const decoded = decodeServerPosForView(serverPos, tokenIndex, k);
  return {
    typeTile: decoded.typeTile as TtypeTile,
    positionTile: decoded.positionTile,
  };
}

/**
 * Pawns the server just sent back to the yard, keyed by server seat. Derived
 * from the snapshot diff so the walk-back never animates the wrong pawn.
 */
export function capturedVictimsFromSnapshots(
  prev: IGameSnapshot | null | undefined,
  snapshot: IGameSnapshot,
  moverSeat: number
): Array<{ playerIndex: number; tokenIndex: number }> {
  if (!prev) return [];
  const victims: Array<{ playerIndex: number; tokenIndex: number }> = [];
  seatColorsFromSnapshot(snapshot).forEach((color, seat) => {
    if (seat === moverSeat) return;
    const before = prev.tokenPositions?.[color];
    const after = snapshot.tokenPositions?.[color];
    if (!before || !after) return;
    after.forEach((pos, tokenIndex) => {
      const wasAt = before[tokenIndex];
      if (pos === JAIL_POSITION && wasAt != null && wasAt !== JAIL_POSITION) {
        victims.push({ playerIndex: seat, tokenIndex });
      }
    });
  });
  return victims;
}

export function playersFromSnapshot(
  snapshot: IGameSnapshot,
  stableRoomId?: string
): IPlayer[] {
  const roomId = stableRoomId || snapshot.roomId || "room";
  const colors = seatColorsFromSnapshot(snapshot);
  const used: string[] = [];
  return colors.map((color, i) => {
    const raw = snapshot.usernames?.[i];
    const isBot = !!snapshot.isBot?.[i];
    const stableSeatKey = isBot
      ? `${roomId}:bot-seat-${i}`
      : seatDisplayKey(roomId, snapshot.userIds?.[i], i);
    const name = displayPlayerName(raw, stableSeatKey, used, isBot);
    used.push(name);
    return {
      id: snapshot.userIds?.[i] || `seat-${i}`,
      name,
      index: i,
      color,
      isBot,
      isOnline: false,
      isOffline: false,
      finished: !!snapshot.finished?.[i],
      ranking: snapshot.standings?.[i] === 1 ? 1 : 0,
      chatMessage: "",
      counterMessage: 0,
      isMuted: false,
      timeoutStreak: snapshot.consecutiveTimeouts?.[i] ?? 0,
      isEliminated:
        !!snapshot.eliminated?.[i] ||
        (snapshot.consecutiveTimeouts?.[i] ?? 0) >= MAX_PLAYER_CHANCES,
    };
  });
}

/**
 * Profile-ready list: local player always at compact index 0 (bottom-left).
 * 2P → [BL, TR], 3P → [BL, TL, TR], 4P → all corners.
 */
export function playersForView(
  snapshot: IGameSnapshot,
  mySeat: number,
  stableRoomId?: string
): IPlayer[] {
  if (mySeat < 0) return [];
  const server = playersFromSnapshot(snapshot, stableRoomId);
  const k = cornerOffsetForSeat(snapshot, mySeat);
  const count = playerCountFromSnapshot(snapshot);
  const activeCorners = VIEW_CORNERS_BY_PLAYER_COUNT[count];

  const byCorner: (IPlayer | undefined)[] = new Array(4);
  server.forEach((p) => {
    const corner = colorToViewCorner(p.color, k);
    byCorner[corner] = { ...p };
  });

  return activeCorners
    .map((corner, compactIndex) => {
      const p = byCorner[corner];
      if (!p) return null;
      return { ...p, index: compactIndex };
    })
    .filter((p): p is IPlayer => p != null);
}

export function profileTurnIndex(
  snapshot: IGameSnapshot,
  serverSeat: number,
  mySeat: number
): number {
  if (mySeat < 0) return 0;
  return profileIndexForServerSeat(serverSeat, snapshot, mySeat);
}

export function listTokensFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number,
  canMove: boolean
): IListTokens[] {
  if (mySeat < 0) return [];
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
    const positionGame = viewPositionGameForColor(color, snapshot, mySeat);
    const positions = snapshot.tokenPositions[color] || [-1, -1, -1, -1];
    const tokens: IToken[] = positions.map((serverPos, tokenIndex) => {
      const decoded = decodeServerPosForView(serverPos, tokenIndex, k);
      const typeTile = decoded.typeTile as TtypeTile;
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
        decoded.positionTile,
        false
      );

      if (
        canMove &&
        seat === snapshot.currentSeatIndex &&
        seat === mySeat &&
        snapshot.phase === "AWAITING_MOVE"
      ) {
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
  const isMyTurn = mySeat >= 0 && snapshot.currentSeatIndex === mySeat;
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
  const resetDiceVisual = seatChanged;
  const fromList =
    !noDiceYet && snapshot.diceList
      ? (snapshot.diceList[snapshot.diceList.length - 1] as TDicevalues)
      : 0;
  const keptFace = (fromList || prev?.diceValue || 0) as IActionsTurn["diceValue"];

  return {
    timerActivated: awaitingRoll || awaitingMove,
    disabledDice: onlineDiceDisabled(snapshot, mySeat),
    showDice: true,
    diceValue: resetDiceVisual ? 0 : keptFace,
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

export const ONLINE_BOARD_COLOR = EBoardColors.RGYB;
