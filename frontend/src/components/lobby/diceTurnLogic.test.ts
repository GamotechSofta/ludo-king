import {
  isDuplicateOpponentRollFlash,
  opponentRollFlashKey,
  priorOpponentRollVisible,
} from "./diceTurnLogic";
import type { IGameSnapshot } from "./types";

describe("opponent roll flash dedup", () => {
  it("treats ROLL and MOVE actionSeq as one visual roll", () => {
    const rollKey = opponentRollFlashKey(1, 4, 10);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 11)).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 10)).toBe(true);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 4, 12)).toBe(false);
    expect(isDuplicateOpponentRollFlash(rollKey, 1, 6, 11)).toBe(false);
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
