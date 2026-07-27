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
  ONLINE_TURN_PASS_DELAY_MS,
  ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE,
  ROLL_TIME_VALUE,
  TOKEN_STEP_PAUSE_MS,
} from "../../utils/constants";
import { playSound, preloadGameSounds, beginMatchMusic, stopBackgroundMusic } from "../../utils/sounds";
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
  shouldAutoExitJailOnFirstSix,
} from "../game/rules";
import type { IGameSnapshot, IGuestUser, IResultEntry } from "./types";
import Results from "./Results";
import { fetchWalletBalance } from "../../api/ludoApi";
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
} from "./onlineSnapshotBoard";
import {
  buildRollDedupKey,
  canRequestOnlineRoll,
  isMoveSnapshot,
  isStableTurnPass,
  isTurnSeatHandoff,
  isNoMovePassSnapshot,
  moveDiceValueFromSnapshot,
  shouldClearStuckDice,
  shouldEnableTokenSelection,
} from "./diceTurnLogic";

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
  const { snapshot, connected, loadError, rollDice, moveToken, isActionInFlight } =
    useGameSocket(roomId, guest.id, initialSnapshot);
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
  const [liveBalance, setLiveBalance] = useState<number | null>(
    walletBalance ?? null
  );

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
    if (snapshot?.phase === "FINISHED") {
      void refreshBalance();
    }
  }, [snapshot?.phase, refreshBalance]);

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
  const pendingSnapRef = useRef<IGameSnapshot[]>([]);
  const applySeqRef = useRef(0);
  /** actionSeq of MOVE we already finished animating — prevents double-play */
  const lastAnimatedMoveSeqRef = useRef(0);
  const rollingRef = useRef(false);
  const lastProcessedRollIdRef = useRef("");
  /** Server seat that owns the dice UI until their turn fully ends. */
  const diceOwnerSeatRef = useRef(-1);
  const lockedBoardColorRef = useRef<ReturnType<
    typeof boardColorForSnapshot
  > | null>(null);
  const snapshotRef = useRef<IGameSnapshot | null>(snapshot);

  useEffect(() => {
    snapshotRef.current = snapshot;
  }, [snapshot]);

  useEffect(() => {
    lockedBoardColorRef.current = null;
    diceOwnerSeatRef.current = -1;
    clearDisplayNameCache(roomId);
  }, [roomId]);

  /** Map dice-owner server seat → profile slot (BL/TL/TR/BR). */
  const applyDiceOwnerTurn = useCallback(
    (snap: IGameSnapshot, ownerSeat?: number) => {
      if (ownerSeat != null && ownerSeat >= 0) {
        diceOwnerSeatRef.current = ownerSeat;
      }
      const seat =
        diceOwnerSeatRef.current >= 0
          ? diceOwnerSeatRef.current
          : snap.currentSeatIndex;
      setCurrentTurn(profileTurnIndex(snap, seat, mySeat));
    },
    [mySeat]
  );

  useEffect(() => {
    if (!snapshot || mySeat < 0) return;
    if (diceOwnerSeatRef.current < 0) {
      diceOwnerSeatRef.current = snapshot.currentSeatIndex;
      applyDiceOwnerTurn(snapshot);
    }
  }, [snapshot, mySeat, applyDiceOwnerTurn]);

  useEffect(() => {
    listTokensRef.current = listTokens;
  }, [listTokens]);
  useEffect(() => {
    actionsTurnRef.current = actionsTurn;
  }, [actionsTurn]);

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

      // MOVE snapshots carry destination tokenPositions — never paint those
      // until forceTokens (animation finished). Chrome/UI-only update instead.
      const isMoveSnap = snap.lastActionType === "MOVE";
      if (isMoveSnap && !forceTokens) {
        const prev = prevSnapRef.current;
        setPlayers(playersForView(snap, mySeat, roomId));
        // Keep dice on the mover until hop finishes — do not jump to next seat yet
        applyDiceOwnerTurn(snap);
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
        playSound("passingNext");
        diceOwnerSeatRef.current = snap.currentSeatIndex;
        applyDiceOwnerTurn(snap);
        setActionsTurn((prevActions) =>
          actionsTurnFromSnapshot(snap, mySeat, prevActions, prevSeat)
        );
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
        isMyTurn && snap.phase === "AWAITING_MOVE" && !animatingRef.current;
      const nextTokens = listTokensFromSnapshot(snap, mySeat, canMove);
      setPlayers(nextPlayers);
      setListTokens(nextTokens);
      listTokensRef.current = nextTokens;
      const turnPassed =
        prevSeat != null && prevSeat !== snap.currentSeatIndex;
      if (
        !animatingRef.current &&
        turnPassed &&
        snap.phase === "AWAITING_ROLL"
      ) {
        playSound("passingNext");
        diceOwnerSeatRef.current = snap.currentSeatIndex;
      } else if (!animatingRef.current && turnPassed) {
        diceOwnerSeatRef.current = snap.currentSeatIndex;
      }
      applyDiceOwnerTurn(snap);
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
      prevSnapRef.current = snap;
    },
    [mySeat, applyDiceOwnerTurn, roomId]
  );

  /** Clear stuck dice when server turn already moved to the next seat. */
  useEffect(() => {
    if (!snapshot || mySeat < 0) return;
    if (animatingRef.current) return;
    if (rollingRef.current) return;
    if (isNoMovePassSnapshot(snapshot)) return;
    const owner = diceOwnerSeatRef.current;
    const diceFace = (actionsTurnRef.current.diceValue || 0) as number;
    if (shouldClearStuckDice(snapshot, owner, diceFace)) {
      rollingRef.current = false;
      lastProcessedRollIdRef.current = "";
      diceOwnerSeatRef.current = snapshot.currentSeatIndex;
      syncBoardFromSnapshot(snapshot);
    }
  }, [snapshot, mySeat, syncBoardFromSnapshot]);

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
      startServerPos: number | null
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
        flushSync(() => {
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
      flushSync(() => {
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
      animatingRef.current = false;
      setIsBusy(false);
      return true;
    },
    [mySeat]
  );

  // Lock BEFORE browser paints MOVE destination into any derived UI
  useLayoutEffect(() => {
    if (!snapshot) return;
    if (
      isMoveSnapshot(snapshot, lastAnimatedMoveSeqRef.current) &&
      !suppressMoveAnimRef.current
    ) {
      animatingRef.current = true;
    }
  }, [snapshot]);

  // Apply snapshot → board (dice + opponent/bot move animations)
  useEffect(() => {
    if (!snapshot || mySeat < 0) return;
    let cancelled = false;

    const apply = async (snap: IGameSnapshot) => {
      if (cancelled) return;

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
      const diceSig = `${snap.currentSeatIndex}|${snap.phase}|${(
        snap.diceList || []
      ).join(",")}`;

      const diceAppeared =
        (snap.diceList?.length || 0) > 0 &&
        diceSig !== lastDiceSigRef.current &&
        snap.phase === "AWAITING_MOVE";

      if (diceAppeared) {
        rollingRef.current = false;
        lastDiceSigRef.current = diceSig;
        pendingDiceRef.current = {
          seat: snap.currentSeatIndex,
          diceList: [...snap.diceList],
        };
        const value = snap.diceList[snap.diceList.length - 1] as TDicevalues;
        if (snap.currentSeatIndex !== mySeat) {
          playSound("diceRolling");
        }
        beginMatchMusic();
        setPlayers(playersForView(snap, mySeat, roomId));
        diceOwnerSeatRef.current = snap.currentSeatIndex;
        applyDiceOwnerTurn(snap);
        setActionsTurn((prevActions) => {
          const base = actionsTurnFromSnapshot(
            snap,
            mySeat,
            prevActions,
            prev?.currentSeatIndex
          );
          const rolled = applyServerDiceVisual(base, value);
          rolled.diceList = base.diceList;
          rolled.actionsBoardGame = EActionsBoardGame.ROLL_DICE;
          return rolled;
        });
        // Never full-sync here — it races the dice visual and clears the first roll value
        prevSnapRef.current = {
          ...snap,
          tokenPositions:
            prev?.tokenPositions && Object.keys(prev.tokenPositions).length
              ? prev.tokenPositions
              : snap.tokenPositions,
        };
        return;
      }

      // Server turn already passed (skip when a MOVE still needs animation)
      const moveNeedsAnim = isMoveSnapshot(
        snap,
        lastAnimatedMoveSeqRef.current
      );
      if (isTurnSeatHandoff(snap, prev) && !animatingRef.current && !moveNeedsAnim) {
        rollingRef.current = false;
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
          if (rollerSeat !== mySeat) {
            playSound("diceRolling");
          }
          diceOwnerSeatRef.current = rollerSeat;
          applyDiceOwnerTurn(snap, rollerSeat);
          setActionsTurn((prevActions) => {
            const base = actionsTurnFromSnapshot(
              {
                ...snap,
                currentSeatIndex: rollerSeat,
                phase: "AWAITING_MOVE",
                diceList: [rolledValue],
              },
              mySeat,
              prevActions,
              prev?.currentSeatIndex
            );
            const rolled = applyServerDiceVisual(
              base,
              rolledValue as TDicevalues
            );
            rolled.diceList = [];
            rolled.actionsBoardGame = EActionsBoardGame.ROLL_DICE;
            return rolled;
          });
          passDelayMs = Math.max(
            ONLINE_TURN_PASS_DELAY_MS,
            Math.round(ROLL_TIME_VALUE * 1000) + 250
          );
        }

        await rafDelay(passDelayMs, animCancelRef.current);
        if (cancelled) return;
        rollingRef.current = false;
        pendingDiceRef.current = null;
        diceOwnerSeatRef.current = snap.currentSeatIndex;
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
          flushSync(() => setListTokens(placed));
        }

        const ok = await runMoveAnimation(
          snap,
          moved.seat,
          moved.tokenIndex,
          diceValue as TDicevalues,
          // Already placed above — pass null to skip second snap
          null
        );
        if (cancelled) return;
        if (seq !== applySeqRef.current) return;
        lastAnimatedMoveSeqRef.current = moveSeq;
        pendingDiceRef.current = null;
        suppressMoveAnimRef.current = false;
        if (
          snap.currentSeatIndex !== moved.seat &&
          snap.phase === "AWAITING_ROLL"
        ) {
          await rafDelay(ONLINE_TURN_PASS_DELAY_MS, animCancelRef.current);
          if (cancelled) return;
        }
        if (ok && prev) {
          const colors = seatColorsFromSnapshot(snap);
          let captured = false;
          for (let s = 0; s < colors.length; s++) {
            if (s === moved.seat) continue;
            const color = colors[s];
            const a = prev.tokenPositions?.[color] || [];
            const b = snap.tokenPositions?.[color] || [];
            for (let i = 0; i < 4; i++) {
              if ((a[i] ?? -1) >= 0 && (b[i] ?? -1) === -1) {
                captured = true;
                break;
              }
            }
            if (captured) break;
          }
          if (captured) playSound("capture");
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
  }, [snapshot, mySeat, syncBoardFromSnapshot, runMoveAnimation]);

  const handleSelectDice = useCallback(
    (_diceValue?: TDicevalues) => {
      if (
        !canRequestOnlineRoll(snapshot, {
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
      playSound("diceRolling");
      beginMatchMusic();
      const nextActions = {
        ...actionsTurnRef.current,
        disabledDice: true,
        timerActivated: false,
      };
      actionsTurnRef.current = nextActions;
      setActionsTurn(nextActions);
      rollDice();
    },
    [snapshot, isBusy, mySeat, rollDice, isActionInFlight]
  );

  const handleSelectedToken = useCallback(
    async (select: ISelectTokenValues) => {
      if (!snapshot || isBusy || animatingRef.current) return;
      if (snapshot.currentSeatIndex !== mySeat) return;
      if (snapshot.phase !== "AWAITING_MOVE") return;

      const { tokenIndex, diceIndex } = select;
      const diceValue = (snapshot.diceList || [])[diceIndex] as TDicevalues;
      if (!diceValue) return;

      // Lock BEFORE moveToken so the MOVE snapshot cannot paint destination first
      animatingRef.current = true;
      setIsBusy(true);
      suppressMoveAnimRef.current = true;
      pendingDiceRef.current = {
        seat: mySeat,
        diceList: [...(snapshot.diceList || [])],
      };
      // Pre-move snapshot still has the start cell — never use post-move positions
      const colors = seatColorsFromSnapshot(snapshot);
      const startPos =
        snapshot.tokenPositions?.[colors[mySeat]]?.[tokenIndex] ?? null;
      moveToken(tokenIndex, diceIndex);
      const ok = await runMoveAnimation(
        snapshot,
        mySeat,
        tokenIndex,
        diceValue,
        startPos
      );
      suppressMoveAnimRef.current = false;
      // Apply any snapshots that arrived during the hop (sync only, no re-anim)
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
      } else if (ok && snapshot) {
        prevSnapRef.current = {
          ...(prevSnapRef.current || snapshot),
          actionSeq: (prevSnapRef.current?.actionSeq || 0) + 1,
        };
      }
    },
    [snapshot, isBusy, mySeat, runMoveAnimation, moveToken, syncBoardFromSnapshot]
  );

  const handleDoneDice = useCallback(() => {
    if (!snapshot) return;
    if (animatingRef.current) return;
    rollingRef.current = false;

    const live = snapshotRef.current ?? snapshot;
    const actions = actionsTurnRef.current as IActionsTurn & { rollId?: string };
    const rollId = buildRollDedupKey(
      live,
      actions.diceRollNumber,
      actions.diceValue as number
    );
    if (rollId && lastProcessedRollIdRef.current === rollId) return;
    lastProcessedRollIdRef.current = rollId;

    // Opponent/bot roll finished — refresh or hand off when server moved on
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

    // After dice spin, show selectable tokens for the current snap
    const canMove = shouldEnableTokenSelection(live, mySeat);
    const nextTokens = listTokensFromSnapshot(live, mySeat, canMove);
    setListTokens(nextTokens);
    listTokensRef.current = nextTokens;
    setActionsTurn((prev) => ({
      ...prev,
      ...actionsTurnFromSnapshot(live, mySeat, prev),
      diceValue: prev.diceValue,
      diceRollNumber: prev.diceRollNumber,
    }));

    // First 6 with all pawns still in jail → auto-exit one pawn (no click)
    if (!canMove || isBusy || animatingRef.current) return;
    const colors = seatColorsFromSnapshot(live);
    const color = colors[mySeat];
    const positions = (color && live.tokenPositions[color]) || [];
    const allInJail = positions.map((p) => p === -1);
    const diceValues = live.diceList || [];
    if (!shouldAutoExitJailOnFirstSix(allInJail, diceValues)) return;

    const legal =
      live.legalMoves?.length
        ? live.legalMoves
        : (live.legalTokenIndexes || []).map((tokenIndex) => ({
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
  }, [snapshot, mySeat, isBusy, handleSelectedToken, syncBoardFromSnapshot]);

  const resultEntries: IResultEntry[] = useMemo(
    () => buildResults(snapshot, guest.id, roomId),
    [snapshot, guest.id, roomId]
  );

  // 4-slot sparse view (BL/TL/TR/BR) with my house rotated to bottom-left.
  // ProfileSection skips empty slots; always render with the 4-player layout.
  const renderPlayers =
    players.length > 0
      ? players
      : snapshot
      ? playersForView(snapshot, mySeat, roomId)
      : [];

  const totalPlayers: TTotalPlayers = 4;

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
    return {
      rank: snapshot.standings![seat] || seat + 1,
      name: display,
      color: colors[seat],
      isBot: snapshot.isBot?.[seat],
      isYou: snapshot.userIds?.[seat] === myId,
    };
  });
}

export default React.memo(OnlineGame);
