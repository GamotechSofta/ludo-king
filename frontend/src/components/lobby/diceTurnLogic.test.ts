import {
  buildHumanOnlineRollGate,
  isDuplicateOpponentRollFlash,
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
});

describe("opponent roll flash dedup", () => {
  it("treats ROLL and MOVE actionSeq as one visual roll", () => {
    const rollKey = opponentRollFlashKey(1, 4, 10, "ROLL");
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 11, "MOVE")).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 10, "ROLL")).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 12, "MOVE")).toBe(false);
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
