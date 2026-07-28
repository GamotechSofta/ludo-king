import {
  boardColorForOffset,
  colorToViewCorner,
  cornerOffsetForSeat,
  profileIndexForServerSeat,
  resolveLocalSeatIndex,
} from "./onlineViewMapping";
import type { IGameSnapshot } from "./types";

function snap(
  seatColors: string[],
  userIds: string[],
  mySeat = 0
): IGameSnapshot {
  const tokenPositions: Record<string, number[]> = {};
  seatColors.forEach((c) => {
    tokenPositions[c] = [-1, -1, -1, -1];
  });
  return {
    roomId: "r",
    phase: "AWAITING_ROLL",
    currentSeatIndex: 0,
    currentColor: seatColors[0],
    diceValue: 0,
    diceList: [],
    tokenPositions,
    seatColors,
    legalTokenIndexes: [],
    userIds,
    usernames: userIds.map((_, i) => `P${i + 1}`),
    isBot: seatColors.map(() => false),
  };
}

describe("onlineViewMapping", () => {
  it("resolves seat by userId only (no seat-0 fallback)", () => {
    const s = snap(["RED", "YELLOW"], ["alice", "bob"]);
    expect(resolveLocalSeatIndex(s, "bob")).toBe(1);
    expect(resolveLocalSeatIndex(s, "unknown")).toBe(-1);
  });

  it("maps GREEN local to BL=GREEN TL=YELLOW TR=BLUE BR=RED", () => {
    const s = snap(["RED", "GREEN", "YELLOW", "BLUE"], ["a", "b", "c", "d"], 1);
    expect(cornerOffsetForSeat(s, 1)).toBe(1);
    expect(boardColorForOffset(1)).toBe("GYBR");
    expect(colorToViewCorner("GREEN", 1)).toBe(0);
    expect(colorToViewCorner("YELLOW", 1)).toBe(1);
    expect(colorToViewCorner("BLUE", 1)).toBe(2);
    expect(colorToViewCorner("RED", 1)).toBe(3);
  });

  it("2P compact profile: local YELLOW seat → profile 0, opponent profile 1", () => {
    const s = snap(["RED", "YELLOW"], ["a", "b"], 1);
    expect(profileIndexForServerSeat(1, s, 1)).toBe(0);
    expect(profileIndexForServerSeat(0, s, 1)).toBe(1);
  });
});
