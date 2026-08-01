import {
  buildHumanOnlineRollGate,
  isDuplicateOpponentRollFlash,
  isStableTurnPass,
  opponentRollFlashKey,
  priorOpponentRollVisible,
} from "./diceTurnLogic";
import type { IGameSnapshot } from "./types";

describe("buildHumanOnlineRollGate", () => {
  it("allows human roll when generic isBusy but not animating a move", () => {
    const snap: IGameSnapshot = {
      roomId: "r1",
      phase: "AWAITING_ROLL",
      currentSeatIndex: 0,
      currentColor: "YELLOW",
      diceValue: 0,
      diceList: [],
      tokenPositions: { YELLOW: [-1, -1, -1, -1] },
      legalTokenIndexes: [],
    };
    const gate = buildHumanOnlineRollGate(snap, 0, {
      isBusy: true,
      isAnimating: false,
      isRolling: true,
      isActionInFlight: false,
      disabledDice: true,
    });
    expect(gate.isBusy).toBe(false);
    expect(gate.isRolling).toBe(false);
    expect(gate.disabledDice).toBe(false);
  });

  it("blocks human dice while local bot/opponent playback is pending", () => {
    const snap: IGameSnapshot = {
      roomId: "r1",
      phase: "AWAITING_ROLL",
      currentSeatIndex: 0,
      currentColor: "YELLOW",
      diceValue: 0,
      diceList: [],
      tokenPositions: { YELLOW: [-1, -1, -1, -1] },
      legalTokenIndexes: [],
    };
    const gate = buildHumanOnlineRollGate(snap, 0, {
      isBusy: false,
      isAnimating: false,
      isRolling: false,
      isActionInFlight: false,
      disabledDice: false,
      localPlaybackPending: true,
    });
    expect(gate.disabledDice).toBe(true);
    expect(gate.isAnimating).toBe(true);
    expect(gate.isRolling).toBe(true);
  });
});

describe("opponent roll flash dedup", () => {
  it("treats ROLL and MOVE actionSeq as one visual roll", () => {
    const rollKey = opponentRollFlashKey(1, 4, 10, "ROLL");
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 11, "MOVE")).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 12, "MOVE")).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 10, "ROLL")).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 14, "MOVE")).toBe(false);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 6, 11, "MOVE")).toBe(false);
  });

  it("still flashes a bonus roll that repeats the value after a MOVE", () => {
    // Bot rolled 6 (flashed on the MOVE event), bonus roll is another 6
    const moveKey = opponentRollFlashKey(1, 6, 11, "MOVE");
    expect(isDuplicateOpponentRollFlash(moveKey, 1, 6, 12, "ROLL")).toBe(false);
    expect(isDuplicateOpponentRollFlash(moveKey, 1, 6, 13, "MOVE")).toBe(false);
  });

  it("detects prior AWAITING_MOVE roll before MOVE", () => {
    const prev: IGameSnapshot = {
      roomId: "r1",
      phase: "AWAITING_MOVE",
      currentSeatIndex: 1,
      currentColor: "YELLOW",
      diceValue: 5,
      diceList: [5],
      tokenPositions: { YELLOW: [-1, -1, -1, -1] },
      legalTokenIndexes: [],
    };
    expect(priorOpponentRollVisible(prev, 1, 5)).toBe(true);
    expect(priorOpponentRollVisible(prev, 1, 6)).toBe(false);
  });
});

describe("isStableTurnPass", () => {
  const base: IGameSnapshot = {
    roomId: "r1",
    phase: "AWAITING_ROLL",
    currentSeatIndex: 0,
    currentColor: "YELLOW",
    diceValue: 0,
    diceList: [],
    tokenPositions: { YELLOW: [-1, -1, -1, -1], RED: [-1, -1, -1, -1] },
    legalTokenIndexes: [],
  };

  it("shows a jail non-6 pass even when prev still points at the same seat", () => {
    // Human (seat 0) passed too; its own flash delayed prevSnap by one action
    const prev: IGameSnapshot = { ...base, currentSeatIndex: 0, actionSeq: 1 };
    const botPass: IGameSnapshot = {
      ...base,
      currentSeatIndex: 0,
      actionSeq: 2,
      lastActionType: "PASS",
      lastActionSeat: 1,
      lastActionDice: 3,
    };
    expect(isStableTurnPass(botPass, prev)).toBe(true);
  });

  it("ignores a pass whose roller still holds the turn", () => {
    const prev: IGameSnapshot = { ...base, currentSeatIndex: 1, actionSeq: 4 };
    const samSeat: IGameSnapshot = {
      ...base,
      currentSeatIndex: 1,
      actionSeq: 5,
      lastActionType: "PASS",
      lastActionSeat: 1,
      lastActionDice: 2,
    };
    expect(isStableTurnPass(samSeat, prev)).toBe(false);
  });

  it("ignores repeats of the same action and non-pass snapshots", () => {
    const prev: IGameSnapshot = { ...base, currentSeatIndex: 1, actionSeq: 7 };
    const same: IGameSnapshot = {
      ...base,
      actionSeq: 7,
      lastActionType: "PASS",
      lastActionSeat: 1,
      lastActionDice: 2,
    };
    expect(isStableTurnPass(same, prev)).toBe(false);

    const rolled: IGameSnapshot = {
      ...base,
      actionSeq: 8,
      phase: "AWAITING_MOVE",
      diceList: [4],
      lastActionType: "ROLL",
      lastActionSeat: 1,
      lastActionDice: 4,
    };
    expect(isStableTurnPass(rolled, prev)).toBe(false);
  });
});
