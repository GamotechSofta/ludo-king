import {
  EColors,
  EPositionGame,
  EtypeTile,
  DICE_VALUE_GET_OUT_JAIL,
} from "../../utils/constants";
import type { IDiceList, IListTokens, IToken } from "../../interfaces";
import {
  pickHumanAutoMove,
  pickHumanAutoMoveFromSnapshot,
  pickHumanAutoMoveOffline,
} from "./humanAutoMove";
import type { IGameSnapshot } from "../lobby/types";

const baseToken = (overrides: Partial<IToken> = {}): IToken => ({
  color: EColors.YELLOW,
  index: 0,
  typeTile: EtypeTile.JAIL,
  positionTile: 0,
  coordinate: { x: 0, y: 0 },
  diceAvailable: [],
  totalTokens: 1,
  position: 1,
  enableTooltip: false,
  isMoving: false,
  animated: false,
  canSelectToken: true,
  ...overrides,
});

const offlineBoard = (tokenOverrides: Partial<IToken>[]) => {
  const listTokens: IListTokens[] = [
    {
      index: 0,
      positionGame: EPositionGame.BOTTOM_LEFT,
      tokens: tokenOverrides.map((t, i) => baseToken({ index: i, ...t })),
    },
  ];
  return listTokens;
};

describe("pickHumanAutoMove", () => {
  it("auto-moves when exactly one pawn has legal moves", () => {
    const move = pickHumanAutoMove(
      [{ tokenIndex: 0, diceIndex: 0 }],
      false,
      [5]
    );
    expect(move).toEqual({ tokenIndex: 0, diceIndex: 0 });
  });

  it("does not auto-move when two pawns can move", () => {
    const move = pickHumanAutoMove(
      [
        { tokenIndex: 0, diceIndex: 0 },
        { tokenIndex: 1, diceIndex: 0 },
      ],
      false,
      [4]
    );
    expect(move).toBeNull();
  });

  it("auto-releases from jail when all pawns are jailed and dice is 6", () => {
    const move = pickHumanAutoMove(
      [
        { tokenIndex: 0, diceIndex: 0 },
        { tokenIndex: 1, diceIndex: 0 },
      ],
      true,
      [DICE_VALUE_GET_OUT_JAIL]
    );
    expect(move).toEqual({ tokenIndex: 0, diceIndex: 0 });
  });

  it("picks highest dice when one pawn has multiple legal dice", () => {
    const move = pickHumanAutoMove(
      [
        { tokenIndex: 2, diceIndex: 0 },
        { tokenIndex: 2, diceIndex: 1 },
      ],
      false,
      [3, 6]
    );
    expect(move).toEqual({ tokenIndex: 2, diceIndex: 1 });
  });
});

describe("pickHumanAutoMoveOffline", () => {
  it("auto-moves the only active pawn", () => {
    const listTokens = offlineBoard([
      { typeTile: EtypeTile.NORMAL, positionTile: 26 },
      { typeTile: EtypeTile.JAIL, positionTile: 1 },
      { typeTile: EtypeTile.JAIL, positionTile: 2 },
      { typeTile: EtypeTile.JAIL, positionTile: 3 },
    ]);
    const diceList: IDiceList[] = [{ key: 1, value: 5 }];
    expect(pickHumanAutoMoveOffline(listTokens, 0, diceList)).toEqual({
      tokenIndex: 0,
      diceIndex: 0,
    });
  });
});

describe("pickHumanAutoMoveFromSnapshot", () => {
  it("returns null when two tokens can move online", () => {
    const snapshot: IGameSnapshot = {
      roomId: "r1",
      phase: "AWAITING_MOVE",
      currentSeatIndex: 0,
      currentColor: "YELLOW",
      diceValue: 4,
      diceList: [4],
      tokenPositions: {
        YELLOW: [10, 15, -1, -1],
      },
      seatColors: ["YELLOW"],
      legalTokenIndexes: [0, 1],
      legalMoves: [
        { tokenIndex: 0, diceIndex: 0 },
        { tokenIndex: 1, diceIndex: 0 },
      ],
      userIds: ["u1"],
    };
    expect(pickHumanAutoMoveFromSnapshot(snapshot, 0)).toBeNull();
  });

  it("filters jail tokens when dice is not 6", () => {
    const move = pickHumanAutoMoveFromSnapshot(
      {
        roomId: "r1",
        phase: "AWAITING_MOVE",
        currentSeatIndex: 0,
        currentColor: "YELLOW",
        diceValue: 5,
        diceList: [5],
        tokenPositions: { YELLOW: [26, -1, -1, -1] },
        seatColors: ["YELLOW"],
        legalTokenIndexes: [0, 1, 2, 3],
        userIds: ["u1"],
      },
      0
    );
    expect(move).toEqual({ tokenIndex: 0, diceIndex: 0 });
  });
});
