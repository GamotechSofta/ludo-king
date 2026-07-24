import React, { useCallback, useEffect, useRef, useState } from "react";
import type {
  IActionsTurn,
  IListTokens,
  IPlayer,
  ISelectTokenValues,
  IUser,
  TBoardColors,
  TDicevalues,
  TTotalPlayers,
  TTypeGame,
} from "../../interfaces";
import {
  EActionsBoardGame,
  EBoardColors,
  ENextStepGame,
  EPositionProfiles,
  ETypeGame,
  DICE_VALUE_GET_OUT_JAIL,
  EtypeTile,
  TOKEN_MOVEMENT_INTERVAL_VALUE,
} from "../../utils/constants";
import { playSound, preloadGameSounds, startBackgroundMusic, stopBackgroundMusic } from "../../utils/sounds";
import { PageWrapper } from "../wrapper";
import {
  Board,
  BoardWrapper,
  Debug,
  ProfileSection,
  Tokens,
} from "./components";
import {
  getInitialActionsTurnValue,
  getInitialDataPlayers,
  getInitialPositionTokens,
  getRandomValueDice,
} from "./helpers";
import {
  appendDiceRoll,
  applyTokenCell,
  buildMovePath,
  clearDiceAvailable,
  createTurnActions,
  decideAfterDiceRoll,
  decideAfterMove,
  finalizeRankings,
  getNextTurnIndex,
  getPossibleMoves,
  isGameOver,
  pickBotMove,
  resetDiceKeyCounter,
  resolveLanding,
} from "./rules";
import "./game-over.css";

import type { IResultEntry } from "../lobby/types";

interface GameProps {
  totalPlayers: TTotalPlayers;
  initialTurn: number;
  users: IUser[];
  typeGame?: TTypeGame;
  boardColor?: TBoardColors;
  debug?: boolean;
  onExit?: () => void;
  onGameOver?: (entries: IResultEntry[]) => void;
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const Game = ({
  totalPlayers = 2,
  initialTurn = 0,
  users = [],
  typeGame = ETypeGame.OFFLINE,
  boardColor = EBoardColors.RGYB,
  debug = false,
  onExit,
  onGameOver,
}: GameProps) => {
  const [players, setPlayers] = useState<IPlayer[]>(() =>
    getInitialDataPlayers(users, boardColor, totalPlayers)
  );

  const [listTokens, setListTokens] = useState<IListTokens[]>(() =>
    getInitialPositionTokens(
      boardColor,
      totalPlayers,
      getInitialDataPlayers(users, boardColor, totalPlayers)
    )
  );

  const [actionsTurn, setActionsTurn] = useState<IActionsTurn>(() =>
    getInitialActionsTurnValue(
      initialTurn,
      getInitialDataPlayers(users, boardColor, totalPlayers)
    )
  );

  const [currentTurn, setCurrentTurn] = useState(initialTurn);
  const [gameOver, setGameOver] = useState(false);
  const [isBusy, setIsBusy] = useState(false);

  /** Unique id of last resolved roll — reset every turn so P1↔P2 never collide. */
  const lastProcessedRollIdRef = useRef<string>("");
  const rollSeqRef = useRef(0);

  const playersRef = useRef(players);
  const listTokensRef = useRef(listTokens);
  const actionsTurnRef = useRef(actionsTurn);
  const currentTurnRef = useRef(currentTurn);
  const busyRef = useRef(false);
  const gameOverRef = useRef(false);

  useEffect(() => {
    playersRef.current = players;
  }, [players]);
  useEffect(() => {
    listTokensRef.current = listTokens;
  }, [listTokens]);
  useEffect(() => {
    actionsTurnRef.current = actionsTurn;
  }, [actionsTurn]);
  useEffect(() => {
    currentTurnRef.current = currentTurn;
  }, [currentTurn]);
  useEffect(() => {
    gameOverRef.current = gameOver;
  }, [gameOver]);

  useEffect(() => {
    resetDiceKeyCounter();
    preloadGameSounds();
    startBackgroundMusic();
    return () => {
      stopBackgroundMusic();
    };
  }, []);

  const passToNextPlayer = useCallback(
    (fromTurn: number, tokens: IListTokens[], plist: IPlayer[]) => {
      const nextTurn = getNextTurnIndex(fromTurn, plist);
      lastProcessedRollIdRef.current = "";
      return {
        type: ENextStepGame.NEXT_TURN as const,
        nextTurn,
        listTokens: clearDiceAvailable(tokens),
        actionsTurn: createTurnActions(nextTurn, plist),
      };
    },
    []
  );

  const applyDecision = useCallback(
    (
      decision: ReturnType<typeof decideAfterDiceRoll>,
      nextPlayers?: IPlayer[]
    ) => {
      if (nextPlayers) {
        let finalPlayers = nextPlayers;
        if (isGameOver(nextPlayers)) {
          finalPlayers = finalizeRankings(nextPlayers);
          setGameOver(true);
          gameOverRef.current = true;
          if (onGameOver) {
            const entries = finalPlayers
              .slice()
              .sort((a, b) => a.ranking - b.ranking)
              .map((p) => ({
                rank: p.ranking,
                name: p.name,
                color: p.color,
                isBot: !!p.isBot,
                isYou: !p.isBot && p.index === 0,
              }));
            stopBackgroundMusic();
            window.setTimeout(() => onGameOver(entries), 500);
          }
        }
        setPlayers(finalPlayers);
        playersRef.current = finalPlayers;
      }

      setListTokens(decision.listTokens);
      listTokensRef.current = decision.listTokens;
      setActionsTurn(decision.actionsTurn);
      actionsTurnRef.current = decision.actionsTurn;

      if (decision.type === ENextStepGame.NEXT_TURN) {
        setCurrentTurn(decision.nextTurn);
        currentTurnRef.current = decision.nextTurn;
        lastProcessedRollIdRef.current = "";
      }

      if (decision.type === ENextStepGame.ROLL_DICE_AGAIN) {
        lastProcessedRollIdRef.current = "";
      }
    },
    [onGameOver]
  );

  const runTokenMove = useCallback(
    async (tokenIndex: number, diceIndex: number) => {
      if (busyRef.current || gameOverRef.current) return;
      busyRef.current = true;
      setIsBusy(true);

      const turn = currentTurnRef.current;
      const tokens = listTokensRef.current;
      const actions = actionsTurnRef.current;
      const currentPlayers = playersRef.current;

      const dice = actions.diceList[diceIndex];
      if (!dice) {
        busyRef.current = false;
        setIsBusy(false);
        return;
      }

      const positionGame = tokens[turn].positionGame;
      const token = tokens[turn].tokens[tokenIndex];
      const path = buildMovePath(token, positionGame, dice.value);

      if (!path.length) {
        // Illegal / empty path → pass turn so game never sticks
        applyDecision(
          passToNextPlayer(turn, tokens, currentPlayers),
          currentPlayers
        );
        busyRef.current = false;
        setIsBusy(false);
        return;
      }

      setActionsTurn((prev) => {
        const next = {
          ...prev,
          isDisabledUI: true,
          timerActivated: false,
          disabledDice: true,
          actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
        };
        actionsTurnRef.current = next;
        return next;
      });

      let working = listTokensRef.current;

      for (let i = 0; i < path.length; i++) {
        const step = path[i];
        playSound("passingNext");
        working = working.map((group, pIdx) => {
          if (pIdx !== turn) return group;
          return {
            ...group,
            tokens: group.tokens.map((t, tIdx) => {
              if (tIdx !== tokenIndex) {
                return { ...t, diceAvailable: [], animated: false };
              }
              return applyTokenCell(
                t,
                positionGame,
                step.typeTile,
                step.positionTile,
                true
              );
            }),
          };
        });
        setListTokens(working);
        listTokensRef.current = working;
        await delay(TOKEN_MOVEMENT_INTERVAL_VALUE);

        if (step.typeTile === EtypeTile.END) {
          playSound("inside");
        }
      }

      working = working.map((group, pIdx) => {
        if (pIdx !== turn) return group;
        return {
          ...group,
          tokens: group.tokens.map((t, tIdx) =>
            tIdx === tokenIndex ? { ...t, isMoving: false } : t
          ),
        };
      });

      const landing = resolveLanding(working, currentPlayers, turn, tokenIndex);
      if (landing.captured) {
        playSound("capture");
      }
      const remainingDice = actions.diceList.filter((_, i) => i !== diceIndex);
      const usedSix = dice.value === DICE_VALUE_GET_OUT_JAIL;
      const playerFinished = !!landing.players[turn]?.finished;
      const bonusRoll = !playerFinished && (usedSix || landing.captured);
      const consecutiveSixes = usedSix ? actions.consecutiveSixes ?? 0 : 0;

      const decision = decideAfterMove(
        landing.listTokens,
        landing.players,
        turn,
        remainingDice,
        bonusRoll,
        consecutiveSixes
      );

      applyDecision(decision, landing.players);

      busyRef.current = false;
      setIsBusy(false);
    },
    [applyDecision, passToNextPlayer]
  );

  const handleSelectedToken = (selectTokenValues: ISelectTokenValues) => {
    if (busyRef.current || gameOverRef.current) return;
    const turn = currentTurnRef.current;
    const player = playersRef.current[turn];
    // Only the current non-bot seat may click tokens
    if (player?.isBot) return;
    const { diceIndex, tokenIndex } = selectTokenValues;
    void runTokenMove(tokenIndex, diceIndex);
  };

  const handleSelectDice = useCallback(
    (diceValue?: TDicevalues, _isActionSocket = false) => {
      if (busyRef.current || gameOverRef.current) return;
      const actions = actionsTurnRef.current;
      const turnPlayer = playersRef.current[currentTurnRef.current];

      const botAutoRoll =
        !!turnPlayer?.isBot &&
        diceValue === undefined &&
        actions.actionsBoardGame === EActionsBoardGame.ROLL_DICE;

      if (actions.disabledDice && diceValue === undefined && !botAutoRoll) {
        return;
      }
      if (
        actions.actionsBoardGame !== EActionsBoardGame.ROLL_DICE &&
        diceValue === undefined
      ) {
        return;
      }

      // Sync ref immediately so rollDone can read diceValue without waiting for React
      playSound("diceRolling");
      setActionsTurn((current) => {
        const next = getRandomValueDice(current, diceValue);
        rollSeqRef.current += 1;
        (next as IActionsTurn & { rollId?: string }).rollId = `${
          currentTurnRef.current
        }:${rollSeqRef.current}:${next.diceValue}`;
        actionsTurnRef.current = next;
        return next;
      });
    },
    []
  );

  const handleDoneDice = useCallback(
    (_isActionSocket = false) => {
      if (busyRef.current || gameOverRef.current) return;

      const actions = actionsTurnRef.current as IActionsTurn & {
        rollId?: string;
      };
      const diceValue = actions.diceValue;
      if (!diceValue) return;

      const rollId =
        actions.rollId ||
        `${currentTurnRef.current}:${actions.diceRollNumber}:${diceValue}`;

      if (lastProcessedRollIdRef.current === rollId) return;
      lastProcessedRollIdRef.current = rollId;

      const turnAtRoll = currentTurnRef.current;
      const withDice = appendDiceRoll(actions, diceValue);
      let decision = decideAfterDiceRoll(
        withDice,
        listTokensRef.current,
        playersRef.current,
        turnAtRoll
      );

      if (decision.type === ENextStepGame.MOVE_TOKENS_AGAIN) {
        const moves = getPossibleMoves(
          decision.listTokens,
          turnAtRoll,
          decision.actionsTurn.diceList
        );
        if (moves.length === 0) {
          decision = passToNextPlayer(
            turnAtRoll,
            decision.listTokens,
            playersRef.current
          );
        }
      }

      applyDecision(decision);

      if (decision.type === ENextStepGame.MOVE_TOKENS_AGAIN) {
        const seat = currentTurnRef.current;
        const human = !playersRef.current[seat]?.isBot;
        const moves = decision.listTokens[seat].tokens
          .flatMap((token, tokenIndex) =>
            token.diceAvailable.map((dice) => {
              const diceIndex = decision.actionsTurn.diceList.findIndex(
                (d) => d.key === dice.key
              );
              return { tokenIndex, diceIndex };
            })
          )
          .filter((m) => m.diceIndex >= 0);

        if (human && moves.length === 1) {
          void runTokenMove(moves[0].tokenIndex, moves[0].diceIndex);
        }
      }
    },
    [applyDecision, passToNextPlayer, runTokenMove]
  );

  const handleMuteChat = (playerIndex: number) => {
    if (playerIndex === 0) return;
    setPlayers((prev) =>
      prev.map((p, i) =>
        i === playerIndex ? { ...p, isMuted: !p.isMuted } : p
      )
    );
  };

  const handleTimer = (ends = false, playerIndex?: number) => {
    if (busyRef.current || gameOverRef.current) return;
    const turn = currentTurnRef.current;
    if (playerIndex !== undefined && playerIndex !== turn) return;

    const player = playersRef.current[turn];
    const actions = actionsTurnRef.current;

    if (!player?.isBot) {
      if (!ends) return;
      if (actions.actionsBoardGame === EActionsBoardGame.ROLL_DICE) {
        handleSelectDice();
        return;
      }
      if (actions.actionsBoardGame === EActionsBoardGame.SELECT_TOKEN) {
        const move = pickBotMove(
          listTokensRef.current,
          turn,
          actions.diceList
        );
        if (move) void runTokenMove(move.tokenIndex, move.diceIndex);
        else {
          applyDecision(
            passToNextPlayer(turn, listTokensRef.current, playersRef.current)
          );
        }
      }
      return;
    }

    // Bot: disabledDice is intentionally true — still allow auto-roll
    if (actions.actionsBoardGame === EActionsBoardGame.ROLL_DICE) {
      handleSelectDice();
      return;
    }

    if (actions.actionsBoardGame === EActionsBoardGame.SELECT_TOKEN) {
      const move = pickBotMove(listTokensRef.current, turn, actions.diceList);
      if (move) void runTokenMove(move.tokenIndex, move.diceIndex);
      else {
        applyDecision(
          passToNextPlayer(turn, listTokensRef.current, playersRef.current)
        );
      }
    }
  };

  // Bot: roll when it is their turn and waiting for a roll
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (!player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.ROLL_DICE) return;
    if (actionsTurn.diceValue !== 0) return;

    const timer = window.setTimeout(() => {
      if (currentTurnRef.current !== currentTurn) return;
      if (gameOverRef.current || busyRef.current) return;
      handleSelectDice();
    }, 650);

    return () => window.clearTimeout(timer);
  }, [
    currentTurn,
    actionsTurn.actionsBoardGame,
    actionsTurn.diceValue,
    actionsTurn.consecutiveSixes,
    gameOver,
    isBusy,
    players,
    handleSelectDice,
  ]);

  // Bot: pick a token (or pass) when moves are required
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (!player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.SELECT_TOKEN) return;

    const move = pickBotMove(listTokens, currentTurn, actionsTurn.diceList);
    if (!move) {
      applyDecision(passToNextPlayer(currentTurn, listTokens, players));
      return;
    }

    const timer = window.setTimeout(() => {
      if (currentTurnRef.current !== currentTurn) return;
      if (gameOverRef.current || busyRef.current) return;
      void runTokenMove(move.tokenIndex, move.diceIndex);
    }, 500);

    return () => window.clearTimeout(timer);
  }, [
    actionsTurn.actionsBoardGame,
    actionsTurn.diceList,
    applyDecision,
    currentTurn,
    gameOver,
    isBusy,
    listTokens,
    passToNextPlayer,
    players,
    runTokenMove,
  ]);

  // Human: auto-pass if SELECT_TOKEN with no legal moves
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.SELECT_TOKEN) return;

    const moves = getPossibleMoves(
      listTokens,
      currentTurn,
      actionsTurn.diceList
    );
    if (moves.length > 0) return;

    applyDecision(passToNextPlayer(currentTurn, listTokens, players));
  }, [
    actionsTurn.actionsBoardGame,
    actionsTurn.diceList,
    applyDecision,
    currentTurn,
    gameOver,
    isBusy,
    listTokens,
    passToNextPlayer,
    players,
  ]);

  const profileHandlers = {
    handleTimer,
    handleSelectDice,
    handleDoneDice,
    handleMuteChat,
  };

  const profileProps = { players, totalPlayers, currentTurn, actionsTurn };

  const ranking = [...players]
    .filter((p) => p.finished)
    .sort((a, b) => a.ranking - b.ranking);

  return (
    <PageWrapper>
      {onExit && (
        <button
          className="game-back-arrow"
          type="button"
          aria-label="Back"
          onClick={onExit}
        >
          <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden>
            <path
              d="M15.5 4.5L8 12l7.5 7.5"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.6"
              strokeLinecap="round"
              strokeLinejoin="round"
            />
          </svg>
        </button>
      )}
      <BoardWrapper>
        <ProfileSection
          basePosition={EPositionProfiles.TOP}
          profileHandlers={profileHandlers}
          {...profileProps}
        />
        <Board boardColor={boardColor}>
          {debug && <Debug.Tiles />}
          <Tokens
            debug={debug}
            isDisabledUI={actionsTurn.isDisabledUI || isBusy}
            listTokens={listTokens}
            diceList={actionsTurn.diceList}
            handleSelectedToken={handleSelectedToken}
          />
        </Board>
        <ProfileSection
          basePosition={EPositionProfiles.BOTTOM}
          profileHandlers={profileHandlers}
          {...profileProps}
        />
      </BoardWrapper>

      {gameOver && !onGameOver && (
        <div className="game-over-overlay">
          <div className="game-over-card">
            <h2>Game Over</h2>
            <ol>
              {ranking.map((p) => (
                <li key={p.id}>
                  <span>{p.ranking}.</span> {p.name}
                </li>
              ))}
            </ol>
            {onExit && (
              <button
                className="lobby-btn primary"
                type="button"
                onClick={onExit}
              >
                Back to Home
              </button>
            )}
          </div>
        </div>
      )}

      {debug && (
        <Debug.Tokens
          typeGame={typeGame}
          players={players}
          listTokens={listTokens}
          actionsTurn={actionsTurn}
          setListTokens={setListTokens}
          handleSelectDice={handleSelectDice}
        />
      )}
    </PageWrapper>
  );
};

export default React.memo(Game);
