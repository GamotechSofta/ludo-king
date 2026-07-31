import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
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
  TOKEN_STEP_PAUSE_MS,
} from "../../utils/constants";
import { isStarTile } from "../../config/ludoBoard";
import { playSound, preloadGameSounds, stopBackgroundMusic } from "../../utils/sounds";
import { runReturnToJailAnimations } from "../lobby/captureReturnAnim";
import { runCellByCellSteps, nextFrame } from "../lobby/onlineAnimate";
import {
  lostStatusLabel,
  partitionResults,
} from "../lobby/resultHelpers";
import "../lobby/styles.css";
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
  updateTokenAt,
  clearDiceAvailable,
  createTurnActions,
  decideAfterDiceRoll,
  decideAfterMove,
  finalizeRankings,
  findCaptureVictims,
  getNextTurnIndex,
  getPossibleMoves,
  isGameOver,
  pickBotMove,
  resetDiceKeyCounter,
  resolveLanding,
} from "./rules";
import { pickHumanAutoMoveOffline } from "./humanAutoMove";
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
  const lastAutoMoveKeyRef = useRef("");
  const autoMoveTimerRef = useRef<number | null>(null);

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
    playSound("matchStart");
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
            const entries = finalPlayers.map((p) => ({
              rank: p.ranking === 1 ? 1 : 0,
              name: p.name,
              color: p.color,
              isBot: !!p.isBot,
              isYou: !p.isBot && p.index === 0,
              won: p.ranking === 1,
              lost: p.ranking !== 1,
            }));
            stopBackgroundMusic();
            window.setTimeout(() => onGameOver(entries), 500);
          } else {
            const youWon = finalPlayers.some(
              (p) => !p.isBot && p.index === 0 && p.ranking === 1
            );
            stopBackgroundMusic();
            if (!youWon) {
              playSound("playerLost");
            }
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

      // Arm CSS transition on the mover at its current cell before hopping
      working = working.map((group, pIdx) => {
        if (pIdx !== turn) {
          return {
            ...group,
            tokens: group.tokens.map((t) =>
              t.animated || t.diceAvailable?.length || t.isMoving
                ? {
                    ...t,
                    diceAvailable: [],
                    animated: false,
                    isMoving: false,
                    canSelectToken: false,
                  }
                : t
            ),
          };
        }
        return {
          ...group,
          tokens: group.tokens.map((t, tIdx) =>
            tIdx === tokenIndex
              ? {
                  ...t,
                  diceAvailable: [],
                  animated: false,
                  canSelectToken: false,
                  isMoving: true,
                }
              : {
                  ...t,
                  diceAvailable: [],
                  animated: false,
                  canSelectToken: false,
                  isMoving: false,
                }
          ),
        };
      });
      setListTokens(working);
      listTokensRef.current = working;
      // Paint isMoving at start cell so CSS transition applies to every hop
      await nextFrame();
      await nextFrame();

      await runCellByCellSteps(
        path.length,
        TOKEN_MOVEMENT_INTERVAL_VALUE,
        (stepIndex) => {
          const step = path[stepIndex];
          const isFinalStep = stepIndex === path.length - 1;
          if (
            isFinalStep &&
            step.typeTile === EtypeTile.NORMAL &&
            isStarTile(step.positionTile)
          ) {
            playSound("star");
          } else {
            playSound("passingNext");
          }
          working = updateTokenAt(working, turn, tokenIndex, (t) =>
            applyTokenCell(
              t,
              positionGame,
              step.typeTile,
              step.positionTile,
              true
            )
          );
          setListTokens(working);
          listTokensRef.current = working;

          if (step.typeTile === EtypeTile.END) {
            playSound("inside");
          }
        },
        undefined,
        TOKEN_STEP_PAUSE_MS
      );

      working = updateTokenAt(working, turn, tokenIndex, (t) => ({
        ...t,
        isMoving: false,
      }));
      setListTokens(working);
      listTokensRef.current = working;

      const captives = findCaptureVictims(working, turn, tokenIndex);
      if (captives.length) {
        playSound("capture");
        working = await runReturnToJailAnimations(
          working,
          captives,
          (next) => {
            working = next;
            setListTokens(next);
            listTokensRef.current = next;
          }
        );
      }

      const landing = resolveLanding(working, currentPlayers, turn, tokenIndex);
      const remainingDice = actions.diceList.filter((_, i) => i !== diceIndex);
      const usedSix = dice.value === DICE_VALUE_GET_OUT_JAIL;
      const playerFinished = !!landing.players[turn]?.finished;
      // Product rules: bonus only after used 6 or capture (not home alone)
      const bonusRoll = !playerFinished && (usedSix || landing.captured);
      // decideAfterDiceRoll already increments the streak on roll; keep it for
      // bonus turns. If missing (edge path), treat a spent 6 as streak 1.
      const consecutiveSixes = usedSix
        ? Math.max(1, actions.consecutiveSixes ?? 0)
        : 0;

      const decision = decideAfterMove(
        landing.listTokens,
        landing.players,
        turn,
        remainingDice,
        bonusRoll,
        consecutiveSixes,
        actions.diceRollNumber ?? 0
      );

      applyDecision(decision, landing.players);

      busyRef.current = false;
      setIsBusy(false);
    },
    [applyDecision, passToNextPlayer]
  );

  const scheduleHumanAutoMove = useCallback(() => {
    if (autoMoveTimerRef.current != null) {
      window.clearTimeout(autoMoveTimerRef.current);
    }
    let attempts = 0;
    const attempt = () => {
      attempts += 1;
      if (gameOverRef.current) return;
      const turn = currentTurnRef.current;
      if (playersRef.current[turn]?.isBot) return;
      if (busyRef.current) {
        if (attempts < 30) {
          autoMoveTimerRef.current = window.setTimeout(attempt, 50);
        }
        return;
      }
      if (
        actionsTurnRef.current.actionsBoardGame !== EActionsBoardGame.SELECT_TOKEN
      ) {
        if (attempts < 30) {
          autoMoveTimerRef.current = window.setTimeout(attempt, 50);
        }
        return;
      }
      const autoMove = pickHumanAutoMoveOffline(
        listTokensRef.current,
        turn,
        actionsTurnRef.current.diceList
      );
      if (!autoMove) return;
      const key = `${turn}|${actionsTurnRef.current.diceList
        .map((d) => d.value)
        .join(",")}|${autoMove.tokenIndex}|${autoMove.diceIndex}`;
      if (lastAutoMoveKeyRef.current === key) return;
      lastAutoMoveKeyRef.current = key;
      void runTokenMove(autoMove.tokenIndex, autoMove.diceIndex);
    };
    autoMoveTimerRef.current = window.setTimeout(attempt, 0);
  }, [runTokenMove]);

  const handleSelectedToken = useCallback(
    (selectTokenValues: ISelectTokenValues) => {
      if (busyRef.current || gameOverRef.current) return;
      const turn = currentTurnRef.current;
      const player = playersRef.current[turn];
      // Only the current non-bot seat may click tokens
      if (player?.isBot) return;
      const { diceIndex, tokenIndex } = selectTokenValues;
      void runTokenMove(tokenIndex, diceIndex);
    },
    [runTokenMove]
  );

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
        if (!playersRef.current[seat]?.isBot) {
          lastAutoMoveKeyRef.current = "";
          scheduleHumanAutoMove();
        }
      }
    },
    [applyDecision, passToNextPlayer, scheduleHumanAutoMove]
  );

  const handleMuteChat = useCallback((playerIndex: number) => {
    if (playerIndex === 0) return;
    setPlayers((prev) =>
      prev.map((p, i) =>
        i === playerIndex ? { ...p, isMuted: !p.isMuted } : p
      )
    );
  }, []);

  const handleTimer = useCallback(
    (ends = false, playerIndex?: number) => {
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
    },
    [applyDecision, handleSelectDice, passToNextPlayer, runTokenMove]
  );

  // Bot: roll when it is their turn and waiting for a roll
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (!player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.ROLL_DICE) return;
    // A non-zero face means tumble is already in flight (or just finished waiting
    // for handleDoneDice). Bonus rolls reset diceValue to 0 first.
    if (actionsTurn.diceValue !== 0) return;

    const timer = window.setTimeout(() => {
      if (currentTurnRef.current !== currentTurn) return;
      if (gameOverRef.current || busyRef.current) return;
      if (actionsTurnRef.current.actionsBoardGame !== EActionsBoardGame.ROLL_DICE) {
        return;
      }
      if (actionsTurnRef.current.diceValue !== 0) return;
      handleSelectDice();
    }, 450);

    return () => window.clearTimeout(timer);
  }, [
    currentTurn,
    actionsTurn.actionsBoardGame,
    actionsTurn.diceValue,
    actionsTurn.diceRollNumber,
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
    }, 350);

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

  // Human: auto-move when only one pawn can move (or jail exit on 6)
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.SELECT_TOKEN) return;
    scheduleHumanAutoMove();
  }, [
    actionsTurn.actionsBoardGame,
    actionsTurn.diceList,
    currentTurn,
    gameOver,
    isBusy,
    listTokens,
    scheduleHumanAutoMove,
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

  const profileHandlers = useMemo(
    () => ({
      handleTimer,
      handleSelectDice,
      handleDoneDice,
      handleMuteChat,
    }),
    [handleTimer, handleSelectDice, handleDoneDice, handleMuteChat]
  );

  const profileProps = { players, totalPlayers, currentTurn, actionsTurn };

  const offlineResultEntries = players.map((p) => ({
    rank: p.ranking === 1 ? 1 : 0,
    name: p.name,
    color: p.color,
    isBot: !!p.isBot,
    isYou: !p.isBot && p.index === 0,
    won: p.ranking === 1,
    lost: p.ranking !== 1,
  }));
  const { winner: offlineWinner, lost: offlineLost } =
    partitionResults(offlineResultEntries);

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
          <div className="game-over-card match-results-panel">
            <h2>Match Results</h2>
            {offlineWinner && (
              <section className="match-results-section">
                <h3 className="match-results-section-title winner">🏆 Winner</h3>
                <div className="match-results-winner-card">
                  {offlineWinner.color && (
                    <div
                      className={`player-swatch ${offlineWinner.color.toLowerCase()}`}
                    />
                  )}
                  <div className="match-results-winner-meta">
                    <span className="match-results-name">{offlineWinner.name}</span>
                    <span className="match-results-rank">Rank 1</span>
                  </div>
                </div>
              </section>
            )}
            {offlineLost.length > 0 && (
              <section className="match-results-section">
                <h3 className="match-results-section-title lost">❌ Lost</h3>
                <ol className="match-results-lost-list">
                  {offlineLost.map((entry) => (
                    <li
                      className="match-results-lost-row"
                      key={`${entry.name}-${entry.color || "x"}`}
                    >
                      {entry.color && (
                        <div
                          className={`player-swatch ${entry.color.toLowerCase()}`}
                        />
                      )}
                      <span className="match-results-name">{entry.name}</span>
                      <span className="match-results-lost-tag">
                        {lostStatusLabel(entry)}
                      </span>
                    </li>
                  ))}
                </ol>
              </section>
            )}
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
