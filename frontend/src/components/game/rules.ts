import type {
  IActionsTurn,
  IDiceList,
  IListTokens,
  IPlayer,
  IToken,
  TDicevalues,
  TPositionGame,
  TtypeTile,
} from "../../interfaces";
import {
  DICE_VALUE_GET_OUT_JAIL,
  EActionsBoardGame,
  ENextStepGame,
  EtypeTile,
  MAXIMUM_DICE_PER_TURN,
} from "../../utils/constants";
import {
  POSITION_ELEMENTS_BOARD,
  POSITION_TILES,
  SAFE_AREAS,
  TOTAL_EXIT_TILES,
  TOTAL_TILES,
} from "../../utils/positions-board";
import { getInitialActionsTurnValue, validateDisabledDice } from "./helpers";

/** Immutable patch of a single pawn — avoids cloning the full board. */
export const updateTokenAt = (
  listTokens: IListTokens[],
  playerIndex: number,
  tokenIndex: number,
  updater: (token: IToken) => IToken
): IListTokens[] => {
  const group = listTokens[playerIndex];
  if (!group) return listTokens;
  const tokens = group.tokens.slice();
  tokens[tokenIndex] = updater(tokens[tokenIndex]);
  const next = listTokens.slice();
  next[playerIndex] = { ...group, tokens };
  return next;
};

export interface IMoveTarget {
  typeTile: TtypeTile;
  positionTile: number;
}

export interface IMoveResult {
  listTokens: IListTokens[];
  captured: boolean;
  reachedHome: boolean;
  players: IPlayer[];
}

let diceKeyCounter = 1;

const nextDiceKey = () => {
  diceKeyCounter += 1;
  return diceKeyCounter;
};

export const resetDiceKeyCounter = () => {
  diceKeyCounter = 1;
};

const getBoard = (positionGame: TPositionGame) =>
  POSITION_ELEMENTS_BOARD[positionGame];

export const getCoordinatesForToken = (
  typeTile: TtypeTile,
  positionGame: TPositionGame,
  positionTile: number
) => {
  if (typeTile === EtypeTile.JAIL) {
    return getBoard(positionGame).startPositions[positionTile].coordinate;
  }
  if (typeTile === EtypeTile.NORMAL) {
    return POSITION_TILES[positionTile].coordinate;
  }
  if (typeTile === EtypeTile.EXIT) {
    return getBoard(positionGame).exitTiles[positionTile].coordinate;
  }
  return getBoard(positionGame).finalPositions[positionTile].coordinate;
};

export const getRemainingDistance = (
  token: IToken,
  positionGame: TPositionGame
): number => {
  if (token.typeTile === EtypeTile.END) return 0;
  if (token.typeTile === EtypeTile.JAIL) return Number.POSITIVE_INFINITY;

  const { exitTileIndex } = getBoard(positionGame);

  if (token.typeTile === EtypeTile.EXIT) {
    return TOTAL_EXIT_TILES - 1 - token.positionTile;
  }

  const toExit =
    (exitTileIndex - token.positionTile + TOTAL_TILES) % TOTAL_TILES;
  return toExit + TOTAL_EXIT_TILES;
};

export const buildMovePath = (
  token: IToken,
  positionGame: TPositionGame,
  steps: number
): IMoveTarget[] => {
  const { exitTileIndex, startTileIndex } = getBoard(positionGame);
  const path: IMoveTarget[] = [];

  if (token.typeTile === EtypeTile.JAIL) {
    path.push({ typeTile: EtypeTile.NORMAL, positionTile: startTileIndex });
    return path;
  }

  let typeTile: TtypeTile = token.typeTile;
  let positionTile = token.positionTile;

  for (let i = 0; i < steps; i++) {
    if (typeTile === EtypeTile.NORMAL) {
      if (positionTile === exitTileIndex) {
        typeTile = EtypeTile.EXIT;
        positionTile = 0;
      } else {
        positionTile = (positionTile + 1) % TOTAL_TILES;
      }
    } else if (typeTile === EtypeTile.EXIT) {
      if (positionTile >= TOTAL_EXIT_TILES - 2) {
        typeTile = EtypeTile.END;
        positionTile = token.index;
      } else {
        positionTile += 1;
      }
    } else {
      break;
    }

    path.push({ typeTile, positionTile });
  }

  return path;
};

/**
 * Step-by-step path back to the yard after a capture.
 * Walks reverse along exit column (if any), then counterclockwise on the
 * shared path to the color start tile, then into JAIL.
 */
export const buildReturnToJailPath = (
  token: IToken,
  positionGame: TPositionGame
): IMoveTarget[] => {
  const { exitTileIndex, startTileIndex } = getBoard(positionGame);
  const path: IMoveTarget[] = [];

  if (token.typeTile === EtypeTile.JAIL || token.typeTile === EtypeTile.END) {
    return path;
  }

  let typeTile: TtypeTile = token.typeTile;
  let positionTile = token.positionTile;

  if (typeTile === EtypeTile.EXIT) {
    while (positionTile > 0) {
      positionTile -= 1;
      path.push({ typeTile: EtypeTile.EXIT, positionTile });
    }
    typeTile = EtypeTile.NORMAL;
    positionTile = exitTileIndex;
    path.push({ typeTile, positionTile });
  }

  if (typeTile === EtypeTile.NORMAL) {
    let guard = 0;
    while (positionTile !== startTileIndex && guard++ < TOTAL_TILES + 2) {
      positionTile = (positionTile - 1 + TOTAL_TILES) % TOTAL_TILES;
      path.push({ typeTile: EtypeTile.NORMAL, positionTile });
    }
  }

  path.push({ typeTile: EtypeTile.JAIL, positionTile: token.index });
  return path;
};

const getTokensOnCell = (
  listTokens: IListTokens[],
  typeTile: TtypeTile,
  positionTile: number
) => {
  const result: { playerIndex: number; tokenIndex: number; token: IToken }[] =
    [];

  listTokens.forEach((group, playerIndex) => {
    group.tokens.forEach((token, tokenIndex) => {
      if (
        token.typeTile === typeTile &&
        token.positionTile === positionTile &&
        token.typeTile !== EtypeTile.JAIL &&
        token.typeTile !== EtypeTile.END
      ) {
        result.push({ playerIndex, tokenIndex, token });
      }
    });
  });

  return result;
};

export const canTokenUseDice = (
  token: IToken,
  dice: TDicevalues,
  positionGame: TPositionGame,
  listTokens: IListTokens[],
  playerIndex: number
): boolean => {
  if (token.typeTile === EtypeTile.END) return false;

  if (token.typeTile === EtypeTile.JAIL) {
    if (dice !== DICE_VALUE_GET_OUT_JAIL) return false;
    // Start cell is always safe — stacking unrestricted there
    return true;
  }

  const remaining = getRemainingDistance(token, positionGame);
  if (dice > remaining) return false;

  const path = buildMovePath(token, positionGame, dice);
  if (!path.length || path.length !== dice) return false;

  const finalCell = path[path.length - 1];
  if (!finalCell) return false;

  if (finalCell.typeTile === EtypeTile.NORMAL) {
    // Safe cells: mixed occupancy OK; exempt from block / max-stack limits
    if (SAFE_AREAS.includes(finalCell.positionTile)) {
      return true;
    }
    // Spec default: opponent blocks may be passed through; cannot land on them
    if (
      hasOpponentBlock(
        listTokens,
        playerIndex,
        finalCell.typeTile,
        finalCell.positionTile
      )
    ) {
      return false;
    }
    if (
      countOwnOnCell(
        listTokens,
        playerIndex,
        finalCell.positionTile,
        token.index
      ) >= 2
    ) {
      return false;
    }
  }

  return true;
};

const countOwnOnCell = (
  listTokens: IListTokens[],
  playerIndex: number,
  positionTile: number,
  excludeTokenIndex: number
) => {
  let count = 0;
  listTokens[playerIndex].tokens.forEach((token, idx) => {
    if (idx === excludeTokenIndex) return;
    if (
      token.typeTile === EtypeTile.NORMAL &&
      token.positionTile === positionTile
    ) {
      count += 1;
    }
  });
  return count;
};

const hasOpponentBlock = (
  listTokens: IListTokens[],
  moverPlayerIndex: number,
  typeTile: TtypeTile,
  positionTile: number
) => {
  const onCell = getTokensOnCell(listTokens, typeTile, positionTile).filter(
    (t) => t.playerIndex !== moverPlayerIndex
  );
  const byPlayer: Record<number, number> = {};
  onCell.forEach(({ playerIndex }) => {
    byPlayer[playerIndex] = (byPlayer[playerIndex] || 0) + 1;
  });
  return Object.values(byPlayer).some((count) => count >= 2);
};

export const isSafeCell = (
  typeTile: TtypeTile,
  positionTile: number,
  tokensOnCell: { playerIndex: number; token: IToken }[]
) => {
  if (typeTile !== EtypeTile.NORMAL) return true;
  if (SAFE_AREAS.includes(positionTile)) return true;

  const byPlayer: Record<number, number> = {};
  tokensOnCell.forEach(({ playerIndex }) => {
    byPlayer[playerIndex] = (byPlayer[playerIndex] || 0) + 1;
  });
  return Object.values(byPlayer).some((count) => count >= 2);
};

export const recomputeStacking = (listTokens: IListTokens[]): IListTokens[] => {
  const groups: Record<string, { playerIndex: number; tokenIndex: number }[]> =
    {};

  listTokens.forEach((group, playerIndex) => {
    group.tokens.forEach((token, tokenIndex) => {
      if (
        token.typeTile === EtypeTile.JAIL ||
        token.typeTile === EtypeTile.END
      ) {
        return;
      }
      const key = `${token.typeTile}:${token.positionTile}`;
      if (!groups[key]) groups[key] = [];
      groups[key].push({ playerIndex, tokenIndex });
    });
  });

  const stackMeta = new Map<string, { total: number; position: number }>();
  Object.values(groups).forEach((items) => {
    items.forEach((item, idx) => {
      stackMeta.set(`${item.playerIndex}:${item.tokenIndex}`, {
        total: items.length,
        position: idx + 1,
      });
    });
  });

  return listTokens.map((group, playerIndex) => ({
    ...group,
    tokens: group.tokens.map((token, tokenIndex) => {
      if (
        token.typeTile === EtypeTile.JAIL ||
        token.typeTile === EtypeTile.END
      ) {
        if (token.totalTokens === 1 && token.position === 1) return token;
        return { ...token, totalTokens: 1, position: 1 };
      }
      const meta = stackMeta.get(`${playerIndex}:${tokenIndex}`);
      const total = meta?.total ?? 1;
      const position = meta?.position ?? 1;
      if (token.totalTokens === total && token.position === position) {
        return token;
      }
      return { ...token, totalTokens: total, position };
    }),
  }));
};

export const clearDiceAvailable = (listTokens: IListTokens[]): IListTokens[] =>
  listTokens.map((group) => ({
    ...group,
    tokens: group.tokens.map((token) => {
      if (
        !token.diceAvailable.length &&
        !token.enableTooltip &&
        !token.animated &&
        !token.isMoving &&
        !token.canSelectToken
      ) {
        return token;
      }
      return {
        ...token,
        diceAvailable: [],
        enableTooltip: false,
        animated: false,
        isMoving: false,
        canSelectToken: false,
      };
    }),
  }));

export const assignDiceToTokens = (
  listTokens: IListTokens[],
  playerIndex: number,
  diceList: IDiceList[],
  players: IPlayer[]
): IListTokens[] => {
  const cleared = clearDiceAvailable(listTokens);
  const positionGame = cleared[playerIndex].positionGame;

  return cleared.map((group, pIdx) => {
    if (pIdx !== playerIndex) return group;
    return {
      ...group,
      tokens: group.tokens.map((token) => {
        const diceAvailable = diceList.filter((dice) =>
          canTokenUseDice(token, dice.value, positionGame, cleared, playerIndex)
        );
        const player = players[playerIndex];
        const canSelectToken = player.isOnline
          ? playerIndex === 0
          : !player.isBot;
        return {
          ...token,
          diceAvailable,
          animated: diceAvailable.length > 0,
          canSelectToken,
        };
      }),
    };
  });
};

export const getPossibleMoves = (
  listTokens: IListTokens[],
  playerIndex: number,
  diceList: IDiceList[]
): { tokenIndex: number; diceIndex: number }[] => {
  const moves: { tokenIndex: number; diceIndex: number }[] = [];
  const positionGame = listTokens[playerIndex].positionGame;

  listTokens[playerIndex].tokens.forEach((token, tokenIndex) => {
    diceList.forEach((dice, diceIndex) => {
      if (
        canTokenUseDice(
          token,
          dice.value,
          positionGame,
          listTokens,
          playerIndex
        )
      ) {
        moves.push({ tokenIndex, diceIndex });
      }
    });
  });

  return moves;
};

export const areThreeSameDice = (diceList: IDiceList[]) => {
  if (diceList.length < MAXIMUM_DICE_PER_TURN) return false;
  const first = diceList[0].value;
  return diceList.every((d) => d.value === first);
};

export const applyTokenCell = (
  token: IToken,
  positionGame: TPositionGame,
  typeTile: TtypeTile,
  positionTile: number,
  isMoving = false
): IToken => ({
  ...token,
  typeTile,
  positionTile,
  coordinate: getCoordinatesForToken(typeTile, positionGame, positionTile),
  isMoving,
  diceAvailable: [],
  animated: isMoving,
  enableTooltip: false,
});

export const sendTokenToJail = (
  token: IToken,
  positionGame: TPositionGame
): IToken => {
  return applyTokenCell(token, positionGame, EtypeTile.JAIL, token.index, false);
};

/** Opponents that would be sent to jail if this mover lands (single pawn, unsafe). */
export const findCaptureVictims = (
  listTokens: IListTokens[],
  playerIndex: number,
  tokenIndex: number
): Array<{ playerIndex: number; tokenIndex: number }> => {
  const mover = listTokens[playerIndex]?.tokens[tokenIndex];
  if (!mover || mover.typeTile !== EtypeTile.NORMAL) return [];

  const onCell = getTokensOnCell(
    listTokens,
    mover.typeTile,
    mover.positionTile
  ).filter(
    (t) => !(t.playerIndex === playerIndex && t.tokenIndex === tokenIndex)
  );

  if (isSafeCell(mover.typeTile, mover.positionTile, onCell)) return [];

  const opponents = onCell.filter((t) => t.playerIndex !== playerIndex);
  const byPlayer: Record<number, typeof opponents> = {};
  opponents.forEach((o) => {
    if (!byPlayer[o.playerIndex]) byPlayer[o.playerIndex] = [];
    byPlayer[o.playerIndex].push(o);
  });

  const victims: Array<{ playerIndex: number; tokenIndex: number }> = [];
  Object.values(byPlayer).forEach((tokens) => {
    if (tokens.length === 1) {
      victims.push({
        playerIndex: tokens[0].playerIndex,
        tokenIndex: tokens[0].tokenIndex,
      });
    }
  });
  return victims;
};

export const resolveLanding = (
  listTokens: IListTokens[],
  players: IPlayer[],
  playerIndex: number,
  tokenIndex: number
): IMoveResult => {
  let copy = listTokens;
  let playersCopy = players;
  const mover = copy[playerIndex].tokens[tokenIndex];
  let captured = false;
  const reachedHome = mover.typeTile === EtypeTile.END;

  const victims = findCaptureVictims(copy, playerIndex, tokenIndex);
  victims.forEach((victim) => {
    const victimPos = copy[victim.playerIndex].positionGame;
    copy = updateTokenAt(copy, victim.playerIndex, victim.tokenIndex, (t) =>
      sendTokenToJail(t, victimPos)
    );
    captured = true;
  });

  if (reachedHome) {
    const allHome = copy[playerIndex].tokens.every(
      (t) => t.typeTile === EtypeTile.END
    );
    if (allHome && !playersCopy[playerIndex].finished) {
      playersCopy = playersCopy.map((p, i) =>
        i === playerIndex ? { ...p, finished: true, ranking: 1 } : p
      );
    }
  }

  copy = recomputeStacking(copy);

  return {
    listTokens: copy,
    captured,
    reachedHome,
    players: playersCopy,
  };
};

export const getNextTurnIndex = (
  currentTurn: number,
  players: IPlayer[]
): number => {
  const total = players.length;
  for (let i = 1; i <= total; i++) {
    const idx = (currentTurn + i) % total;
    if (!players[idx].finished) return idx;
  }
  return currentTurn;
};

export const createTurnActions = (
  turnIndex: number,
  players: IPlayer[]
): IActionsTurn => getInitialActionsTurnValue(turnIndex, players);

export const appendDiceRoll = (
  actionsTurn: IActionsTurn,
  diceValue: TDicevalues
): IActionsTurn => ({
  ...actionsTurn,
  diceList: [...actionsTurn.diceList, { key: nextDiceKey(), value: diceValue }],
  actionsBoardGame: EActionsBoardGame.DONE_DICE,
});

export type TTurnDecision =
  | {
      type: typeof ENextStepGame.ROLL_DICE_AGAIN;
      actionsTurn: IActionsTurn;
      listTokens: IListTokens[];
    }
  | {
      type: typeof ENextStepGame.MOVE_TOKENS_AGAIN;
      actionsTurn: IActionsTurn;
      listTokens: IListTokens[];
    }
  | {
      type: typeof ENextStepGame.NEXT_TURN;
      actionsTurn: IActionsTurn;
      listTokens: IListTokens[];
      nextTurn: number;
    };

export const decideAfterDiceRoll = (
  actionsTurn: IActionsTurn,
  listTokens: IListTokens[],
  players: IPlayer[],
  currentTurn: number
): TTurnDecision => {
  let tokens = clearDiceAvailable(listTokens);
  const last = actionsTurn.diceList[actionsTurn.diceList.length - 1];
  // Single-die flow: only the latest roll can be spent
  const diceList: IDiceList[] = last ? [last] : [];
  let consecutiveSixes = actionsTurn.consecutiveSixes ?? 0;

  if (last?.value === DICE_VALUE_GET_OUT_JAIL) {
    consecutiveSixes += 1;
  } else {
    consecutiveSixes = 0;
  }

  // Three consecutive sixes → turn cancelled
  if (consecutiveSixes >= MAXIMUM_DICE_PER_TURN) {
    const nextTurn = getNextTurnIndex(currentTurn, players);
    return {
      type: ENextStepGame.NEXT_TURN,
      nextTurn,
      listTokens: tokens,
      actionsTurn: createTurnActions(nextTurn, players),
    };
  }

  tokens = assignDiceToTokens(tokens, currentTurn, diceList, players);
  const moves = getPossibleMoves(tokens, currentTurn, diceList);

  const baseActions: IActionsTurn = {
    ...actionsTurn,
    diceList,
    consecutiveSixes,
    timerActivated: false,
  };

  if (moves.length === 0) {
    // Spec: no legal moves (including on a 6) → pass; 6 does NOT grant extra roll
    const nextTurn = getNextTurnIndex(currentTurn, players);
    return {
      type: ENextStepGame.NEXT_TURN,
      nextTurn,
      listTokens: clearDiceAvailable(tokens),
      actionsTurn: createTurnActions(nextTurn, players),
    };
  }

  // Even on 6: move first, then roll again after the move
  return {
    type: ENextStepGame.MOVE_TOKENS_AGAIN,
    actionsTurn: {
      ...baseActions,
      disabledDice: true,
      showDice: false,
      actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
      isDisabledUI: false,
    },
    listTokens: tokens,
  };
};

export const decideAfterMove = (
  listTokens: IListTokens[],
  players: IPlayer[],
  currentTurn: number,
  remainingDice: IDiceList[],
  bonusRoll: boolean,
  consecutiveSixes = 0,
  /** Keep tumble counter across bonus rolls so 6→6 does not reuse roll key `1:6`. */
  prevDiceRollNumber = 0
): TTurnDecision => {
  if (bonusRoll) {
    const actions = createTurnActions(currentTurn, players);
    // Keep streak only when the spent die was a 6 (caller passes current streak)
    actions.consecutiveSixes = consecutiveSixes;
    actions.diceList = [];
    actions.timerActivated = false;
    // Do not reset to 0 — next getRandomValueDice/applyServerDiceVisual must
    // bump past the previous key or bot/human bonus 6 skips tumble + handleDoneDice.
    actions.diceRollNumber = Math.max(0, prevDiceRollNumber);
    return {
      type: ENextStepGame.ROLL_DICE_AGAIN,
      actionsTurn: actions,
      listTokens: clearDiceAvailable(listTokens),
    };
  }

  if (remainingDice.length > 0) {
    const tokens = assignDiceToTokens(
      listTokens,
      currentTurn,
      remainingDice,
      players
    );
    const moves = getPossibleMoves(tokens, currentTurn, remainingDice);

    if (moves.length > 0) {
      return {
        type: ENextStepGame.MOVE_TOKENS_AGAIN,
        actionsTurn: {
          ...createTurnActions(currentTurn, players),
          diceList: remainingDice,
          consecutiveSixes,
          disabledDice: true,
          showDice: false,
          timerActivated: false,
          actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
        },
        listTokens: tokens,
      };
    }
  }

  const nextTurn = getNextTurnIndex(currentTurn, players);
  return {
    type: ENextStepGame.NEXT_TURN,
    nextTurn,
    listTokens: clearDiceAvailable(listTokens),
    actionsTurn: createTurnActions(nextTurn, players),
  };
};

/**
 * First time out of base: all pawns still in jail and rolled a 6 → auto-exit one pawn.
 */
export const shouldAutoExitJailOnFirstSix = (
  tokensInJail: boolean[],
  diceValues: number[]
): boolean =>
  tokensInJail.length > 0 &&
  tokensInJail.every(Boolean) &&
  diceValues.includes(DICE_VALUE_GET_OUT_JAIL);

export const pickBotMove = (
  listTokens: IListTokens[],
  playerIndex: number,
  diceList: IDiceList[]
): { tokenIndex: number; diceIndex: number } | null => {
  const moves = getPossibleMoves(listTokens, playerIndex, diceList);
  if (!moves.length) return null;

  const jailMoves = moves.filter(
    (m) =>
      listTokens[playerIndex].tokens[m.tokenIndex].typeTile === EtypeTile.JAIL
  );
  if (jailMoves.length) return jailMoves[0];

  const sorted = [...moves].sort(
    (a, b) => diceList[b.diceIndex].value - diceList[a.diceIndex].value
  );
  return sorted[0];
};

export const isGameOver = (players: IPlayer[]) =>
  players.some((p) => p.finished && p.ranking === 1);

export const finalizeRankings = (players: IPlayer[]): IPlayer[] =>
  players.map((p) =>
    p.ranking === 1 ? p : { ...p, finished: true, ranking: 0 }
  );
