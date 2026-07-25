import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useGameSocket } from "../../hooks/useGameSocket";
import type {
  IActionsTurn,
  IListTokens,
  IPlayer,
  ISelectTokenValues,
  TDicevalues,
  TTotalPlayers,
} from "../../interfaces";
import {
  EActionsBoardGame,
  EPositionProfiles,
  EtypeTile,
  ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE,
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
import { applyTokenCell, buildMovePath, resolveLanding, shouldAutoExitJailOnFirstSix } from "../game/rules";
import type { IGameSnapshot, IGuestUser, IResultEntry } from "./types";
import Results from "./Results";
import {
  ONLINE_BOARD_COLOR,
  actionsTurnFromSnapshot,
  displayPlayerName,
  listTokensFromSnapshot,
  playersForView,
  playersFromSnapshot,
  profileTurnIndex,
  seatColorsFromSnapshot,
} from "./onlineSnapshotBoard";

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
  const prevSnapRef = useRef<IGameSnapshot | null>(null);
  const pendingDiceRef = useRef<{ seat: number; diceList: number[] } | null>(
    null
  );
  const suppressMoveAnimRef = useRef(false);
  const pendingSnapRef = useRef<IGameSnapshot | null>(null);
  const applySeqRef = useRef(0);

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
      const nextPlayers = playersForView(snap, mySeat);
      const isMyTurn = snap.currentSeatIndex === mySeat;
      const canMove =
        isMyTurn && snap.phase === "AWAITING_MOVE" && !animatingRef.current;
      const nextTokens = listTokensFromSnapshot(snap, mySeat, canMove);
      setPlayers(nextPlayers);
      setListTokens(nextTokens);
      listTokensRef.current = nextTokens;
      setCurrentTurn(profileTurnIndex(snap, snap.currentSeatIndex));
      setActionsTurn((prev) => {
        const next = actionsTurnFromSnapshot(snap, mySeat, prev);
        if (keepDiceVisual) {
          next.diceValue = prev.diceValue;
          next.diceRollNumber = prev.diceRollNumber;
        }
        return next;
      });
      prevSnapRef.current = snap;
    },
    [mySeat]
  );

  const runMoveAnimation = useCallback(
    async (
      snapForLanding: IGameSnapshot,
      seat: number,
      tokenIndex: number,
      diceValue: TDicevalues
    ) => {
      const tokens = listTokensRef.current;
      if (!tokens[seat]) return false;

      const positionGame = tokens[seat].positionGame;
      const token = tokens[seat].tokens[tokenIndex];
      if (!token) return false;
      const path = buildMovePath(token, positionGame, diceValue);
      if (!path.length) return false;

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
          if (pIdx !== seat) return group;
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
        await delay(ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE);
        if (step.typeTile === EtypeTile.END) {
          playSound("inside");
        }
      }

      const landing = resolveLanding(
        working,
        playersFromSnapshot(snapForLanding),
        seat,
        tokenIndex
      );
      if (landing.captured) {
        playSound("capture");
        setListTokens(landing.listTokens);
        listTokensRef.current = landing.listTokens;
      }

      animatingRef.current = false;
      setIsBusy(false);
      return true;
    },
    []
  );

  // Apply snapshot → board (dice + opponent/bot move animations)
  useEffect(() => {
    if (!snapshot || mySeat < 0) return;

    const apply = async (snap: IGameSnapshot) => {
      if (animatingRef.current) {
        pendingSnapRef.current = snap;
        return;
      }

      const seq = ++applySeqRef.current;
      const prev = prevSnapRef.current;
      const diceSig = `${snap.currentSeatIndex}|${snap.phase}|${(
        snap.diceList || []
      ).join(",")}`;

      const diceAppeared =
        (snap.diceList?.length || 0) > 0 &&
        diceSig !== lastDiceSigRef.current &&
        snap.phase === "AWAITING_MOVE";

      if (diceAppeared) {
        lastDiceSigRef.current = diceSig;
        pendingDiceRef.current = {
          seat: snap.currentSeatIndex,
          diceList: [...snap.diceList],
        };
        const value = snap.diceList[snap.diceList.length - 1] as TDicevalues;
        if (snap.currentSeatIndex !== mySeat) {
          playSound("diceRolling");
        }
        setPlayers(playersForView(snap, mySeat));
        setCurrentTurn(profileTurnIndex(snap, snap.currentSeatIndex));
        setActionsTurn((prevActions) => {
          const base = actionsTurnFromSnapshot(snap, mySeat, prevActions);
          const rolled = getRandomValueDice(base, value);
          rolled.diceList = base.diceList;
          rolled.actionsBoardGame = EActionsBoardGame.ROLL_DICE;
          return rolled;
        });
        // Keep token positions until the move arrives so we can animate the path
        if (!prev) {
          syncBoardFromSnapshot(snap, true);
        } else {
          prevSnapRef.current = {
            ...prev,
            phase: snap.phase,
            currentSeatIndex: snap.currentSeatIndex,
            diceList: snap.diceList,
            turnStartedAt: snap.turnStartedAt,
            turnSecondsRemaining: snap.turnSecondsRemaining,
          };
        }
        return;
      }

      // Prefer server lastAction (Redis/WS) for reliable opponent move animation
      let moved: { seat: number; tokenIndex: number } | null = null;
      let diceValue: TDicevalues | 0 = 0;

      if (
        !suppressMoveAnimRef.current &&
        snap.lastActionType === "MOVE" &&
        snap.lastActionSeat != null &&
        snap.lastActionTokenIndex != null &&
        snap.lastActionDice != null &&
        (!prev || (snap.actionSeq || 0) !== (prev.actionSeq || 0))
      ) {
        moved = {
          seat: snap.lastActionSeat,
          tokenIndex: snap.lastActionTokenIndex,
        };
        diceValue = snap.lastActionDice as TDicevalues;
      } else if (prev && !suppressMoveAnimRef.current) {
        moved = findMovedToken(
          prev.tokenPositions,
          snap.tokenPositions,
          seatColorsFromSnapshot(snap),
          pendingDiceRef.current?.seat ?? prev.currentSeatIndex
        );
        diceValue = (pendingDiceRef.current?.diceList[0] ||
          snap.lastActionDice ||
          0) as TDicevalues;
      }

      if (moved && diceValue >= 1 && diceValue <= 6) {
        lastDiceSigRef.current = diceSig;
        const ok = await runMoveAnimation(
          snap,
          moved.seat,
          moved.tokenIndex,
          diceValue as TDicevalues
        );
        if (seq !== applySeqRef.current) return;
        pendingDiceRef.current = null;
        if (ok) {
          syncBoardFromSnapshot(snap, true);
        } else {
          syncBoardFromSnapshot(snap);
        }
        const queued = pendingSnapRef.current;
        pendingSnapRef.current = null;
        if (queued) void apply(queued);
        return;
      }

      if (suppressMoveAnimRef.current) {
        suppressMoveAnimRef.current = false;
        pendingDiceRef.current = null;
      }

      lastDiceSigRef.current = diceSig;
      syncBoardFromSnapshot(snap);
      const queued = pendingSnapRef.current;
      pendingSnapRef.current = null;
      if (queued) void apply(queued);
    };

    void apply(snapshot);
  }, [snapshot, mySeat, syncBoardFromSnapshot, runMoveAnimation]);

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

  const handleSelectedToken = useCallback(
    async (select: ISelectTokenValues) => {
      if (!snapshot || isBusy || animatingRef.current) return;
      if (snapshot.currentSeatIndex !== mySeat) return;
      if (snapshot.phase !== "AWAITING_MOVE") return;

      const { tokenIndex, diceIndex } = select;
      const diceValue = (snapshot.diceList || [])[diceIndex] as TDicevalues;
      if (!diceValue) return;

      suppressMoveAnimRef.current = true;
      pendingDiceRef.current = {
        seat: mySeat,
        diceList: [...(snapshot.diceList || [])],
      };
      await runMoveAnimation(snapshot, mySeat, tokenIndex, diceValue);
      moveToken(tokenIndex, diceIndex);
    },
    [snapshot, isBusy, mySeat, runMoveAnimation, moveToken]
  );

  const handleDoneDice = useCallback(() => {
    if (!snapshot) return;
    // After dice spin, show selectable tokens for the current snap
    const canMove =
      snapshot.currentSeatIndex === mySeat &&
      snapshot.phase === "AWAITING_MOVE";
    const nextTokens = listTokensFromSnapshot(snapshot, mySeat, canMove);
    setListTokens(nextTokens);
    listTokensRef.current = nextTokens;
    setActionsTurn((prev) => ({
      ...prev,
      ...actionsTurnFromSnapshot(snapshot, mySeat, prev),
      diceValue: prev.diceValue,
      diceRollNumber: prev.diceRollNumber,
    }));

    // First 6 with all pawns still in jail → auto-exit one pawn (no click)
    if (!canMove || isBusy || animatingRef.current) return;
    const colors = seatColorsFromSnapshot(snapshot);
    const color = colors[mySeat];
    const positions = (color && snapshot.tokenPositions[color]) || [];
    const allInJail = positions.map((p) => p === -1);
    const diceValues = snapshot.diceList || [];
    if (!shouldAutoExitJailOnFirstSix(allInJail, diceValues)) return;

    const legal =
      snapshot.legalMoves?.length
        ? snapshot.legalMoves
        : (snapshot.legalTokenIndexes || []).map((tokenIndex) => ({
            tokenIndex,
            diceIndex: 0,
          }));
    const jailExit =
      legal.find((m) => positions[m.tokenIndex] === -1) || legal[0];
    if (!jailExit) return;
    void handleSelectedToken({
      tokenIndex: jailExit.tokenIndex,
      diceIndex: jailExit.diceIndex,
    });
  }, [snapshot, mySeat, isBusy, handleSelectedToken]);

  const resultEntries: IResultEntry[] = useMemo(
    () => buildResults(snapshot, guest.id),
    [snapshot, guest.id]
  );

  // Profiles sit by house color on the fixed RGYB board (no rotate / remap).
  const renderPlayers =
    players.length > 0
      ? players
      : snapshot && mySeat >= 0
      ? playersForView(snapshot, mySeat)
      : snapshot
      ? playersFromSnapshot(snapshot)
      : [];

  const totalPlayers: TTotalPlayers =
    renderPlayers.length === 2 || renderPlayers.length === 3
      ? renderPlayers.length
      : 4;

  const boardColor = ONLINE_BOARD_COLOR;

  const profileHandlers = {
    handleTimer: () => undefined,
    handleSelectDice,
    handleDoneDice,
    handleMuteChat: (_playerIndex: number) => undefined,
  };

  const profileProps = {
    players: renderPlayers,
    totalPlayers,
    currentTurn,
    actionsTurn: {
      ...actionsTurn,
      timerActivated:
        snapshot?.phase === "AWAITING_ROLL" ||
        snapshot?.phase === "AWAITING_MOVE",
      turnSecondsRemaining: secondsLeft,
      turnTimeoutSeconds: snapshot?.turnTimeoutSeconds ?? 20,
    },
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
        <div
          style={{
            width: "100%",
            display: "flex",
            justifyContent: "center",
          }}
        >
          <Board boardColor={boardColor}>
            <Tokens
              isDisabledUI={actionsTurn.isDisabledUI || isBusy}
              listTokens={listTokens}
              diceList={actionsTurn.diceList}
              handleSelectedToken={handleSelectedToken}
            />
          </Board>
        </div>
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
  const colors = seatColorsFromSnapshot(snapshot);
  const used: string[] = [];
  return snapshot.usernames.map((name, seat) => {
    const seatKey = `${snapshot.roomId || "room"}:${snapshot.userIds?.[seat] || seat}`;
    const display = displayPlayerName(name, seatKey, used);
    used.push(display);
    return {
      rank: snapshot.standings![seat] || seat + 1,
      name: display,
      color: colors[seat],
      isBot: snapshot.isBot?.[seat],
      isYou: snapshot.userIds?.[seat] === myId,
    };
  });
}

/** Prefer the mover seat; ignore captured tokens sent back to jail. */
function findMovedToken(
  prev: Record<string, number[]>,
  next: Record<string, number[]>,
  colors: string[],
  preferredSeat: number
): { seat: number; tokenIndex: number } | null {
  const seats =
    preferredSeat >= 0 && preferredSeat < colors.length
      ? [
          preferredSeat,
          ...colors.map((_, i) => i).filter((i) => i !== preferredSeat),
        ]
      : colors.map((_, i) => i);

  for (const seat of seats) {
    const color = colors[seat];
    const a = prev[color] || [];
    const b = next[color] || [];
    for (let i = 0; i < 4; i++) {
      const from = a[i] ?? -1;
      const to = b[i] ?? -1;
      if (from === to) continue;
      // Captured pawn → jail is a side-effect, not the mover
      if (to === -1 && from >= 0) continue;
      return { seat, tokenIndex: i };
    }
  }
  return null;
}

export default React.memo(OnlineGame);
