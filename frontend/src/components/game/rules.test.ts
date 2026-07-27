import {
  EActionsBoardGame,
  EColors,
  ENextStepGame,
  EPositionGame,
  EtypeTile,
  DICE_VALUE_GET_OUT_JAIL,
} from "../../utils/constants";
import type {
  IActionsTurn,
  IListTokens,
  IPlayer,
  IToken,
} from "../../interfaces";
import {
  canTokenUseDice,
  decideAfterDiceRoll,
  decideAfterMove,
  getNextTurnIndex,
  getRemainingDistance,
  resolveLanding,
  applyTokenCell,
} from "./rules";

const baseToken = (overrides: Partial<IToken> = {}): IToken => ({
  color: EColors.RED,
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

const makePlayers = (n: number): IPlayer[] =>
  Array.from({ length: n }, (_, i) => ({
    id: `player-${i}`,
    index: i,
    name: `P${i}`,
    color: [EColors.RED, EColors.GREEN, EColors.YELLOW, EColors.BLUE][i],
    finished: false,
    isBot: i > 0,
    isOffline: false,
    isMuted: false,
    chatMessage: "",
    counterMessage: 0,
    ranking: 0,
  }));

const makeTokens = (players: IPlayer[]): IListTokens[] => {
  const seats = [
    EPositionGame.BOTTOM_LEFT,
    EPositionGame.TOP_LEFT,
    EPositionGame.TOP_RIGHT,
    EPositionGame.BOTTOM_RIGHT,
  ];
  return players.map((p, i) => ({
    index: i,
    positionGame: seats[i],
    tokens: [0, 1, 2, 3].map((t) =>
      baseToken({
        index: t,
        color: p.color,
        positionTile: t,
        typeTile: EtypeTile.JAIL,
      })
    ),
  }));
};

const actionsFor = (players: IPlayer[], turn = 0): IActionsTurn => ({
  timerActivated: false,
  disabledDice: false,
  showDice: true,
  diceValue: 0,
  diceList: [],
  diceRollNumber: 0,
  isDisabledUI: false,
  actionsBoardGame: EActionsBoardGame.ROLL_DICE,
  consecutiveSixes: 0,
});

describe("Ludo rules", () => {
  test("jail opens only on 6", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    const token = tokens[0].tokens[0];
    expect(
      canTokenUseDice(token, 5, tokens[0].positionGame, tokens, 0)
    ).toBe(false);
    expect(
      canTokenUseDice(token, 6, tokens[0].positionGame, tokens, 0)
    ).toBe(true);
  });

  test("non-6 with all in jail → next turn", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    const actions = {
      ...actionsFor(players),
      diceList: [{ key: 1, value: 4 as const }],
    };
    const decision = decideAfterDiceRoll(actions, tokens, players, 0);
    expect(decision.type).toBe(ENextStepGame.NEXT_TURN);
    if (decision.type === ENextStepGame.NEXT_TURN) {
      expect(decision.nextTurn).toBe(1);
    }
  });

  test("6 with no legal moves → pass (no extra roll)", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    // All tokens finished → no moves even on 6
    tokens[0].tokens = tokens[0].tokens.map((t, i) =>
      applyTokenCell(t, tokens[0].positionGame, EtypeTile.END, i)
    );
    const actions = {
      ...actionsFor(players),
      diceList: [{ key: 1, value: 6 as const }],
    };
    const decision = decideAfterDiceRoll(actions, tokens, players, 0);
    expect(decision.type).toBe(ENextStepGame.NEXT_TURN);
  });

  test("6 with jail token → must move (not roll again first)", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    const actions = {
      ...actionsFor(players),
      diceList: [{ key: 1, value: 6 as const }],
    };
    const decision = decideAfterDiceRoll(actions, tokens, players, 0);
    expect(decision.type).toBe(ENextStepGame.MOVE_TOKENS_AGAIN);
  });

  test("three consecutive sixes cancel turn", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    const actions = {
      ...actionsFor(players),
      consecutiveSixes: 2,
      diceList: [{ key: 1, value: 6 as const }],
    };
    const decision = decideAfterDiceRoll(actions, tokens, players, 0);
    expect(decision.type).toBe(ENextStepGame.NEXT_TURN);
  });

  test("6 grants extra roll after move", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    const decision = decideAfterMove(tokens, players, 0, [], true, 1);
    expect(decision.type).toBe(ENextStepGame.ROLL_DICE_AGAIN);
    expect(decision.actionsTurn.consecutiveSixes).toBe(1);
  });

  test("resolveLanding sets reachedHome on END", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    tokens[0].tokens[0] = applyTokenCell(
      tokens[0].tokens[0],
      tokens[0].positionGame,
      EtypeTile.END,
      0
    );
    const result = resolveLanding(tokens, players, 0, 0);
    expect(result.reachedHome).toBe(true);
    expect(result.players[0].finished).toBe(false);
  });

  test("turn order is clockwise and skips finished", () => {
    const players = makePlayers(4);
    players[1].finished = true;
    expect(getNextTurnIndex(0, players)).toBe(2);
    expect(getNextTurnIndex(2, players)).toBe(3);
    expect(getNextTurnIndex(3, players)).toBe(0);
  });

  test("exact dice required near home", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    // On last exit cell — only 1 step to home
    tokens[0].tokens[0] = applyTokenCell(
      tokens[0].tokens[0],
      tokens[0].positionGame,
      EtypeTile.EXIT,
      4
    );
    const remaining = getRemainingDistance(
      tokens[0].tokens[0],
      tokens[0].positionGame
    );
    expect(remaining).toBe(1);
    expect(
      canTokenUseDice(
        tokens[0].tokens[0],
        2,
        tokens[0].positionGame,
        tokens,
        0
      )
    ).toBe(false);
    expect(
      canTokenUseDice(
        tokens[0].tokens[0],
        1,
        tokens[0].positionGame,
        tokens,
        0
      )
    ).toBe(true);
  });

  test("safe start cell does not capture", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    // Red on green start (13) — SAFE_AREAS includes 13
    tokens[0].tokens[0] = applyTokenCell(
      tokens[0].tokens[0],
      tokens[0].positionGame,
      EtypeTile.NORMAL,
      13
    );
    tokens[1].tokens[0] = applyTokenCell(
      tokens[1].tokens[0],
      tokens[1].positionGame,
      EtypeTile.NORMAL,
      13
    );
    const result = resolveLanding(tokens, players, 0, 0);
    expect(result.captured).toBe(false);
    expect(result.listTokens[1].tokens[0].typeTile).toBe(EtypeTile.NORMAL);
  });

  test("unsafe cell captures single opponent", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    // Tile 1 is not in SAFE_AREAS
    tokens[0].tokens[0] = applyTokenCell(
      tokens[0].tokens[0],
      tokens[0].positionGame,
      EtypeTile.NORMAL,
      1
    );
    tokens[1].tokens[0] = applyTokenCell(
      tokens[1].tokens[0],
      tokens[1].positionGame,
      EtypeTile.NORMAL,
      1
    );
    const result = resolveLanding(tokens, players, 0, 0);
    expect(result.captured).toBe(true);
    expect(result.listTokens[1].tokens[0].typeTile).toBe(EtypeTile.JAIL);
  });

  test("turn order cycles 0 → 1 → 0", () => {
    const players = makePlayers(2);
    expect(getNextTurnIndex(0, players)).toBe(1);
    expect(getNextTurnIndex(1, players)).toBe(0);
  });

  test("turn order cycles with 4 players", () => {
    const players = makePlayers(4);
    expect(getNextTurnIndex(0, players)).toBe(1);
    expect(getNextTurnIndex(1, players)).toBe(2);
    expect(getNextTurnIndex(2, players)).toBe(3);
    expect(getNextTurnIndex(3, players)).toBe(0);
  });

  test("finished tokens cannot move", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    tokens[0].tokens[0] = applyTokenCell(
      tokens[0].tokens[0],
      tokens[0].positionGame,
      EtypeTile.END,
      0
    );
    expect(
      canTokenUseDice(
        tokens[0].tokens[0],
        DICE_VALUE_GET_OUT_JAIL,
        tokens[0].positionGame,
        tokens,
        0
      )
    ).toBe(false);
  });

  test("all four home marks player finished", () => {
    const players = makePlayers(2);
    const tokens = makeTokens(players);
    tokens[0].tokens = tokens[0].tokens.map((t, i) =>
      applyTokenCell(t, tokens[0].positionGame, EtypeTile.END, i)
    );
    const result = resolveLanding(tokens, players, 0, 0);
    expect(result.players[0].finished).toBe(true);
    expect(result.players[0].ranking).toBe(1);
  });
});
