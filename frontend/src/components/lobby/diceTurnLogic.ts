import type { IGameSnapshot } from "./types";

/** Which server event a dice flash came from — a ROLL and its MOVE are one roll. */
export type RollFlashKind = "ROLL" | "MOVE" | "OTHER";

export function rollFlashKind(snap: IGameSnapshot): RollFlashKind {
  if (snap.lastActionType === "MOVE") return "MOVE";
  if (snap.lastActionType === "ROLL") return "ROLL";
  return "OTHER";
}

/** Dedup key for opponent/bot dice tumble (ROLL seq N and MOVE seq N+1 are one roll). */
export function opponentRollFlashKey(
  seat: number,
  value: number,
  actionSeq: number,
  kind: RollFlashKind = "OTHER"
): string {
  return `${seat}|${value}|${actionSeq}|${kind}`;
}

/**
 * A bonus roll (six / capture) can repeat the same value on the very next
 * actionSeq, so seq proximity alone must not swallow it — only the MOVE that
 * belongs to an already flashed ROLL counts as a duplicate.
 */
export function isDuplicateOpponentRollFlash(
  lastFlashKey: string,
  seat: number,
  value: number,
  actionSeq: number,
  kind: RollFlashKind = "OTHER"
): boolean {
  if (!lastFlashKey) return false;
  const [lastSeat, lastValue, lastSeq, lastKind] = lastFlashKey.split("|");
  if (Number(lastSeat) !== seat || Number(lastValue) !== value) return false;
  if (Number(lastSeq) === actionSeq) return true;
  return (
    kind === "MOVE" &&
    lastKind === "ROLL" &&
    actionSeq === Number(lastSeq) + 1
  );
}

/** True when AWAITING_MOVE already showed this seat's roll before a MOVE event. */
export function priorOpponentRollVisible(
  prev: IGameSnapshot | null | undefined,
  seat: number,
  diceValue: number
): boolean {
  if (!prev) return false;
  return (
    prev.currentSeatIndex === seat &&
    prev.phase === "AWAITING_MOVE" &&
    (prev.diceList?.length ?? 0) > 0 &&
    (prev.diceList || []).includes(diceValue)
  );
}

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
  if (snapshot.eliminated?.[gate.mySeat] || snapshot.finished?.[gate.mySeat]) {
    return false;
  }
  if (gate.isBusy || gate.isAnimating || gate.isRolling || gate.isActionInFlight) {
    return false;
  }
  if (gate.disabledDice) return false;
  if (!isCurrentPlayer(snapshot, gate.mySeat)) return false;
  if (!isAwaitingRoll(snapshot)) return false;
  if ((snapshot.diceList?.length ?? 0) > 0) return false;
  return true;
}

/**
 * Human roll gate: do not block the active player on generic isBusy or a stuck
 * rolling flag after the network request has finished.
 */
export function buildHumanOnlineRollGate(
  snapshot: IGameSnapshot | null,
  mySeat: number,
  state: {
    isBusy: boolean;
    isAnimating: boolean;
    isRolling: boolean;
    isActionInFlight: boolean;
    disabledDice: boolean;
  }
): OnlineRollGate {
  const humanRollTurn =
    !!snapshot &&
    mySeat >= 0 &&
    snapshot.currentSeatIndex === mySeat &&
    isAwaitingRoll(snapshot);

  return {
    mySeat,
    isBusy: humanRollTurn ? state.isAnimating : state.isBusy,
    isAnimating: state.isAnimating,
    isRolling: humanRollTurn
      ? state.isRolling && state.isActionInFlight
      : state.isRolling,
    isActionInFlight: state.isActionInFlight,
    disabledDice:
      humanRollTurn && !state.isActionInFlight ? false : state.disabledDice,
  };
}

/** Dice button disabled for every non-active player and during AWAITING_MOVE. */
export function onlineDiceDisabled(
  snapshot: IGameSnapshot,
  mySeat: number
): boolean {
  if (mySeat < 0 || isGameFinished(snapshot)) return true;
  if (snapshot.eliminated?.[mySeat] || snapshot.finished?.[mySeat]) return true;
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

/** Turn passed to next seat — idle die, no roll animation. */
export function isStableTurnPass(
  snap: IGameSnapshot,
  prev: IGameSnapshot | null | undefined
): boolean {
  if (!prev || (snap.actionSeq ?? 0) === (prev.actionSeq ?? 0)) return false;
  if (snap.phase !== "AWAITING_ROLL") return false;
  if ((snap.diceList?.length ?? 0) > 0) return false;
  if (prev.currentSeatIndex === snap.currentSeatIndex) return false;
  const passType = snap.lastActionType;
  return passType === "PASS" || passType === "TIMEOUT" || passType === "ELIMINATED";
}

/** Jail / no-move pass — rolled value shown, then turn hands off. */
export function isNoMovePassSnapshot(snap: IGameSnapshot): boolean {
  const passType = snap.lastActionType;
  return (
    (passType === "PASS" || passType === "TIMEOUT" || passType === "ELIMINATED") &&
    snap.phase === "AWAITING_ROLL" &&
    (snap.diceList?.length ?? 0) === 0
  );
}

/** Server seat changed while waiting to roll — clear stuck dice on prior profile. */
export function isTurnSeatHandoff(
  snap: IGameSnapshot,
  prev: IGameSnapshot | null | undefined
): boolean {
  if (!prev || (snap.actionSeq ?? 0) === (prev.actionSeq ?? 0)) return false;
  if (snap.phase !== "AWAITING_ROLL") return false;
  if ((snap.diceList?.length ?? 0) > 0) return false;
  if (prev.currentSeatIndex === snap.currentSeatIndex) return false;
  // PASS/TIMEOUT (jail non-6, void 6, timeout) — show roll flash first
  if (isNoMovePassSnapshot(snap)) return false;
  return true;
}

/** Client dice UI pinned to a seat that is no longer the active server seat. */
export function shouldClearStuckDice(
  snap: IGameSnapshot,
  diceOwnerSeat: number,
  diceFace: number
): boolean {
  if (diceOwnerSeat < 0) return false;
  if (snap.currentSeatIndex === diceOwnerSeat) return false;
  // Active seat has dice to move — die must sit on that profile (not a prior player)
  if (
    snap.phase === "AWAITING_MOVE" &&
    (snap.diceList?.length ?? 0) > 0
  ) {
    return true;
  }
  if ((snap.diceList?.length ?? 0) > 0) return false;
  // Let isStableTurnPass show roll flash for jail non-6 / timeout PASS
  if (isNoMovePassSnapshot(snap)) return false;
  if (snap.phase === "AWAITING_ROLL") return true;
  return diceFace > 0;
}

/** Resolve hop count for MOVE animation when lastActionDice is missing on the event. */
export function moveDiceValueFromSnapshot(
  snap: IGameSnapshot,
  fallbacks: number[] = []
): number {
  if (
    snap.lastActionDice != null &&
    snap.lastActionDice >= 1 &&
    snap.lastActionDice <= 6
  ) {
    return snap.lastActionDice;
  }
  for (const v of fallbacks) {
    if (v >= 1 && v <= 6) return v;
  }
  const fromList = snap.diceList?.find((d) => d >= 1 && d <= 6);
  return fromList ?? 0;
}

/** Server MOVE event that still needs client animation. */
export function isMoveSnapshot(
  snap: IGameSnapshot,
  lastAnimatedMoveSeq: number
): boolean {
  if (snap.lastActionType !== "MOVE") return false;
  if (snap.lastActionSeat == null || snap.lastActionTokenIndex == null) {
    return false;
  }
  return (snap.actionSeq || 0) !== lastAnimatedMoveSeq;
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
