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
  TOKEN_MOVEMENT_INTERVAL_VALUE,
} from "../../utils/constants";
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
  decideAfterDiceRoll,
  decideAfterMove,
  finalizeRankings,
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
  const lastProcessedRollRef = useRef(0);

  const playersRef = useRef(players);
  const listTokensRef = useRef(listTokens);
  const actionsTurnRef = useRef(actionsTurn);
  const currentTurnRef = useRef(currentTurn);
  const busyRef = useRef(false);

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
    resetDiceKeyCounter();
  }, []);

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
      }
    },
    [onGameOver]
  );

  const runTokenMove = useCallback(
    async (tokenIndex: number, diceIndex: number) => {
      if (busyRef.current) return;
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
        busyRef.current = false;
        setIsBusy(false);
        return;
      }

      // Lock UI during movement
      setActionsTurn((prev) => ({
        ...prev,
        isDisabledUI: true,
        timerActivated: false,
        disabledDice: true,
        actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
      }));

      let working = listTokensRef.current;

      for (let i = 0; i < path.length; i++) {
        const step = path[i];
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
      }

      // Stop moving flag on final cell
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
      const remainingDice = actions.diceList.filter((_, i) => i !== diceIndex);
      const bonusRoll = landing.captured || landing.reachedHome;

      const decision = decideAfterMove(
        landing.listTokens,
        landing.players,
        turn,
        remainingDice,
        bonusRoll
      );

      applyDecision(decision, landing.players);

      busyRef.current = false;
      setIsBusy(false);
    },
    [applyDecision]
  );

  const handleSelectedToken = (selectTokenValues: ISelectTokenValues) => {
    if (busyRef.current || gameOver) return;
    const { diceIndex, tokenIndex } = selectTokenValues;
    void runTokenMove(tokenIndex, diceIndex);
  };

  const handleSelectDice = (
    diceValue?: TDicevalues,
    _isActionSocket = false
  ) => {
    if (busyRef.current || gameOver) return;
    const actions = actionsTurnRef.current;
    if (actions.disabledDice && diceValue === undefined) return;
    if (
      actions.actionsBoardGame !== EActionsBoardGame.ROLL_DICE &&
      diceValue === undefined
    ) {
      return;
    }

    setActionsTurn((current) => getRandomValueDice(current, diceValue));
  };

  const handleDoneDice = (_isActionSocket = false) => {
    if (busyRef.current || gameOver) return;

    const actions = actionsTurnRef.current;
    const diceValue = actions.diceValue;
    if (!diceValue) return;

    // Prevent duplicate rollDone callbacks for the same roll
    if (lastProcessedRollRef.current === actions.diceRollNumber) return;
    lastProcessedRollRef.current = actions.diceRollNumber;

    const withDice = appendDiceRoll(actions, diceValue);
    const decision = decideAfterDiceRoll(
      withDice,
      listTokensRef.current,
      playersRef.current,
      currentTurnRef.current
    );

    applyDecision(decision);

    if (decision.type === ENextStepGame.MOVE_TOKENS_AGAIN) {
      const human = !playersRef.current[currentTurnRef.current].isBot;
      const moves = decision.listTokens[currentTurnRef.current].tokens
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
  };

  const handleMuteChat = (playerIndex: number) => {
    if (playerIndex === 0) return;
    setPlayers((prev) =>
      prev.map((p, i) =>
        i === playerIndex ? { ...p, isMuted: !p.isMuted } : p
      )
    );
  };

  const handleTimer = (ends = false, playerIndex?: number) => {
    if (busyRef.current || gameOver) return;
    const turn = currentTurnRef.current;
    if (playerIndex !== undefined && playerIndex !== turn) return;

    const player = playersRef.current[turn];
    if (!player?.isBot) {
      // Human timeout: if still needs to roll, roll; if selecting, auto first move
      if (!ends) return;
      const actions = actionsTurnRef.current;
      if (
        actions.actionsBoardGame === EActionsBoardGame.ROLL_DICE &&
        !actions.disabledDice
      ) {
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
      }
      return;
    }

    // Bot acts on early nudge or timeout
    const actions = actionsTurnRef.current;
    if (
      actions.actionsBoardGame === EActionsBoardGame.ROLL_DICE &&
      !actions.disabledDice
    ) {
      handleSelectDice();
      return;
    }

    if (actions.actionsBoardGame === EActionsBoardGame.SELECT_TOKEN) {
      const move = pickBotMove(listTokensRef.current, turn, actions.diceList);
      if (move) void runTokenMove(move.tokenIndex, move.diceIndex);
    }
  };

  // Bot kickoff when turn changes to a bot waiting to roll
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (!player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.ROLL_DICE) return;
    if (actionsTurn.disabledDice) return;

    const timer = window.setTimeout(() => {
      handleSelectDice();
    }, 700);

    return () => window.clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    currentTurn,
    actionsTurn.actionsBoardGame,
    actionsTurn.disabledDice,
    gameOver,
    isBusy,
  ]);

  // Bot selects token when in SELECT_TOKEN phase
  useEffect(() => {
    if (gameOver || isBusy) return;
    const player = players[currentTurn];
    if (!player?.isBot) return;
    if (actionsTurn.actionsBoardGame !== EActionsBoardGame.SELECT_TOKEN) return;

    const move = pickBotMove(listTokens, currentTurn, actionsTurn.diceList);
    if (!move) return;

    const timer = window.setTimeout(() => {
      void runTokenMove(move.tokenIndex, move.diceIndex);
    }, 500);

    return () => window.clearTimeout(timer);
  }, [
    actionsTurn.actionsBoardGame,
    actionsTurn.diceList,
    currentTurn,
    gameOver,
    isBusy,
    listTokens,
    players,
    runTokenMove,
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
    <PageWrapper
      leftOption={
        onExit ? (
          <button className="game-exit-btn" type="button" onClick={onExit}>
            ← Home
          </button>
        ) : undefined
      }
    >
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
              <button className="lobby-btn primary" type="button" onClick={onExit}>
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
