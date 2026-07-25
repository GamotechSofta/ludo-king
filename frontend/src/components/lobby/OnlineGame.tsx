import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useGameSocket } from "../../hooks/useGameSocket";
import type {
  IActionsTurn,
  IListTokens,
  IPlayer,
  ISelectTokenValues,
  TDicevalues,
} from "../../interfaces";
import {
  EActionsBoardGame,
  EPositionProfiles,
  EtypeTile,
  TOKEN_MOVEMENT_INTERVAL_VALUE,
} from "../../utils/constants";
import { playSound, preloadGameSounds, startBackgroundMusic, stopBackgroundMusic } from "../../utils/sounds";
import { PageWrapper } from "../wrapper";
import {
  Board,
  BoardWrapper,
  ProfileSection,
  Tokens,
} from "../game/components";
import { getRandomValueDice } from "../game/helpers";
import { applyTokenCell, buildMovePath, resolveLanding } from "../game/rules";
import type { IGameSnapshot, IGuestUser, IResultEntry } from "./types";
import Results from "./Results";
import {
  ONLINE_BOARD_COLOR,
  actionsTurnFromSnapshot,
  listTokensFromSnapshot,
  playersFromSnapshot,
  totalPlayersFromSnapshot,
} from "./onlineSnapshotBoard";
import "../game/game-over.css";

interface OnlineGameProps {
  guest: IGuestUser;
  roomId: string;
  initialSnapshot?: IGameSnapshot | null;
  onExit: () => void;
  onPlayAgain: () => void;
}

const delay = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const OnlineGame = ({
  guest,
  roomId,
  initialSnapshot = null,
  onExit,
  onPlayAgain,
}: OnlineGameProps) => {
  const { snapshot, connected, loadError, rollDice, moveToken } = useGameSocket(
    roomId,
    guest.id,
    initialSnapshot
  );
  const [showResults, setShowResults] = useState(false);
  const [listTokens, setListTokens] = useState<IListTokens[]>([]);
  const [players, setPlayers] = useState<IPlayer[]>([]);
  const [actionsTurn, setActionsTurn] = useState<IActionsTurn>(() =>
    actionsTurnFromSnapshot(
      {
        roomId: "",
        phase: "AWAITING_ROLL",
        currentSeatIndex: 0,
        currentColor: "RED",
        diceValue: 0,
        diceList: [],
        tokenPositions: {},
        legalTokenIndexes: [],
      },
      -1
    )
  );
  const [currentTurn, setCurrentTurn] = useState(0);
  const [isBusy, setIsBusy] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null);

  const mySeat = useMemo(() => {
    if (!snapshot) return -1;
    if (snapshot.userIds?.length) {
      const byId = snapshot.userIds.findIndex((id) => id === guest.id);
      if (byId >= 0) return byId;
    }
    if (snapshot.usernames?.length) {
      const byName = snapshot.usernames.findIndex(
        (n) => n === guest.username || n === guest.name
      );
      if (byName >= 0) return byName;
    }
    return 0;
  }, [snapshot, guest.id, guest.username, guest.name]);

  // Prefer showing the board as soon as we have token positions
  const boardReady = !!(
    snapshot?.tokenPositions &&
    Object.keys(snapshot.tokenPositions).length > 0
  );

  const lastDiceSigRef = useRef("");
  const animatingRef = useRef(false);
  const listTokensRef = useRef(listTokens);
  const actionsTurnRef = useRef(actionsTurn);

  useEffect(() => {
    listTokensRef.current = listTokens;
  }, [listTokens]);
  useEffect(() => {
    actionsTurnRef.current = actionsTurn;
  }, [actionsTurn]);

  useEffect(() => {
    preloadGameSounds();
    startBackgroundMusic();
    return () => {
      stopBackgroundMusic();
    };
  }, []);

  useEffect(() => {
    if (snapshot?.phase === "FINISHED") {
      const t = window.setTimeout(() => {
        stopBackgroundMusic();
        setShowResults(true);
      }, 600);
      return () => window.clearTimeout(t);
    }
    setShowResults(false);
  }, [snapshot?.phase]);

  useEffect(() => {
    if (
      snapshot?.turnSecondsRemaining == null ||
      snapshot.phase === "FINISHED"
    ) {
      setSecondsLeft(null);
      return;
    }
    setSecondsLeft(snapshot.turnSecondsRemaining);
    const id = window.setInterval(() => {
      setSecondsLeft((prev) =>
        prev == null ? null : Math.max(0, prev - 1)
      );
    }, 1000);
    return () => window.clearInterval(id);
  }, [
    snapshot?.turnStartedAt,
    snapshot?.turnSecondsRemaining,
    snapshot?.phase,
    snapshot?.currentSeatIndex,
  ]);

  const syncBoardFromSnapshot = useCallback(
    (snap: IGameSnapshot, keepDiceVisual = false) => {
      const nextPlayers = playersFromSnapshot(snap);
      const isMyTurn = snap.currentSeatIndex === mySeat;
      const canMove =
        isMyTurn && snap.phase === "AWAITING_MOVE" && !animatingRef.current;
      const nextTokens = listTokensFromSnapshot(snap, mySeat, canMove);
      setPlayers(nextPlayers);
      setListTokens(nextTokens);
      setCurrentTurn(snap.currentSeatIndex);
      setActionsTurn((prev) => {
        const next = actionsTurnFromSnapshot(snap, mySeat, prev);
        if (keepDiceVisual) {
          next.diceValue = prev.diceValue;
          next.diceRollNumber = prev.diceRollNumber;
        }
        return next;
      });
    },
    [mySeat]
  );

  // Apply snapshot → board (with dice roll animation when dice appear)
  useEffect(() => {
    if (!snapshot || mySeat < 0 || animatingRef.current) return;

    const diceSig = `${snapshot.currentSeatIndex}|${snapshot.phase}|${(
      snapshot.diceList || []
    ).join(",")}`;

    const diceAppeared =
      (snapshot.diceList?.length || 0) > 0 &&
      diceSig !== lastDiceSigRef.current &&
      snapshot.phase === "AWAITING_MOVE";

    if (diceAppeared) {
      lastDiceSigRef.current = diceSig;
      const value = snapshot.diceList[
        snapshot.diceList.length - 1
      ] as TDicevalues;
      // Own roll already played the sound on click
      if (snapshot.currentSeatIndex !== mySeat) {
        playSound("diceRolling");
      }
      setPlayers(playersFromSnapshot(snapshot));
      setCurrentTurn(snapshot.currentSeatIndex);
      setActionsTurn((prev) => {
        const base = actionsTurnFromSnapshot(snapshot, mySeat, prev);
        const rolled = getRandomValueDice(base, value);
        rolled.diceList = base.diceList;
        rolled.actionsBoardGame = EActionsBoardGame.ROLL_DICE;
        return rolled;
      });
      return;
    }

    lastDiceSigRef.current = diceSig;
    syncBoardFromSnapshot(snapshot);
  }, [snapshot, mySeat, syncBoardFromSnapshot]);

  const handleDoneDice = useCallback(() => {
    if (!snapshot) return;
    syncBoardFromSnapshot(snapshot, true);
    setActionsTurn((prev) => ({
      ...prev,
      ...actionsTurnFromSnapshot(snapshot, mySeat, prev),
      diceValue: prev.diceValue,
      diceRollNumber: prev.diceRollNumber,
    }));
  }, [snapshot, syncBoardFromSnapshot, mySeat]);

  const handleSelectDice = useCallback(
    (_diceValue?: TDicevalues) => {
      if (!snapshot || isBusy || animatingRef.current) return;
      if (snapshot.currentSeatIndex !== mySeat) return;
      if (snapshot.phase !== "AWAITING_ROLL") return;
      if (actionsTurnRef.current.disabledDice) return;
      playSound("diceRolling");
      setActionsTurn((prev) => ({
        ...prev,
        disabledDice: true,
        timerActivated: false,
      }));
      rollDice();
    },
    [snapshot, isBusy, mySeat, rollDice]
  );
  const runMoveAnimation = useCallback(
    async (
      snap: IGameSnapshot,
      tokenIndex: number,
      diceIndex: number
    ) => {
      const turn = snap.currentSeatIndex;
      const tokens = listTokensRef.current;
      if (!tokens[turn]) return;

      const diceValue = (snap.diceList || [])[diceIndex];
      if (!diceValue) return;

      const positionGame = tokens[turn].positionGame;
      const token = tokens[turn].tokens[tokenIndex];
      const path = buildMovePath(token, positionGame, diceValue as TDicevalues);
      if (!path.length) return;

      animatingRef.current = true;
      setIsBusy(true);
      setActionsTurn((prev) => ({
        ...prev,
        isDisabledUI: true,
        disabledDice: true,
        actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
      }));

      let working = tokens;
      for (const step of path) {
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

      const landing = resolveLanding(
        working,
        playersFromSnapshot(snap),
        turn,
        tokenIndex
      );
      if (landing.captured) {
        playSound("capture");
        setListTokens(landing.listTokens);
        listTokensRef.current = landing.listTokens;
      }

      animatingRef.current = false;
      setIsBusy(false);
    },
    []
  );

  const handleSelectedToken = useCallback(
    async (select: ISelectTokenValues) => {
      if (!snapshot || isBusy || animatingRef.current) return;
      if (snapshot.currentSeatIndex !== mySeat) return;
      if (snapshot.phase !== "AWAITING_MOVE") return;

      const { tokenIndex, diceIndex } = select;
      await runMoveAnimation(snapshot, tokenIndex, diceIndex);
      moveToken(tokenIndex, diceIndex);
    },
    [snapshot, isBusy, mySeat, runMoveAnimation, moveToken]
  );

  const resultEntries: IResultEntry[] = useMemo(
    () => buildResults(snapshot, guest.id),
    [snapshot, guest.id]
  );

  const totalPlayers = snapshot
    ? totalPlayersFromSnapshot(snapshot)
    : (4 as const);

  const profileHandlers = {
    handleTimer: () => undefined,
    handleSelectDice,
    handleDoneDice,
    handleMuteChat: (_playerIndex: number) => undefined,
  };

  const profileProps = {
    players,
    totalPlayers,
    currentTurn,
    actionsTurn,
  };

  if (showResults && snapshot?.phase === "FINISHED") {
    return (
      <Results
        title="Match Results"
        entries={resultEntries}
        onPlayAgain={onPlayAgain}
        onHome={onExit}
      />
    );
  }

  if (!boardReady) {
    return (
      <PageWrapper>
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
        <p className="lobby-sub" style={{ marginTop: 80, textAlign: "center" }}>
          {connected ? "Loading board…" : "Connecting…"}
        </p>
        {loadError && (
          <p
            className="lobby-footer-note"
            style={{ color: "#ffd0d0", textAlign: "center", marginTop: 12 }}
          >
            {loadError}
          </p>
        )}
        <p className="lobby-footer-note" style={{ textAlign: "center" }}>
          API: {process.env.REACT_APP_API_URL || "http://localhost:3000"}
        </p>
      </PageWrapper>
    );
  }

  return (
    <PageWrapper>
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
      {secondsLeft != null && (
        <div
          style={{
            position: "absolute",
            top: 12,
            right: 14,
            zIndex: 20,
            fontSize: "0.8rem",
            fontWeight: 700,
            opacity: 0.85,
          }}
        >
          {secondsLeft}s
        </div>
      )}
      <BoardWrapper>
        <ProfileSection
          basePosition={EPositionProfiles.TOP}
          profileHandlers={profileHandlers}
          {...profileProps}
        />
        <Board boardColor={ONLINE_BOARD_COLOR}>
          <Tokens
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
    </PageWrapper>
  );
};

function buildResults(
  snapshot: IGameSnapshot | null,
  myId: string
): IResultEntry[] {
  if (!snapshot?.usernames || !snapshot.standings) return [];
  const colors = Object.keys(snapshot.tokenPositions || {});
  return snapshot.usernames.map((name, seat) => ({
    rank: snapshot.standings![seat] || seat + 1,
    name,
    color: colors[seat],
    isBot: snapshot.isBot?.[seat],
    isYou: snapshot.userIds?.[seat] === myId,
  }));
}

export default React.memo(OnlineGame);
