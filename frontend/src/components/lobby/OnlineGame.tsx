import React, {
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { flushSync } from "react-dom";
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
  DICE_ROLL_ANIM_MS,
  ONLINE_TURN_PASS_DELAY_MS,
  ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE,
  MAX_PLAYER_CHANCES,
  ONLINE_ENTRY_AMOUNT,
  TOKEN_STEP_PAUSE_MS,
} from "../../utils/constants";
import { playSound, preloadGameSounds, ensureBackgroundMusic, stopBackgroundMusic } from "../../utils/sounds";
import { PageWrapper } from "../wrapper";
import {
  Board,
  BoardWrapper,
  ProfileSection,
  Tokens,
} from "../game/components";
import { applyServerDiceVisual } from "../game/helpers";
import {
  applyTokenCell,
  buildMovePath,
  clearDiceAvailable,
  findCaptureVictims,
} from "../game/rules";
import { pickHumanAutoMoveFromSnapshot } from "../game/humanAutoMove";
import type { IGameSnapshot, IGuestUser, IResultEntry } from "./types";
import Results from "./Results";
import LostSummaryPopup from "./LostSummaryPopup";
import LeaveMatchConfirmPopup from "./LeaveMatchConfirmPopup";
import { fetchWalletBalance, leaveRoom, ensureGameSnapshot } from "../../api/ludoApi";
import {
  runReturnToJailAnimations,
  type CaptureVictim,
} from "./captureReturnAnim";
import {
  runCellByCellSteps,
  nextFrame,
  rafDelay,
  type AnimCancel,
} from "./onlineAnimate";
import { onlinePerf } from "../../utils/onlinePerf";
import {
  actionsTurnFromSnapshot,
  boardColorForSnapshot,
  clearDisplayNameCache,
  displayPlayerName,
  listTokensFromSnapshot,
  playersForView,
  profileTurnIndex,
  seatColorsFromSnapshot,
  seatDisplayKey,
  snapshotTokenPositionsEqual,
  viewTileFromServerPos,
  totalPlayersFromSnapshot,
  resolveLocalSeatIndex,
  perspectiveKey,
} from "./onlineSnapshotBoard";
import {
  buildRollDedupKey,
  canRequestOnlineRoll,
  isMoveSnapshot,
  isStableTurnPass,
  isTurnSeatHandoff,
  isNoMovePassSnapshot,
  moveDiceValueFromSnapshot,
  onlineDiceDisabled,
  shouldClearStuckDice,
  shouldEnableTokenSelection,
} from "./diceTurnLogic";
import { isHumanOnlineMatch } from "./humanMatch";

/** flushSync outside React commit/effects — avoids lifecycle flushSync warning. */
function flushSyncAfterRender(update: () => void): Promise<void> {
  return new Promise((resolve) => {
    queueMicrotask(() => {
      flushSync(update);
      resolve();
    });
  });
}

interface OnlineGameProps {
  guest: IGuestUser;
  roomId: string;
  initialSnapshot?: IGameSnapshot | null;
  walletBalance?: number | null;
  onExit: () => void;
  onPlayAgain: () => void;
}

const OnlineGame = ({
  guest,
  roomId,
  initialSnapshot = null,
  walletBalance = null,
  onExit,
  onPlayAgain,
}: OnlineGameProps) => {
  const { snapshot, connected, loadError, rollDice, moveToken, isActionInFlight, setSnapshot } =
    useGameSocket(roomId, guest.id, initialSnapshot);
  const [showResults, setShowResults] = useState(false);
  const [showLostSummary, setShowLostSummary] = useState(false);
  const [showLeaveConfirm, setShowLeaveConfirm] = useState(false);
  const [voluntaryExit, setVoluntaryExit] = useState(false);
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
  /** House color that should show the die (authoritative seat → color). */
  const [turnColor, setTurnColor] = useState<string | null>(null);
  const [isBusy, setIsBusy] = useState(false);
  const [diceRolling, setDiceRolling] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null);
  const [liveBalance, setLiveBalance] = useState<number | null>(
    walletBalance ?? null
  );
  const exitingRef = useRef(false);
  const leftRoomRef = useRef(false);
  const lostSummaryShownRef = useRef(false);

  useEffect(() => {
    setLiveBalance(walletBalance ?? null);
  }, [walletBalance]);

  const refreshBalance = useCallback(async () => {
    try {
      const res = await fetchWalletBalance(guest.id);
      if (res.walletEnabled !== false && typeof res.balance === "number") {
        setLiveBalance(res.balance);
      }
    } catch {
      // keep last known balance
    }
  }, [guest.id]);

  useEffect(() => {
    void refreshBalance();
  }, [refreshBalance]);

  useEffect(() => {
    if (leftRoomRef.current || exitingRef.current) return;
    if (snapshot?.phase === "FINISHED") {
      void refreshBalance();
      // Settle room as COMPLETED while results show — next queue gets a fresh match
      void leaveRoom(roomId, guest.id).catch(() => undefined);
    }
  }, [snapshot?.phase, refreshBalance, roomId, guest.id]);

  const mySeat = useMemo(
    () => resolveLocalSeatIndex(snapshot, guest.id, guest.name, guest.username),
    [snapshot, guest.id, guest.username, guest.name]
  );

  const myEliminated = useMemo(() => {
    if (mySeat < 0 || !snapshot) return false;
    return (
      !!snapshot.eliminated?.[mySeat] ||
      (snapshot.consecutiveTimeouts?.[mySeat] ?? 0) >= MAX_PLAYER_CHANCES
    );
  }, [snapshot, mySeat]);

  useEffect(() => {
    if (!myEliminated || lostSummaryShownRef.current) return;
    lostSummaryShownRef.current = true;
    setShowLostSummary(true);
    stopBackgroundMusic();
    void refreshBalance();
  }, [myEliminated, refreshBalance]);

  // Prefer showing the board as soon as we have token positions
  const boardReady = !!(
    snapshot?.tokenPositions &&
    Object.keys(snapshot.tokenPositions).length > 0 &&
    mySeat >= 0
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
  const pendingSnapRef = useRef<IGameSnapshot[]>([]);
  const applySeqRef = useRef(0);
  /** actionSeq of MOVE we already finished animating — prevents double-play */
  const lastAnimatedMoveSeqRef = useRef(0);
  const rollingRef = useRef(false);
  const lastProcessedRollIdRef = useRef("");
  /** Server seat that owns the dice UI until their turn fully ends. */
  const diceOwnerSeatRef = useRef(-1);
  /** PASS roll-flash: keep die on roller until this time. */
  const passFlashUntilRef = useRef(0);
  const lastAutoMoveKeyRef = useRef("");
  const autoMoveTimerRef = useRef<number | null>(null);
  const diceRollPendingRef = useRef(false);
  const diceRollWaitersRef = useRef<Array<() => void>>([]);
  const diceRollFallbackRef = useRef<number | null>(null);
  const rollRecoveryTimerRef = useRef<number | null>(null);
  /** Dedup bot/opponent roll flash (bots often skip AWAITING_MOVE). */
  const lastDiceFlashKeyRef = useRef("");
  /** Dedup dice sound per server roll event key. */
  const lastDiceSoundKeyRef = useRef("");
  const scheduleHumanAutoMoveRef = useRef<(snap?: IGameSnapshot) => void>(() => {});
  const isBusyRef = useRef(false);
  const lockedBoardColorRef = useRef<ReturnType<
    typeof boardColorForSnapshot
  > | null>(null);
  const perspectiveKeyRef = useRef("");
  const snapshotRef = useRef<IGameSnapshot | null>(snapshot);
  const prevConnectedRef = useRef(false);
  const lockSeqRef = useRef<{ seq: number; at: number }>({ seq: 0, at: Date.now() });

  useEffect(() => {
    snapshotRef.current = snapshot;
  }, [snapshot]);

  useEffect(() => {
    if (!snapshot) return;
    const seq = snapshot.actionSeq || 0;
    if (lockSeqRef.current.seq !== seq) {
      lockSeqRef.current = { seq, at: Date.now() };
    }
  }, [snapshot?.actionSeq, snapshot]);

  useEffect(() => {
    lockedBoardColorRef.current = null;
    perspectiveKeyRef.current = "";
    diceOwnerSeatRef.current = -1;
    passFlashUntilRef.current = 0;
    lastAutoMoveKeyRef.current = "";
    if (autoMoveTimerRef.current != null) {
      window.clearTimeout(autoMoveTimerRef.current);
    }
    if (diceRollFallbackRef.current != null) {
      window.clearTimeout(diceRollFallbackRef.current);
      diceRollFallbackRef.current = null;
    }
    diceRollPendingRef.current = false;
    setDiceRolling(false);
    diceRollWaitersRef.current = [];
    lastDiceFlashKeyRef.current = "";
    lastDiceSoundKeyRef.current = "";
    setTurnColor(null);
    clearDisplayNameCache(roomId);
  }, [roomId]);

  const finishDiceRollAnimation = useCallback(() => {
    diceRollPendingRef.current = false;
    setDiceRolling(false);
    if (diceRollFallbackRef.current != null) {
      window.clearTimeout(diceRollFallbackRef.current);
      diceRollFallbackRef.current = null;
    }
    const waiters = diceRollWaitersRef.current.splice(0);
    waiters.forEach((resolve) => resolve());
  }, []);

  const waitForDiceRollAnimation = useCallback(async () => {
    if (!diceRollPendingRef.current) return;
    await Promise.race([
      new Promise<void>((resolve) => {
        diceRollWaitersRef.current.push(resolve);
      }),
      new Promise<void>((resolve) => {
        window.setTimeout(resolve, DICE_ROLL_ANIM_MS + 250);
      }),
    ]);
    finishDiceRollAnimation();
  }, [finishDiceRollAnimation]);

  const beginDiceRollAnimation = useCallback(() => {
    diceRollPendingRef.current = true;
    setDiceRolling(true);
    if (diceRollFallbackRef.current != null) {
      window.clearTimeout(diceRollFallbackRef.current);
    }
    diceRollFallbackRef.current = window.setTimeout(() => {
      finishDiceRollAnimation();
    }, DICE_ROLL_ANIM_MS + 120);
  }, [finishDiceRollAnimation]);

  const playDiceRollingOnce = useCallback((soundKey: string) => {
    if (!soundKey) return;
    if (lastDiceSoundKeyRef.current === soundKey) return;
    lastDiceSoundKeyRef.current = soundKey;
    playSound("diceRolling");
  }, []);

  /** Map dice-owner server seat → profile slot + house color for the die UI. */
  const applyDiceOwnerTurn = useCallback(
    (snap: IGameSnapshot, ownerSeat?: number) => {
      if (ownerSeat != null && ownerSeat >= 0) {
        diceOwnerSeatRef.current = ownerSeat;
      }
      const seat =
        diceOwnerSeatRef.current >= 0
          ? diceOwnerSeatRef.current
          : snap.currentSeatIndex;
      const colors = seatColorsFromSnapshot(snap);
      const color =
        seat >= 0 && seat < colors.length ? colors[seat] : null;
      setTurnColor(color);
      setCurrentTurn(profileTurnIndex(snap, seat, mySeat));
    },
    [mySeat]
  );

  /**
   * Show tumble on a seat's profile (bot/opponent often skip AWAITING_MOVE).
   * Returns true if a new flash was started.
   */
  const flashDiceOnSeat = useCallback(
    (
      snap: IGameSnapshot,
      seat: number,
      value: TDicevalues,
      flashKey: string
    ): boolean => {
      if (value < 1 || value > 6) return false;
      if (lastDiceFlashKeyRef.current === flashKey) return false;
      lastDiceFlashKeyRef.current = flashKey;
      beginDiceRollAnimation();
      pendingDiceRef.current = {
        seat,
        diceList: [value],
      };
      applyDiceOwnerTurn(snap, seat);
      setActionsTurn((prevActions) => {
        const base = actionsTurnFromSnapshot(
          {
            ...snap,
            currentSeatIndex: seat,
            phase: "AWAITING_MOVE",
            diceList: [value],
          },
          mySeat,
          prevActions
        );
        const rolled = applyServerDiceVisual(base, value);
        rolled.diceList = base.diceList;
        rolled.actionsBoardGame = EActionsBoardGame.SELECT_TOKEN;
        return rolled;
      });
      return true;
    },
    [applyDiceOwnerTurn, beginDiceRollAnimation, mySeat]
  );

  /**
   * Hard sync: die profile always follows server current seat (or MOVE mover while hopping).
   * Runs in layout so paint cannot show the previous player's die.
   */
  useLayoutEffect(() => {
    if (!snapshot || mySeat < 0) return;
    // Keep bot/opponent die on their profile while tumble plays
    if (diceRollPendingRef.current && diceOwnerSeatRef.current >= 0) {
      applyDiceOwnerTurn(snapshot, diceOwnerSeatRef.current);
      return;
    }
    if (
      animatingRef.current &&
      snapshot.lastActionType === "MOVE" &&
      snapshot.lastActionSeat != null
    ) {
      applyDiceOwnerTurn(snapshot, snapshot.lastActionSeat);
      return;
    }
    // PASS/TIMEOUT: apply() shows roll on roller then hands off — never jump early
    if (
      isNoMovePassSnapshot(snapshot) &&
      snapshot.lastActionSeat != null &&
      snapshot.lastActionSeat !== snapshot.currentSeatIndex
    ) {
      if (performance.now() < passFlashUntilRef.current) {
        applyDiceOwnerTurn(snapshot, snapshot.lastActionSeat);
      }
      return;
    }
    // Turn handoff — idle die before painting on next profile (no spin on pass)
    const prev = prevSnapRef.current;
    const seatHandoff =
      prev != null &&
      prev.currentSeatIndex !== snapshot.currentSeatIndex &&
      snapshot.phase === "AWAITING_ROLL" &&
      (snapshot.diceList?.length ?? 0) === 0;
    if (seatHandoff) {
      setActionsTurn((prevActions) =>
        actionsTurnFromSnapshot(
          snapshot,
          mySeat,
          prevActions,
          prev.currentSeatIndex
        )
      );
      diceOwnerSeatRef.current = snapshot.currentSeatIndex;
      applyDiceOwnerTurn(snapshot, snapshot.currentSeatIndex);
      return;
    }
    applyDiceOwnerTurn(snapshot, snapshot.currentSeatIndex);
  }, [
    snapshot,
    mySeat,
    applyDiceOwnerTurn,
    snapshot?.actionSeq,
    snapshot?.currentSeatIndex,
    snapshot?.phase,
    snapshot?.lastActionType,
    snapshot?.lastActionSeat,
    snapshot?.diceList,
  ]);

  useEffect(() => {
    listTokensRef.current = listTokens;
  }, [listTokens]);
  useEffect(() => {
    actionsTurnRef.current = actionsTurn;
  }, [actionsTurn]);
  useEffect(() => {
    isBusyRef.current = isBusy;
  }, [isBusy]);

  useEffect(() => {
    preloadGameSounds();
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

  const totalPlayers: TTotalPlayers = useMemo(
    () => (snapshot ? totalPlayersFromSnapshot(snapshot) : 4),
    [snapshot]
  );

  /** Rebuild local perspective when seat/color/count is known or changes (reconnect). */
  useEffect(() => {
    if (exitingRef.current || !snapshot || mySeat < 0) return;
    const key = perspectiveKey(roomId, snapshot, mySeat);
    if (perspectiveKeyRef.current === key) return;
    perspectiveKeyRef.current = key;
    lockedBoardColorRef.current = boardColorForSnapshot(snapshot, mySeat);
    setPlayers(playersForView(snapshot, mySeat, roomId));
    setListTokens(listTokensFromSnapshot(snapshot, mySeat, false));
    diceOwnerSeatRef.current = snapshot.currentSeatIndex;
    applyDiceOwnerTurn(snapshot, snapshot.currentSeatIndex);
  }, [snapshot, mySeat, roomId, applyDiceOwnerTurn]);

  const syncBoardFromSnapshot = useCallback(
    (
      snap: IGameSnapshot,
      keepDiceVisual = false,
      /** After hop finished — allowed to paint MOVE destination tokens */
      forceTokens = false
    ) => {
      // Hard lock: while hopping, never rebuild pawns from server (destination leak)
      if (animatingRef.current && !forceTokens) {
        return;
      }
      rollingRef.current = false;
      if (rollRecoveryTimerRef.current != null) {
        window.clearTimeout(rollRecoveryTimerRef.current);
        rollRecoveryTimerRef.current = null;
      }

      const isMyRollTurn =
        snap.currentSeatIndex === mySeat && snap.phase === "AWAITING_ROLL";
      if (isMyRollTurn && !animatingRef.current) {
        setIsBusy(false);
        isBusyRef.current = false;
      }

      // MOVE snapshots carry destination tokenPositions — never paint those
      // until forceTokens (animation finished). Chrome/UI-only update instead.
      const isMoveSnap = snap.lastActionType === "MOVE";
      if (isMoveSnap && !forceTokens) {
        const prev = prevSnapRef.current;
        setPlayers(playersForView(snap, mySeat, roomId));
        // Keep dice on the mover until hop finishes — do not jump to next seat yet
        const moverSeat =
          snap.lastActionSeat != null
            ? snap.lastActionSeat
            : diceOwnerSeatRef.current >= 0
            ? diceOwnerSeatRef.current
            : snap.currentSeatIndex;
        applyDiceOwnerTurn(snap, moverSeat);
        setActionsTurn((prevActions) =>
          actionsTurnFromSnapshot(
            snap,
            mySeat,
            prevActions,
            prev?.currentSeatIndex
          )
        );
        // Keep prior tokenPositions as display source of truth
        prevSnapRef.current = {
          ...snap,
          tokenPositions:
            prev?.tokenPositions && Object.keys(prev.tokenPositions).length
              ? prev.tokenPositions
              : snap.tokenPositions,
        };
        return;
      }

      const prev = prevSnapRef.current;
      const prevSeat = prev?.currentSeatIndex;
      const turnHandoff =
        !!prev &&
        snap.phase === "AWAITING_ROLL" &&
        prev.currentSeatIndex !== snap.currentSeatIndex &&
        snapshotTokenPositionsEqual(prev, snap);

      if (turnHandoff) {
        lastProcessedRollIdRef.current = "";
        lastDiceSigRef.current = `${snap.currentSeatIndex}|${snap.phase}|`;
        setPlayers(playersForView(snap, mySeat, roomId));
        setActionsTurn((prevActions) =>
          actionsTurnFromSnapshot(snap, mySeat, prevActions, prevSeat)
        );
        diceOwnerSeatRef.current = snap.currentSeatIndex;
        applyDiceOwnerTurn(snap, snap.currentSeatIndex);
        setListTokens((tok) => {
          const cleared = clearDiceAvailable(tok);
          listTokensRef.current = cleared;
          return cleared;
        });
        prevSnapRef.current = snap;
        return;
      }

      const nextPlayers = playersForView(snap, mySeat, roomId);
      const isMyTurn = snap.currentSeatIndex === mySeat;
      const canMove =
        isMyTurn &&
        snap.phase === "AWAITING_MOVE" &&
        !animatingRef.current &&
        !diceRollPendingRef.current;
      const nextTokens = listTokensFromSnapshot(snap, mySeat, canMove);
      setPlayers(nextPlayers);
      setListTokens(nextTokens);
      listTokensRef.current = nextTokens;
      const turnPassed =
        prevSeat != null && prevSeat !== snap.currentSeatIndex;
      if (
        !animatingRef.current &&
        (turnPassed ||
          diceOwnerSeatRef.current !== snap.currentSeatIndex)
      ) {
        diceOwnerSeatRef.current = snap.currentSeatIndex;
      }
      const idleTurnPass =
        turnPassed &&
        snap.phase === "AWAITING_ROLL" &&
        (snap.diceList?.length ?? 0) === 0;
      if (idleTurnPass) {
        setActionsTurn((prevActions) =>
          actionsTurnFromSnapshot(snap, mySeat, prevActions, prevSeat)
        );
        applyDiceOwnerTurn(snap, snap.currentSeatIndex);
      } else {
        applyDiceOwnerTurn(snap, snap.currentSeatIndex);
        setActionsTurn((prevActions) => {
          const next = actionsTurnFromSnapshot(
            snap,
            mySeat,
            prevActions,
            prevSeat
          );
          // Never carry a rolled face onto the next player — that retriggers spin.
          if (
            keepDiceVisual &&
            !turnPassed &&
            (prevActions.diceValue || prevActions.diceRollNumber)
          ) {
            next.diceValue = prevActions.diceValue;
            next.diceRollNumber = prevActions.diceRollNumber;
          }
          return next;
        });
      }
      prevSnapRef.current = snap;
    },
    [mySeat, applyDiceOwnerTurn, roomId]
  );

  /** Human online (2P/4P): after reconnect, drop stale locks and paint authoritative board. */
  useEffect(() => {
    if (!snapshot || mySeat < 0) {
      prevConnectedRef.current = connected;
      return;
    }
    if (
      connected &&
      !prevConnectedRef.current &&
      isHumanOnlineMatch(snapshot)
    ) {
      animatingRef.current = false;
      suppressMoveAnimRef.current = false;
      rollingRef.current = false;
      pendingSnapRef.current = [];
      pendingDiceRef.current = null;
      passFlashUntilRef.current = 0;
      lastAutoMoveKeyRef.current = "";
      lastProcessedRollIdRef.current = "";
      lastAnimatedMoveSeqRef.current = snapshot.actionSeq || 0;
      lastDiceSigRef.current = `${snapshot.actionSeq || 0}|${
        snapshot.currentSeatIndex
      }|${snapshot.phase}|${(snapshot.diceList || []).join(",")}`;
      diceOwnerSeatRef.current = snapshot.currentSeatIndex;
      finishDiceRollAnimation();
      setIsBusy(false);
      syncBoardFromSnapshot(snapshot, false, true);
    }
    prevConnectedRef.current = connected;
  }, [
    connected,
    snapshot,
    mySeat,
    syncBoardFromSnapshot,
    finishDiceRollAnimation,
  ]);

  /** Die profile must match the seat that can actually play (fixes wrong-profile dice). */
  useEffect(() => {
    if (!snapshot || mySeat < 0) return;
    if (animatingRef.current) return;
    if (isNoMovePassSnapshot(snapshot)) return;
    const owner = diceOwnerSeatRef.current;
    const diceFace = (actionsTurnRef.current.diceValue || 0) as number;
    if (shouldClearStuckDice(snapshot, owner, diceFace)) {
      rollingRef.current = false;
      lastProcessedRollIdRef.current = "";
      const prevOwner = owner;
      const idleHandoff =
        snapshot.phase === "AWAITING_ROLL" &&
        (snapshot.diceList?.length ?? 0) === 0;
      diceOwnerSeatRef.current = snapshot.currentSeatIndex;
      if (idleHandoff) {
        setActionsTurn((prev) =>
          actionsTurnFromSnapshot(
            snapshot,
            mySeat,
            prev,
            prevOwner >= 0 ? prevOwner : undefined
          )
        );
        applyDiceOwnerTurn(snapshot, snapshot.currentSeatIndex);
      } else {
        applyDiceOwnerTurn(snapshot, snapshot.currentSeatIndex);
        setActionsTurn((prev) => ({
          ...actionsTurnFromSnapshot(snapshot, mySeat, prev),
          diceValue: prev.diceValue,
          diceRollNumber: prev.diceRollNumber,
        }));
      }
    }
  }, [snapshot, mySeat, applyDiceOwnerTurn]);

  const animCancelRef = useRef<AnimCancel>({ cancelled: false });

  /** Put one pawn on a server cell instantly (no CSS slide). */
  const placePawnInstant = (
    working: IListTokens[],
    seat: number,
    tokenIndex: number,
    serverPos: number,
    snap: IGameSnapshot
  ): IListTokens[] => {
    const positionGame = working[seat].positionGame;
    const { typeTile, positionTile } = viewTileFromServerPos(
      serverPos,
      tokenIndex,
      snap,
      mySeat
    );
    return working.map((group, pIdx) => {
      if (pIdx !== seat) return group;
      return {
        ...group,
        tokens: group.tokens.map((t, tIdx) => {
          if (tIdx !== tokenIndex) return t;
          const next = applyTokenCell(
            t,
            positionGame,
            typeTile,
            positionTile,
            false
          );
          return {
            ...next,
            snapPlace: true,
            isMoving: false,
            animated: false,
            diceAvailable: [],
            canSelectToken: false,
            enableTooltip: false,
          };
        }),
      };
    });
  };

  const runMoveAnimation = useCallback(
    async (
      snapForLanding: IGameSnapshot,
      seat: number,
      tokenIndex: number,
      diceValue: TDicevalues,
      startServerPos: number | null,
      /** Keep animatingRef/isBusy locked for capture return after the hop. */
      keepAnimLock = false
    ) => {
      // Lock display FIRST — blocks sync from painting destination
      animatingRef.current = true;
      setIsBusy(true);
      animCancelRef.current = { cancelled: false };

      let working = listTokensRef.current;
      if (!working[seat]?.tokens[tokenIndex]) {
        animatingRef.current = false;
        setIsBusy(false);
        return false;
      }

      const positionGame = working[seat].positionGame;

      // ALWAYS sit on start cell before hopping (never start from destination)
      if (startServerPos != null) {
        working = placePawnInstant(
          working,
          seat,
          tokenIndex,
          startServerPos,
          snapForLanding
        );
        listTokensRef.current = working;
        await flushSyncAfterRender(() => {
          setListTokens(working);
        });
        await nextFrame();
        await nextFrame();
      }

      let token = working[seat].tokens[tokenIndex];
      // Clear snap flag, arm movement transition on the START cell
      working = working.map((group) => ({
        ...group,
        tokens: group.tokens.map((t, tIdx) => {
          const isMover = group.index === seat && tIdx === tokenIndex;
          if (isMover) {
            return {
              ...t,
              snapPlace: false,
              isMoving: true,
              animated: false,
              diceAvailable: [],
              canSelectToken: false,
              enableTooltip: false,
            };
          }
          return {
            ...t,
            snapPlace: false,
            isMoving: false,
            animated: false,
            diceAvailable: [],
            canSelectToken: false,
            enableTooltip: false,
          };
        }),
      }));
      token = working[seat].tokens[tokenIndex];
      listTokensRef.current = working;
      await flushSyncAfterRender(() => {
        setListTokens(working);
      });
      await nextFrame();
      await nextFrame();

      const path = buildMovePath(token, positionGame, diceValue);
      if (!path.length) {
        animatingRef.current = false;
        setIsBusy(false);
        return false;
      }

      setActionsTurn((prev) => ({
        ...prev,
        isDisabledUI: true,
        disabledDice: true,
        actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
      }));

      const t0 = performance.now();

      await runCellByCellSteps(
        path.length,
        ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE,
        (stepIndex) => {
          const step = path[stepIndex];
          playSound("passingNext");
          const group = working[seat];
          const nextToken = {
            ...applyTokenCell(
              group.tokens[tokenIndex],
              positionGame,
              step.typeTile,
              step.positionTile,
              true
            ),
            snapPlace: false,
          };
          const nextTokens = group.tokens.slice();
          nextTokens[tokenIndex] = nextToken;
          working = working.slice();
          working[seat] = { ...group, tokens: nextTokens };
          listTokensRef.current = working;
          setListTokens(working);
          if (step.typeTile === EtypeTile.END) {
            playSound("inside");
          }
        },
        animCancelRef.current,
        TOKEN_STEP_PAUSE_MS
      );

      working = working.map((group, pIdx) => {
        if (pIdx !== seat) return group;
        return {
          ...group,
          tokens: group.tokens.map((t, tIdx) =>
            tIdx === tokenIndex
              ? { ...t, isMoving: false, animated: false, snapPlace: false }
              : t
          ),
        };
      });
      setListTokens(working);
      listTokensRef.current = working;

      onlinePerf.markRender(performance.now() - t0);
      if (!keepAnimLock) {
        animatingRef.current = false;
        setIsBusy(false);
      }
      return true;
    },
    [mySeat]
  );

  /** Only the captured pawn walks back — never other pawns on the board. */
  const runPostMoveCaptureReturn = useCallback(
    async (moverSeat: number, moverToken: number): Promise<boolean> => {
      const captives: CaptureVictim[] = findCaptureVictims(
        listTokensRef.current,
        moverSeat,
        moverToken
      );
      if (!captives.length) {
        return false;
      }
      playSound("capture");
      animatingRef.current = true;
      setIsBusy(true);
      await runReturnToJailAnimations(
        listTokensRef.current,
        captives,
        (next) => {
          listTokensRef.current = next;
          setListTokens(next);
        },
        { cancel: animCancelRef.current }
      );
      return true;
    },
    []
  );

  // Lock BEFORE browser paints MOVE destination into any derived UI
  useLayoutEffect(() => {
    if (exitingRef.current || !snapshot) return;
    if (
      isMoveSnapshot(snapshot, lastAnimatedMoveSeqRef.current) &&
      !suppressMoveAnimRef.current
    ) {
      animatingRef.current = true;
    }
  }, [snapshot]);

  const handleDoneDiceRef = useRef<() => void>(() => undefined);

  // Apply snapshot → board (dice + opponent/bot move animations)
  useEffect(() => {
    if (exitingRef.current || !snapshot || mySeat < 0) return;
    let cancelled = false;

    const apply = async (snap: IGameSnapshot) => {
      if (cancelled) return;

      // Human online: match ended (win/forfeit) — skip animation queue
      if (isHumanOnlineMatch(snap) && snap.phase === "FINISHED") {
        animatingRef.current = false;
        suppressMoveAnimRef.current = false;
        rollingRef.current = false;
        pendingSnapRef.current = [];
        pendingDiceRef.current = null;
        finishDiceRollAnimation();
        setIsBusy(false);
        syncBoardFromSnapshot(snap, false, true);
        return;
      }

      const moveSeq = snap.actionSeq || 0;
      const isRemoteMove =
        !suppressMoveAnimRef.current &&
        isMoveSnapshot(snap, lastAnimatedMoveSeqRef.current);

      if (animatingRef.current && !isRemoteMove) {
        const q = pendingSnapRef.current;
        const last = q[q.length - 1];
        if (!last || (last.actionSeq || 0) !== (snap.actionSeq || 0)) {
          q.push(snap);
        }
        return;
      }

      const seq = ++applySeqRef.current;
      const prev = prevSnapRef.current;
      const diceSig = `${snap.actionSeq || 0}|${snap.currentSeatIndex}|${
        snap.phase
      }|${(snap.diceList || []).join(",")}`;

      const diceAppeared =
        (snap.diceList?.length || 0) > 0 &&
        diceSig !== lastDiceSigRef.current &&
        snap.phase === "AWAITING_MOVE";

      if (diceAppeared) {
        rollingRef.current = false;
        if (rollRecoveryTimerRef.current != null) {
          window.clearTimeout(rollRecoveryTimerRef.current);
          rollRecoveryTimerRef.current = null;
        }
        lastDiceSigRef.current = diceSig;
        pendingDiceRef.current = {
          seat: snap.currentSeatIndex,
          diceList: [...snap.diceList],
        };
        ensureBackgroundMusic();
        const value = snap.diceList[snap.diceList.length - 1] as TDicevalues;
        const flashKey = `${snap.actionSeq || 0}|${snap.currentSeatIndex}|${value}`;

        if (snap.currentSeatIndex === mySeat) {
          lastAutoMoveKeyRef.current = "";
          applyDiceOwnerTurn(snap, snap.currentSeatIndex);
          setPlayers(playersForView(snap, mySeat, roomId));
          setActionsTurn((prev) => {
            const base = actionsTurnFromSnapshot(snap, mySeat, prev);
            return applyServerDiceVisual(base, value);
          });
          beginDiceRollAnimation();
          prevSnapRef.current = {
            ...snap,
            tokenPositions:
              prev?.tokenPositions && Object.keys(prev.tokenPositions).length
                ? prev.tokenPositions
                : snap.tokenPositions,
          };
          snapshotRef.current = snap;
          return;
        }

        if (snap.currentSeatIndex !== mySeat) {
          playDiceRollingOnce(flashKey);
          setPlayers(playersForView(snap, mySeat, roomId));
          flashDiceOnSeat(snap, snap.currentSeatIndex, value, flashKey);
          prevSnapRef.current = {
            ...snap,
            tokenPositions:
              prev?.tokenPositions && Object.keys(prev.tokenPositions).length
                ? prev.tokenPositions
                : snap.tokenPositions,
          };
          window.setTimeout(() => {
            const next = pendingSnapRef.current.shift();
            if (next) void apply(next);
          }, DICE_ROLL_ANIM_MS + 80);
        }
        return;
      }

      // Server turn already passed (skip when a MOVE still needs animation)
      const moveNeedsAnim = isMoveSnapshot(
        snap,
        lastAnimatedMoveSeqRef.current
      );
      if (isTurnSeatHandoff(snap, prev) && !animatingRef.current && !moveNeedsAnim) {
        rollingRef.current = false;
        finishDiceRollAnimation();
        setIsBusy(false);
        isBusyRef.current = false;
        lastProcessedRollIdRef.current = "";
        lastDiceSigRef.current = diceSig;
        diceOwnerSeatRef.current = snap.currentSeatIndex;
        pendingDiceRef.current = null;
        syncBoardFromSnapshot(snap);
        return;
      }

      // PASS / TIMEOUT / no-move: show roll (if any), pause, then hand die to next seat
      if (isStableTurnPass(snap, prev)) {
        rollingRef.current = false;
        lastProcessedRollIdRef.current = "";
        lastDiceSigRef.current = diceSig;
        setPlayers(playersForView(snap, mySeat, roomId));

        const rollerSeat =
          snap.lastActionSeat ?? prev?.currentSeatIndex ?? snap.currentSeatIndex;
        const rolledValue = snap.lastActionDice;
        let passDelayMs = ONLINE_TURN_PASS_DELAY_MS;

        if (
          rolledValue != null &&
          rolledValue >= 1 &&
          rolledValue <= 6 &&
          rollerSeat !== snap.currentSeatIndex
        ) {
          const flashKey = `${snap.actionSeq || 0}|${rollerSeat}|${rolledValue}`;
          if (rollerSeat !== mySeat) {
            playDiceRollingOnce(flashKey);
          }
          flashDiceOnSeat(
            snap,
            rollerSeat,
            rolledValue as TDicevalues,
            flashKey
          );
          passDelayMs = Math.max(
            ONLINE_TURN_PASS_DELAY_MS,
            DICE_ROLL_ANIM_MS + 250
          );
        }

        passFlashUntilRef.current = performance.now() + passDelayMs;
        await rafDelay(passDelayMs, animCancelRef.current);
        if (cancelled) return;
        rollingRef.current = false;
        pendingDiceRef.current = null;
        passFlashUntilRef.current = 0;
        syncBoardFromSnapshot(snap);
        const nextPass = pendingSnapRef.current.shift();
        if (nextPass) void apply(nextPass);
        return;
      }

  // Prefer server lastAction (WS event) for reliable opponent move animation
      let moved: { seat: number; tokenIndex: number } | null = null;
      let diceValue: TDicevalues | 0 = 0;

      if (
        !suppressMoveAnimRef.current &&
        isMoveSnapshot(snap, lastAnimatedMoveSeqRef.current) &&
        snap.lastActionSeat != null &&
        snap.lastActionTokenIndex != null &&
        (!prev || (snap.actionSeq || 0) !== (prev.actionSeq || 0))
      ) {
        moved = {
          seat: snap.lastActionSeat,
          tokenIndex: snap.lastActionTokenIndex,
        };
        diceValue = moveDiceValueFromSnapshot(snap, [
          pendingDiceRef.current?.diceList?.[0] ?? 0,
          actionsTurnRef.current.diceValue as number,
        ]) as TDicevalues;
      }

      // MOVE without dice metadata — snap board instantly (no stuck roll face)
      if (
        moved &&
        (diceValue < 1 || diceValue > 6) &&
        moveSeq !== lastAnimatedMoveSeqRef.current
      ) {
        // Rare server MOVE without dice metadata:
        // clear all local animation locks so the next turn is not blocked.
        animatingRef.current = false;
        suppressMoveAnimRef.current = false;
        setIsBusy(false);
        rollingRef.current = false;
        pendingDiceRef.current = null;
        lastAnimatedMoveSeqRef.current = moveSeq;
        syncBoardFromSnapshot(snap, false, true);
        const next = pendingSnapRef.current.shift();
        if (next) void apply(next);
        return;
      }

      if (moved && diceValue >= 1 && diceValue <= 6) {
        if (moveSeq > 0 && moveSeq === lastAnimatedMoveSeqRef.current) {
          animatingRef.current = false;
          syncBoardFromSnapshot(snap, true, true);
          return;
        }

        // Bot/opponent often emit MOVE without a prior AWAITING_MOVE — flash die first.
        const flashKey = `${snap.actionSeq || 0}|${moved.seat}|${diceValue}`;
        const priorRollAlreadyVisible =
          !!prev &&
          prev.currentSeatIndex === moved.seat &&
          prev.phase === "AWAITING_MOVE" &&
          (prev.diceList?.length || 0) > 0 &&
          (prev.diceList || []).includes(diceValue as number);

        if (!diceRollPendingRef.current && !priorRollAlreadyVisible) {
          const flashed = flashDiceOnSeat(
            snap,
            moved.seat,
            diceValue as TDicevalues,
            flashKey
          );
          if (flashed && moved.seat !== mySeat) {
            playDiceRollingOnce(flashKey);
          }
        }

        await waitForDiceRollAnimation();
        if (cancelled) return;
        // Freeze board BEFORE any paint of destination positions
        animatingRef.current = true;
        lastDiceSigRef.current = diceSig;
        const colors = seatColorsFromSnapshot(snap);
        const startPos =
          snap.lastActionFrom != null
            ? snap.lastActionFrom
            : prev?.tokenPositions?.[colors[moved.seat]]?.[moved.tokenIndex] ??
              null;

        // Instant start cell NOW (sync), before any await — kills destination flash
        if (startPos != null && listTokensRef.current[moved.seat]) {
          const placed = placePawnInstant(
            listTokensRef.current,
            moved.seat,
            moved.tokenIndex,
            startPos,
            snap
          );
          listTokensRef.current = placed;
          await flushSyncAfterRender(() => setListTokens(placed));
        }

        const ok = await runMoveAnimation(
          snap,
          moved.seat,
          moved.tokenIndex,
          diceValue as TDicevalues,
          // Already placed above — pass null to skip second snap
          null,
          true
        );
        if (cancelled) return;
        if (seq !== applySeqRef.current) return;
        lastAnimatedMoveSeqRef.current = moveSeq;
        pendingDiceRef.current = null;
        suppressMoveAnimRef.current = false;
        if (ok) {
          await runPostMoveCaptureReturn(moved.seat, moved.tokenIndex);
          if (cancelled) return;
          if (seq !== applySeqRef.current) return;
        }
        animatingRef.current = false;
        setIsBusy(false);
        isBusyRef.current = false;
        finishDiceRollAnimation();
        rollingRef.current = false;
        if (
          snap.currentSeatIndex !== moved.seat &&
          snap.phase === "AWAITING_ROLL"
        ) {
          await rafDelay(ONLINE_TURN_PASS_DELAY_MS, animCancelRef.current);
          if (cancelled) return;
        }
        syncBoardFromSnapshot(snap, false, true);
        const next = pendingSnapRef.current.shift();
        if (next) void apply(next);
        return;
      }

      // Local player already animating this move — never paint destination early
      if (suppressMoveAnimRef.current || animatingRef.current) {
        const q = pendingSnapRef.current;
        const last = q[q.length - 1];
        if (!last || (last.actionSeq || 0) !== (snap.actionSeq || 0)) {
          q.push(snap);
        }
        return;
      }

      lastDiceSigRef.current = diceSig;
      syncBoardFromSnapshot(snap);
      const nextQueued = pendingSnapRef.current.shift();
      if (nextQueued) void apply(nextQueued);
    };

    void apply(snapshot);
    return () => {
      cancelled = true;
    };
  }, [snapshot, mySeat, syncBoardFromSnapshot, runMoveAnimation, runPostMoveCaptureReturn, beginDiceRollAnimation, waitForDiceRollAnimation, flashDiceOnSeat, playDiceRollingOnce]);

  const handleSelectDice = useCallback(
    (_diceValue?: TDicevalues) => {
      const live = snapshotRef.current ?? snapshot;
      if (
        !canRequestOnlineRoll(live, {
          mySeat,
          isBusy,
          isAnimating: animatingRef.current,
          isRolling: rollingRef.current,
          isActionInFlight: isActionInFlight(),
          disabledDice: actionsTurnRef.current.disabledDice,
        })
      ) {
        return;
      }
      rollingRef.current = true;
      ensureBackgroundMusic();
      playSound("diceRolling");
      setActionsTurn((prev) => {
        const next = applyServerDiceVisual(prev, 1 as TDicevalues);
        actionsTurnRef.current = next;
        return next;
      });
      if (rollRecoveryTimerRef.current != null) {
        window.clearTimeout(rollRecoveryTimerRef.current);
      }
      rollRecoveryTimerRef.current = window.setTimeout(() => {
        rollRecoveryTimerRef.current = null;
        if (!rollingRef.current) return;
        rollingRef.current = false;
        finishDiceRollAnimation();
        const latest = snapshotRef.current;
        if (
          latest?.currentSeatIndex === mySeat &&
          latest.phase === "AWAITING_ROLL"
        ) {
          setActionsTurn((prev) => ({
            ...prev,
            disabledDice: onlineDiceDisabled(latest, mySeat),
          }));
        }
      }, 4000);
      rollDice();
    },
    [snapshot, isBusy, mySeat, rollDice, isActionInFlight, finishDiceRollAnimation]
  );

  const handleSelectedToken = useCallback(
    async (
      select: ISelectTokenValues,
      snapOverride?: IGameSnapshot
    ): Promise<boolean> => {
      const live = snapOverride ?? snapshotRef.current ?? snapshot;
      if (!live) return false;
      if (live.currentSeatIndex !== mySeat) return false;
      if (live.phase !== "AWAITING_MOVE") return false;
      if (isActionInFlight()) return false;

      const { tokenIndex, diceIndex } = select;
      const diceValue = (live.diceList || [])[diceIndex] as TDicevalues;
      if (!diceValue) return false;

      const moveKey = `${live.actionSeq ?? 0}|${(live.diceList || []).join(",")}|${tokenIndex}|${diceIndex}`;

      const legal =
        live.legalMoves?.length
          ? live.legalMoves
          : (live.legalTokenIndexes || []).map((ti) => ({
              tokenIndex: ti,
              diceIndex: 0,
            }));
      const allowed = legal.some(
        (m) => m.tokenIndex === tokenIndex && m.diceIndex === diceIndex
      );
      if (!allowed) return false;

      if (animatingRef.current) return false;

      if (diceRollPendingRef.current) return false;

      if (lastAutoMoveKeyRef.current === moveKey) return false;
      lastAutoMoveKeyRef.current = moveKey;

      animatingRef.current = true;
      isBusyRef.current = true;
      setIsBusy(true);
      suppressMoveAnimRef.current = true;
      pendingDiceRef.current = {
        seat: mySeat,
        diceList: [...(live.diceList || [])],
      };
      const colors = seatColorsFromSnapshot(live);
      const startPos =
        live.tokenPositions?.[colors[mySeat]]?.[tokenIndex] ?? null;
      moveToken(tokenIndex, diceIndex);
      const ok = await runMoveAnimation(
        live,
        mySeat,
        tokenIndex,
        diceValue,
        startPos,
        true
      );
      if (ok) {
        await runPostMoveCaptureReturn(mySeat, tokenIndex);
      } else {
        lastAutoMoveKeyRef.current = "";
      }
      animatingRef.current = false;
      isBusyRef.current = false;
      setIsBusy(false);
      suppressMoveAnimRef.current = false;
      const queued = pendingSnapRef.current.splice(0);
      const latest = queued.length ? queued[queued.length - 1] : null;
      if (latest) {
        if (latest.actionSeq) {
          lastAnimatedMoveSeqRef.current = latest.actionSeq;
        }
        if (
          latest.currentSeatIndex !== mySeat &&
          latest.phase === "AWAITING_ROLL"
        ) {
          await rafDelay(ONLINE_TURN_PASS_DELAY_MS, animCancelRef.current);
        }
        syncBoardFromSnapshot(latest, false, true);
      } else if (ok && live) {
        prevSnapRef.current = {
          ...(prevSnapRef.current || live),
          actionSeq: (prevSnapRef.current?.actionSeq || 0) + 1,
        };
      }
      return ok;
    },
    [snapshot, mySeat, isActionInFlight, runMoveAnimation, runPostMoveCaptureReturn, moveToken, syncBoardFromSnapshot]
  );

  const scheduleHumanAutoMove = useCallback(
    (snap?: IGameSnapshot) => {
      if (autoMoveTimerRef.current != null) {
        window.clearTimeout(autoMoveTimerRef.current);
      }
      let attempts = 0;
      const attempt = () => {
        attempts += 1;
        const live = snap ?? snapshotRef.current ?? snapshot;
        if (!live || live.currentSeatIndex !== mySeat) return;
        if (live.phase !== "AWAITING_MOVE") return;
        if (!shouldEnableTokenSelection(live, mySeat)) {
          if (attempts < 40) {
            autoMoveTimerRef.current = window.setTimeout(attempt, 50);
          }
          return;
        }
        if (animatingRef.current || isActionInFlight() || diceRollPendingRef.current) {
          if (attempts < 40) {
            autoMoveTimerRef.current = window.setTimeout(attempt, 50);
          }
          return;
        }
        const autoMove = pickHumanAutoMoveFromSnapshot(live, mySeat);
        if (!autoMove) return;
        void handleSelectedToken(autoMove, live).then((started) => {
          if (!started && attempts < 40) {
            lastAutoMoveKeyRef.current = "";
            autoMoveTimerRef.current = window.setTimeout(attempt, 50);
          }
        });
      };
      autoMoveTimerRef.current = window.setTimeout(attempt, 0);
    },
    [snapshot, mySeat, isActionInFlight, handleSelectedToken]
  );

  scheduleHumanAutoMoveRef.current = scheduleHumanAutoMove;

  /**
   * Watchdog: sometimes bot/opponent flows can leave local animation/busy locks stale
   * even though server turn progressed. Auto-unlock after a stable seq window.
   */
  useEffect(() => {
    const id = window.setInterval(() => {
      const live = snapshotRef.current;
      if (!live) return;
      if (live.phase === "FINISHED") return;
      if (
        !animatingRef.current &&
        !isBusyRef.current &&
        !diceRollPendingRef.current &&
        !rollingRef.current
      ) {
        return;
      }

      const seq = live.actionSeq || 0;
      if (lockSeqRef.current.seq !== seq) {
        lockSeqRef.current = { seq, at: Date.now() };
        return;
      }

      const elapsed = Date.now() - lockSeqRef.current.at;
      const unlockMs =
        rollingRef.current && !diceRollPendingRef.current ? 2800 : 4500;
      if (elapsed < unlockMs) return;
      if (isActionInFlight()) return;
      if (isMoveSnapshot(live, lastAnimatedMoveSeqRef.current)) return;

      animatingRef.current = false;
      suppressMoveAnimRef.current = false;
      rollingRef.current = false;
      pendingDiceRef.current = null;
      passFlashUntilRef.current = 0;
      finishDiceRollAnimation();
      setIsBusy(false);
      syncBoardFromSnapshot(live, false, true);
      lockSeqRef.current = { seq: live.actionSeq || 0, at: Date.now() };
    }, 500);

    return () => window.clearInterval(id);
  }, [finishDiceRollAnimation, syncBoardFromSnapshot, isActionInFlight]);

  useEffect(() => {
    if (!snapshot || mySeat < 0) return;
    if (myEliminated || snapshot.eliminated?.[mySeat]) return;
    scheduleHumanAutoMove(snapshot);
  }, [snapshot, mySeat, scheduleHumanAutoMove, isBusy, listTokens, myEliminated]);

  const handleDoneDice = useCallback(() => {
    finishDiceRollAnimation();
    if (!snapshot) return;

    const live = snapshotRef.current ?? snapshot;
    const isMyMovePhase =
      live.currentSeatIndex === mySeat && live.phase === "AWAITING_MOVE";

    if (animatingRef.current && !isMyMovePhase) return;
    rollingRef.current = false;

    if (live.currentSeatIndex !== mySeat) {
      rollingRef.current = false;
      const owner = diceOwnerSeatRef.current;
      const diceFace = (actionsTurnRef.current.diceValue || 0) as number;
      if (isNoMovePassSnapshot(live)) {
        return;
      }
      if (shouldClearStuckDice(live, owner, diceFace)) {
        diceOwnerSeatRef.current = live.currentSeatIndex;
        syncBoardFromSnapshot(live);
      } else if (live.phase === "AWAITING_MOVE") {
        setListTokens(listTokensFromSnapshot(live, mySeat, false));
        setActionsTurn((prev) => ({
          ...prev,
          ...actionsTurnFromSnapshot(live, mySeat, prev),
          diceValue: prev.diceValue,
          diceRollNumber: prev.diceRollNumber,
          actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
        }));
      }
      return;
    }

    const actions = actionsTurnRef.current as IActionsTurn & { rollId?: string };
    const rollId = buildRollDedupKey(
      live,
      actions.diceRollNumber,
      actions.diceValue as number
    );
    if (rollId && lastProcessedRollIdRef.current === rollId) {
      const canMove = shouldEnableTokenSelection(live, mySeat);
      if (canMove) {
        const nextTokens = listTokensFromSnapshot(live, mySeat, true);
        setListTokens(nextTokens);
        listTokensRef.current = nextTokens;
      }
      scheduleHumanAutoMove(live);
      return;
    }
    lastProcessedRollIdRef.current = rollId;

    const canMove = shouldEnableTokenSelection(live, mySeat);
    const nextTokens = listTokensFromSnapshot(live, mySeat, canMove);
    setListTokens(nextTokens);
    listTokensRef.current = nextTokens;
    setActionsTurn((prev) => ({
      ...prev,
      ...actionsTurnFromSnapshot(live, mySeat, prev),
      diceValue: prev.diceValue,
      diceRollNumber: prev.diceRollNumber,
      actionsBoardGame: EActionsBoardGame.SELECT_TOKEN,
    }));

    scheduleHumanAutoMove(live);
  }, [snapshot, mySeat, syncBoardFromSnapshot, scheduleHumanAutoMove, finishDiceRollAnimation]);

  handleDoneDiceRef.current = handleDoneDice;

  const lostSummaryEntries = useMemo(
    () => buildLiveSummary(snapshot, guest.id, roomId),
    [snapshot, guest.id, roomId]
  );

  const clearExitTimers = useCallback(() => {
    animCancelRef.current = { cancelled: true };
    if (autoMoveTimerRef.current != null) {
      window.clearTimeout(autoMoveTimerRef.current);
      autoMoveTimerRef.current = null;
    }
    if (diceRollFallbackRef.current != null) {
      window.clearTimeout(diceRollFallbackRef.current);
      diceRollFallbackRef.current = null;
    }
    diceRollPendingRef.current = false;
    setDiceRolling(false);
    animatingRef.current = false;
    suppressMoveAnimRef.current = false;
    rollingRef.current = false;
    pendingSnapRef.current = [];
    pendingDiceRef.current = null;
    setIsBusy(false);
  }, []);

  const confirmLeaveMatch = useCallback(async () => {
    setShowLeaveConfirm(false);
    if (exitingRef.current) return;
    exitingRef.current = true;
    stopBackgroundMusic();
    clearExitTimers();

    const live = snapshotRef.current;
    const inMatch = !!live && live.phase !== "FINISHED";
    const humanMatch = isHumanOnlineMatch(live);

    if (roomId) {
      try {
        if (!leftRoomRef.current) {
          await leaveRoom(roomId, guest.id);
          leftRoomRef.current = true;
        }
      } catch {
        // room may already be completed
      }
    }

    if (inMatch && humanMatch && roomId) {
      try {
        const updated = await ensureGameSnapshot(roomId);
        setSnapshot(updated);
        void refreshBalance();
        lostSummaryShownRef.current = true;
        setVoluntaryExit(true);
        setShowLostSummary(true);
        return;
      } catch {
        // fall through to lobby
      }
    }

    onExit();
  }, [
    roomId,
    guest.id,
    onExit,
    setSnapshot,
    refreshBalance,
    clearExitTimers,
  ]);

  const handleBackPress = useCallback(() => {
    if (exitingRef.current) return;
    const live = snapshotRef.current;
    const inMatch =
      boardReady && !!live && live.phase !== "FINISHED";
    if (inMatch) {
      setShowLeaveConfirm(true);
      return;
    }
    void confirmLeaveMatch();
  }, [boardReady, confirmLeaveMatch]);

  const matchPlayerCount = useMemo(() => {
    if (snapshot?.seatColors?.length) return snapshot.seatColors.length;
    if (snapshot?.usernames?.length) return snapshot.usernames.length;
    return 4;
  }, [snapshot]);

  const isTwoPlayerMatch = matchPlayerCount === 2;
  const matchEnded = snapshot?.phase === "FINISHED";

  useEffect(() => {
    if (voluntaryExit) return;
    if (matchEnded && showLostSummary) {
      setShowLostSummary(false);
    }
  }, [matchEnded, showLostSummary, voluntaryExit]);

  useEffect(() => {
    if (voluntaryExit) return;
    if (myEliminated && isTwoPlayerMatch && matchEnded) {
      setShowResults(true);
    }
  }, [myEliminated, isTwoPlayerMatch, matchEnded, voluntaryExit]);

  const resultEntries: IResultEntry[] = useMemo(
    () => buildResults(snapshot, guest.id, roomId),
    [snapshot, guest.id, roomId]
  );

  // Local perspective: compact profile list (my home = bottom-left).
  const renderPlayers =
    players.length > 0
      ? players
      : snapshot && mySeat >= 0
      ? playersForView(snapshot, mySeat, roomId)
      : [];

  if (snapshot && mySeat >= 0 && lockedBoardColorRef.current === null) {
    lockedBoardColorRef.current = boardColorForSnapshot(snapshot, mySeat);
  }
  const boardColor =
    lockedBoardColorRef.current ??
    boardColorForSnapshot(snapshot, mySeat);

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
    turnColor,
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
          onClick={handleBackPress}
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
        {liveBalance != null && (
          <div
            style={{
              position: "absolute",
              top: 10,
              left: "50%",
              transform: "translateX(-50%)",
              zIndex: 30,
              padding: "6px 12px",
              borderRadius: 999,
              background: "rgba(0,0,0,0.55)",
              color: "#ffe566",
              fontSize: "0.85rem",
              fontWeight: 700,
            }}
          >
            Wallet ₹{liveBalance.toFixed(2)}
          </div>
        )}
        <p className="lobby-sub" style={{ marginTop: 80, textAlign: "center" }}>
          {loadError
            ? /expired|finished|persisted snapshot|Cannot restore/i.test(loadError)
              ? "This match is no longer available"
              : connected
                ? "Loading board…"
                : "Connecting…"
            : connected
              ? "Loading board…"
              : "Connecting…"}
        </p>
        {loadError && (
          <p
            className="lobby-footer-note"
            style={{ color: "#ffd0d0", textAlign: "center", marginTop: 12 }}
          >
            {loadError}
          </p>
        )}
        {loadError &&
          /expired|finished|persisted snapshot|Cannot restore/i.test(loadError) && (
            <div style={{ textAlign: "center", marginTop: 20 }}>
              <button
                type="button"
                className="lobby-btn primary"
                onClick={onPlayAgain}
              >
                Start new game
              </button>
            </div>
          )}
        <p className="lobby-footer-note" style={{ textAlign: "center" }}>
          API: {process.env.REACT_APP_API_URL || "http://localhost:3000"}
        </p>
        {showLeaveConfirm && (
          <LeaveMatchConfirmPopup
            isTwoPlayer={isTwoPlayerMatch}
            isHumanMatch={isHumanOnlineMatch(snapshot)}
            onConfirm={() => void confirmLeaveMatch()}
            onCancel={() => setShowLeaveConfirm(false)}
          />
        )}
      </PageWrapper>
    );
  }

  return (
    <PageWrapper>
      <button
        className="game-back-arrow"
        type="button"
        aria-label="Back"
        onClick={handleBackPress}
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
      {liveBalance != null && (
        <div
          style={{
            position: "absolute",
            top: 10,
            left: "50%",
            transform: "translateX(-50%)",
            zIndex: 30,
            display: "flex",
            alignItems: "center",
            gap: 6,
            padding: "6px 12px",
            borderRadius: 999,
            background: "rgba(0,0,0,0.55)",
            color: "#ffe566",
            fontSize: "0.85rem",
            fontWeight: 700,
            whiteSpace: "nowrap",
            pointerEvents: "none",
            boxShadow: "0 2px 8px rgba(0,0,0,0.25)",
          }}
          aria-label="Wallet balance"
        >
          <span style={{ opacity: 0.85, fontWeight: 600 }}>Wallet</span>
          <span>₹{liveBalance.toFixed(2)}</span>
        </div>
      )}
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
              isDisabledUI={actionsTurn.isDisabledUI || isBusy || diceRolling}
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
      {(showLostSummary && (!matchEnded || voluntaryExit)) && (
        <LostSummaryPopup
          entries={lostSummaryEntries}
          entryAmount={ONLINE_ENTRY_AMOUNT}
          isTwoPlayer={isTwoPlayerMatch}
          exitReason={voluntaryExit ? "left" : "timeout"}
          onExit={() => {
            if (isTwoPlayerMatch) {
              setShowLostSummary(false);
              setShowResults(true);
              return;
            }
            if (!leftRoomRef.current) {
              void leaveRoom(roomId, guest.id).catch(() => undefined);
            }
            onExit();
          }}
          onWatch={() => setShowLostSummary(false)}
        />
      )}
      {showLeaveConfirm && (
        <LeaveMatchConfirmPopup
          isTwoPlayer={isTwoPlayerMatch}
          isHumanMatch={isHumanOnlineMatch(snapshot)}
          onConfirm={() => void confirmLeaveMatch()}
          onCancel={() => setShowLeaveConfirm(false)}
        />
      )}
    </PageWrapper>
  );
};

function buildLiveSummary(
  snapshot: IGameSnapshot | null,
  myId: string,
  stableRoomId: string
): IResultEntry[] {
  if (!snapshot?.usernames) return [];
  const roomId = stableRoomId || snapshot.roomId || "room";
  const colors = seatColorsFromSnapshot(snapshot);
  const used: string[] = [];
  return snapshot.usernames.map((name, seat) => {
    const seatKey = seatDisplayKey(roomId, snapshot.userIds?.[seat], seat);
    const display = displayPlayerName(
      name,
      seatKey,
      used,
      !!snapshot.isBot?.[seat]
    );
    used.push(display);
    const lost =
      !!snapshot.eliminated?.[seat] ||
      (snapshot.consecutiveTimeouts?.[seat] ?? 0) >= MAX_PLAYER_CHANCES;
    const finished = !!snapshot.finished?.[seat];
    const isWinner =
      snapshot.winnerSeat === seat || snapshot.standings?.[seat] === 1;
    return {
      rank: isWinner ? 1 : 0,
      name: display,
      color: colors[seat],
      isBot: snapshot.isBot?.[seat],
      isYou: snapshot.userIds?.[seat] === myId,
      won: isWinner,
      lost: !isWinner && (lost || finished),
      exited: lost,
      playing: !lost && !finished && !isWinner,
    };
  });
}

function buildResults(
  snapshot: IGameSnapshot | null,
  myId: string,
  stableRoomId: string
): IResultEntry[] {
  if (!snapshot?.usernames || !snapshot.standings) return [];
  const roomId = stableRoomId || snapshot.roomId || "room";
  const colors = seatColorsFromSnapshot(snapshot);
  const used: string[] = [];
  return snapshot.usernames.map((name, seat) => {
    const seatKey = seatDisplayKey(roomId, snapshot.userIds?.[seat], seat);
    const display = displayPlayerName(
      name,
      seatKey,
      used,
      !!snapshot.isBot?.[seat]
    );
    used.push(display);
    const isWinner =
      snapshot.winnerSeat === seat || snapshot.standings?.[seat] === 1;
    return {
      rank: isWinner ? 1 : 0,
      name: display,
      color: colors[seat],
      isBot: snapshot.isBot?.[seat],
      isYou: snapshot.userIds?.[seat] === myId,
      won: isWinner,
      lost: !isWinner,
      exited: !!snapshot.eliminated?.[seat],
    };
  });
}

export default React.memo(OnlineGame);
