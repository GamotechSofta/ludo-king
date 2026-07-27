import type { IDiceList, IListTokens } from "../../interfaces";
import { DICE_VALUE_GET_OUT_JAIL, EtypeTile } from "../../utils/constants";
import { getPossibleMoves, shouldAutoExitJailOnFirstSix } from "./rules";
import type { IGameSnapshot } from "../lobby/types";
import { seatColorsFromSnapshot } from "../lobby/onlineSnapshotBoard";

export type HumanLegalMove = { tokenIndex: number; diceIndex: number };

const JAIL = -1;

/** Normalize server or offline legal move lists. */
export function resolveHumanLegalMoves(
  legalMoves?: HumanLegalMove[],
  legalTokenIndexes?: number[]
): HumanLegalMove[] {
  if (legalMoves?.length) return legalMoves;
  return (legalTokenIndexes || []).map((tokenIndex) => ({
    tokenIndex,
    diceIndex: 0,
  }));
}

/**
 * Pick a move only when human auto-selection is allowed:
 * - all pawns in jail + rolled 6 → release one pawn
 * - exactly one pawn has any legal move → move it (highest dice if multiple)
 * - two or more pawns can move → null (manual choice)
 */
export function pickHumanAutoMove(
  legalMoves: HumanLegalMove[],
  allInJail: boolean,
  diceValues: number[]
): HumanLegalMove | null {
  if (!legalMoves.length) return null;

  if (
    allInJail &&
    shouldAutoExitJailOnFirstSix([true, true, true, true], diceValues)
  ) {
    return legalMoves[0];
  }

  // Single legal move — always auto (one pawn, one path)
  if (legalMoves.length === 1) {
    return legalMoves[0];
  }

  const tokensWithMoves = new Set(legalMoves.map((m) => m.tokenIndex));
  if (tokensWithMoves.size !== 1) return null;

  const soleToken = [...tokensWithMoves][0];
  const pawnMoves = legalMoves.filter((m) => m.tokenIndex === soleToken);
  return [...pawnMoves].sort(
    (a, b) => (diceValues[b.diceIndex] ?? 0) - (diceValues[a.diceIndex] ?? 0)
  )[0];
}

/** Drop jail entries when the rolled dice cannot release them (bad index fallback). */
export function filterLegalMovesForPositions(
  legal: HumanLegalMove[],
  positions: number[],
  diceValues: number[]
): HumanLegalMove[] {
  return legal.filter((m) => {
    const pos = positions[m.tokenIndex] ?? JAIL;
    if (pos === JAIL) {
      const dice = diceValues[m.diceIndex] ?? diceValues[0] ?? 0;
      return dice === DICE_VALUE_GET_OUT_JAIL;
    }
    return true;
  });
}

export function pickHumanAutoMoveOffline(
  listTokens: IListTokens[],
  playerIndex: number,
  diceList: IDiceList[]
): HumanLegalMove | null {
  const moves = getPossibleMoves(listTokens, playerIndex, diceList);
  const tokens = listTokens[playerIndex]?.tokens ?? [];
  const allInJail =
    tokens.length > 0 &&
    tokens.every((t) => t.typeTile === EtypeTile.JAIL);
  const diceValues = diceList.map((d) => d.value);
  return pickHumanAutoMove(moves, allInJail, diceValues);
}

export function pickHumanAutoMoveFromSnapshot(
  snapshot: IGameSnapshot,
  mySeat: number
): HumanLegalMove | null {
  const legal = resolveHumanLegalMoves(
    snapshot.legalMoves,
    snapshot.legalTokenIndexes
  );
  const colors = seatColorsFromSnapshot(snapshot);
  const color = colors[mySeat];
  if (!color) return null;
  const positions = snapshot.tokenPositions[color] || [];
  const allInJail =
    positions.length > 0 && positions.every((p) => p === JAIL);
  const diceValues = snapshot.diceList || [];
  const filtered = filterLegalMovesForPositions(legal, positions, diceValues);
  return pickHumanAutoMove(filtered, allInJail, diceValues);
}

/** True when auto-move should fire (single obvious pawn or jail exit on 6). */
export function shouldHumanAutoMove(
  legalMoves: HumanLegalMove[],
  allInJail: boolean,
  diceValues: number[]
): boolean {
  return pickHumanAutoMove(legalMoves, allInJail, diceValues) != null;
}
