import type { IGameSnapshot } from "./types";

/** Gate flags for online roll requests (no UI — logic only). */
export type OnlineRollGate = {
  mySeat: number;
  isBusy: boolean;
  isAnimating: boolean;
  isRolling: boolean;
  isActionInFlight: boolean;
  disabledDice: boolean;
};

export function isCurrentPlayer(
  snapshot: IGameSnapshot,
  seat: number
): boolean {
  return seat >= 0 && snapshot.currentSeatIndex === seat;
}

export function isAwaitingRoll(snapshot: IGameSnapshot): boolean {
  return snapshot.phase === "AWAITING_ROLL";
}

export function isAwaitingMove(snapshot: IGameSnapshot): boolean {
  return snapshot.phase === "AWAITING_MOVE";
}

export function isGameFinished(snapshot: IGameSnapshot): boolean {
  return snapshot.phase === "FINISHED";
}

/**
 * Ludo King: only the active seat may roll, once per AWAITING_ROLL phase,
 * never while busy, animating, or a network action is in flight.
 */
export function canRequestOnlineRoll(
  snapshot: IGameSnapshot | null,
  gate: OnlineRollGate
): boolean {
  if (!snapshot || gate.mySeat < 0) return false;
  if (isGameFinished(snapshot)) return false;
  if (gate.isBusy || gate.isAnimating || gate.isRolling || gate.isActionInFlight) {
    return false;
  }
  if (gate.disabledDice) return false;
  if (!isCurrentPlayer(snapshot, gate.mySeat)) return false;
  if (!isAwaitingRoll(snapshot)) return false;
  if ((snapshot.diceList?.length ?? 0) > 0) return false;
  return true;
}

/** Dice button disabled for every non-active player and during AWAITING_MOVE. */
export function onlineDiceDisabled(
  snapshot: IGameSnapshot,
  mySeat: number
): boolean {
  if (mySeat < 0 || isGameFinished(snapshot)) return true;
  if (!isCurrentPlayer(snapshot, mySeat)) return true;
  if (!isAwaitingRoll(snapshot)) return true;
  if ((snapshot.diceList?.length ?? 0) > 0) return true;
  return false;
}

/** Token selection allowed only for active player in AWAITING_MOVE with a die. */
export function shouldEnableTokenSelection(
  snapshot: IGameSnapshot,
  mySeat: number
): boolean {
  return (
    isCurrentPlayer(snapshot, mySeat) &&
    isAwaitingMove(snapshot) &&
    (snapshot.diceList?.length ?? 0) > 0
  );
}

/** Dedup key for rollDone / auto-jail — ties to server actionSeq. */
export function buildRollDedupKey(
  snapshot: IGameSnapshot,
  diceRollNumber: number,
  diceValue: number
): string {
  return `${snapshot.actionSeq ?? 0}:${snapshot.currentSeatIndex}:${diceRollNumber}:${diceValue}`;
}

/**
 * Turn order (server seats): clockwise board colors via LudoColor.forPlayerCount.
 * 4p: RED(BL)→GREEN(TL)→YELLOW(TR)→BLUE(BR); 2p: RED↔YELLOW; 3p: RED→GREEN→YELLOW.
 */
export function nextSeatIndex(
  currentSeat: number,
  totalPlayers: number,
  seatColors: string[],
  finished: boolean[] = []
): number {
  const boardOrder =
    totalPlayers === 2
      ? ["RED", "YELLOW"]
      : totalPlayers === 3
      ? ["RED", "GREEN", "YELLOW"]
      : ["RED", "GREEN", "YELLOW", "BLUE"];
  const currentColor = seatColors[currentSeat];
  let startIdx = boardOrder.indexOf(currentColor);
  if (startIdx < 0) startIdx = 0;
  for (let step = 1; step <= boardOrder.length; step++) {
    const nextColor = boardOrder[(startIdx + step) % boardOrder.length];
    const seat = seatColors.findIndex((c) => c === nextColor);
    if (seat >= 0 && !finished[seat]) return seat;
  }
  return currentSeat;
}
