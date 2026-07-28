import { useCallback, useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { IGameSnapshot } from "../components/lobby/types";
import {
  ensureGameSnapshot,
  getApiBase,
  httpMoveToken,
  httpRollDice,
} from "../api/ludoApi";
import { onlinePerf } from "../utils/onlinePerf";

const STOMP_JSON = { "content-type": "application/json" };
const POLL_DISCONNECTED_MS = 800;
const POLL_CONNECTED_MS = 6000;

/** Compact server event (GameEvent) or legacy bare GameSnapshot. */
export interface IGameEvent {
  type?: string;
  actionSeq?: number;
  roomId?: string;
  seat?: number | null;
  tokenIndex?: number | null;
  dice?: number | null;
  from?: number | null;
  to?: number | null;
  phase?: string;
  currentSeatIndex?: number;
  diceList?: number[];
  tokenPositions?: Record<string, number[]>;
  seatColors?: string[];
  state?: IGameSnapshot;
  lastActionType?: string | null;
  turnStartedAt?: string;
  turnSecondsRemaining?: number;
  consecutiveSixes?: number;
  consecutiveTimeouts?: number[];
  eliminated?: boolean[];
  legalTokenIndexes?: number[];
  legalMoves?: Array<{ tokenIndex: number; diceIndex: number }>;
  finished?: boolean[];
  winnerSeat?: number | null;
  isBot?: boolean[];
  standings?: number[];
  userIds?: string[];
  usernames?: string[];
}

function normalizeSnapshot(raw: unknown): IGameSnapshot | null {
  if (!raw || typeof raw !== "object") return null;
  const snap = raw as IGameSnapshot & { tokenPositions?: Record<string, number[]> };
  if (!snap.tokenPositions || typeof snap.tokenPositions !== "object") {
    return null;
  }
  if (Object.keys(snap.tokenPositions).length === 0) {
    return null;
  }
  return snap;
}

/** Unwrap GameEvent → authoritative snapshot (prefer embedded state). */
function eventToSnapshot(raw: unknown): IGameSnapshot | null {
  if (!raw || typeof raw !== "object") return null;
  const ev = raw as IGameEvent;
  if (ev.state) {
    const state = normalizeSnapshot(ev.state);
    if (!state) return null;
    // Ensure from/to survive even if nested state omitted them
    return {
      ...state,
      isBot: state.isBot ?? ev.isBot,
      eliminated: state.eliminated ?? ev.eliminated,
      standings: state.standings ?? ev.standings,
      winnerSeat: state.winnerSeat ?? ev.winnerSeat ?? null,
      userIds: state.userIds?.length ? state.userIds : ev.userIds,
      usernames: state.usernames?.length ? state.usernames : ev.usernames,
      seatColors: state.seatColors?.length ? state.seatColors : ev.seatColors,
      lastActionFrom: state.lastActionFrom ?? ev.from ?? null,
      lastActionTo: state.lastActionTo ?? ev.to ?? null,
      lastActionType: state.lastActionType || ev.lastActionType || ev.type || null,
      lastActionSeat: state.lastActionSeat ?? ev.seat ?? null,
      lastActionTokenIndex: state.lastActionTokenIndex ?? ev.tokenIndex ?? null,
      lastActionDice: state.lastActionDice ?? ev.dice ?? null,
    };
  }
  // Bare snapshot (legacy) or compact event with tokenPositions
  if (ev.tokenPositions && Object.keys(ev.tokenPositions).length > 0) {
    const snap: IGameSnapshot = {
      roomId: ev.roomId || "",
      phase: ev.phase || "AWAITING_ROLL",
      currentSeatIndex: ev.currentSeatIndex ?? 0,
      currentColor: "",
      diceValue: ev.dice ?? 0,
      diceList: ev.diceList || [],
      tokenPositions: ev.tokenPositions,
      seatColors: ev.seatColors,
      legalTokenIndexes: ev.legalTokenIndexes || [],
      legalMoves: ev.legalMoves,
      finished: ev.finished,
      eliminated: ev.eliminated,
      winnerSeat: ev.winnerSeat,
      isBot: ev.isBot,
      standings: ev.standings,
      userIds: ev.userIds,
      usernames: ev.usernames,
      turnStartedAt: ev.turnStartedAt,
      turnSecondsRemaining: ev.turnSecondsRemaining,
      consecutiveSixes: ev.consecutiveSixes,
      consecutiveTimeouts: ev.consecutiveTimeouts,
      lastActionType: ev.lastActionType || ev.type || null,
      lastActionSeat: ev.seat,
      lastActionTokenIndex: ev.tokenIndex,
      lastActionDice: ev.dice,
      lastActionFrom: ev.from ?? null,
      lastActionTo: ev.to ?? null,
      actionSeq: ev.actionSeq,
    };
    return normalizeSnapshot(snap);
  }
  return normalizeSnapshot(raw);
}

function snapshotSig(snap: IGameSnapshot): string {
  return [
    snap.actionSeq ?? 0,
    snap.phase,
    snap.currentSeatIndex,
    snap.lastActionType || "",
    snap.lastActionSeat ?? "",
    snap.lastActionTokenIndex ?? "",
    snap.lastActionDice ?? "",
    (snap.diceList || []).join(","),
    Object.entries(snap.tokenPositions || {})
      .map(([c, p]) => `${c}:${(p || []).join(".")}`)
      .join("|"),
  ].join("#");
}

export function useGameSocket(
  roomId: string | null,
  userId: string | null,
  initialSnapshot?: IGameSnapshot | null
) {
  const [snapshot, setSnapshot] = useState<IGameSnapshot | null>(() =>
    normalizeSnapshot(initialSnapshot)
  );
  const [connected, setConnected] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [lastEvent, setLastEvent] = useState<IGameEvent | null>(null);
  const clientRef = useRef<Client | null>(null);
  const connectedRef = useRef(false);
  const lastSigRef = useRef(
    initialSnapshot ? snapshotSig(initialSnapshot) : ""
  );
  const lastSeqRef = useRef(initialSnapshot?.actionSeq ?? 0);
  const lastWsAtRef = useRef(0);
  const inFlightActionRef = useRef(false);
  const lastRollKeyRef = useRef("");
  const lastMoveKeyRef = useRef("");
  const isActionInFlight = useCallback(() => inFlightActionRef.current, []);
  const fallbackTimerRef = useRef<number | null>(null);
  const lastIdentityRef = useRef<{
    userIds?: string[];
    usernames?: string[];
    seatColors?: string[];
  }>({});

  const applySnapshot = useCallback((snap: unknown, fromWs = false) => {
    const event =
      snap && typeof snap === "object" && "type" in (snap as object)
        ? (snap as IGameEvent)
        : null;
    const normalized = eventToSnapshot(snap);
    if (!normalized) return false;

    const merged: IGameSnapshot = {
      ...normalized,
      userIds: normalized.userIds?.length
        ? normalized.userIds
        : lastIdentityRef.current.userIds,
      usernames: normalized.usernames?.length
        ? normalized.usernames
        : lastIdentityRef.current.usernames,
      seatColors: normalized.seatColors?.length
        ? normalized.seatColors
        : lastIdentityRef.current.seatColors,
    };
    if (merged.userIds?.length) {
      lastIdentityRef.current.userIds = merged.userIds;
    }
    if (merged.usernames?.length) {
      lastIdentityRef.current.usernames = merged.usernames;
    }
    if (merged.seatColors?.length) {
      lastIdentityRef.current.seatColors = merged.seatColors;
    }

    const sig = snapshotSig(merged);
    const nextSeq = merged.actionSeq ?? 0;

    if (sig === lastSigRef.current) {
      if (fromWs) lastWsAtRef.current = Date.now();
      return true;
    }
    // Strict ordering: ignore stale actionSeq
    if (nextSeq > 0 && nextSeq < lastSeqRef.current) {
      return true;
    }
    if (
      !fromWs &&
      nextSeq === lastSeqRef.current &&
      Date.now() - lastWsAtRef.current < 400
    ) {
      return true;
    }

    lastSigRef.current = sig;
    lastSeqRef.current = Math.max(lastSeqRef.current, nextSeq);
    if (fromWs) lastWsAtRef.current = Date.now();
    onlinePerf.markSnapshotApplied(fromWs, nextSeq);
    if (event) setLastEvent(event);
    setSnapshot(merged);
    setLoadError("");
    inFlightActionRef.current = false;
    lastRollKeyRef.current = "";
    lastMoveKeyRef.current = "";
    if (fallbackTimerRef.current != null) {
      window.clearTimeout(fallbackTimerRef.current);
      fallbackTimerRef.current = null;
    }
    return true;
  }, []);

  useEffect(() => {
    if (!roomId) return;
    lastIdentityRef.current = {
      userIds: initialSnapshot?.userIds,
      usernames: initialSnapshot?.usernames,
      seatColors: initialSnapshot?.seatColors,
    };
  }, [roomId, initialSnapshot?.userIds, initialSnapshot?.usernames, initialSnapshot?.seatColors]);

  useEffect(() => {
    if (!roomId) return;
    let alive = true;
    let timer: number | undefined;

    const pull = async () => {
      try {
        const game = await ensureGameSnapshot(roomId);
        if (!alive) return;
        if (!applySnapshot(game, false)) {
          setLoadError("Game state missing token positions");
        }
      } catch (e) {
        if (!alive) return;
        setLoadError(e instanceof Error ? e.message : "Failed to load game");
      }
      if (alive) {
        const delayMs = connectedRef.current
          ? POLL_CONNECTED_MS
          : POLL_DISCONNECTED_MS;
        timer = window.setTimeout(() => {
          void pull();
        }, delayMs);
      }
    };

    void pull();
    return () => {
      alive = false;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [roomId, applySnapshot]);

  useEffect(() => {
    if (!roomId || !userId) return;

    onlinePerf.startFpsProbe();

    const client = new Client({
      webSocketFactory: () => new SockJS(`${getApiBase()}/ws`) as WebSocket,
      reconnectDelay: 2000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        connectedRef.current = true;
        setConnected(true);
        client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
          try {
            applySnapshot(JSON.parse(msg.body), true);
          } catch {
            // ignore
          }
        });
        const body = JSON.stringify({ userId });
        client.publish({
          destination: `/app/room/${roomId}/join`,
          headers: STOMP_JSON,
          body,
        });
        // Authoritative resync after reconnect (human 2P recovery)
        void ensureGameSnapshot(roomId)
          .then((g) => applySnapshot(g, false))
          .catch(() => undefined);
      },
      onDisconnect: () => {
        connectedRef.current = false;
        setConnected(false);
      },
      onStompError: () => {
        connectedRef.current = false;
        setConnected(false);
      },
      onWebSocketError: () => {
        connectedRef.current = false;
        setConnected(false);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      onlinePerf.stopFpsProbe();
      if (fallbackTimerRef.current != null) {
        window.clearTimeout(fallbackTimerRef.current);
      }
      void client.deactivate();
      clientRef.current = null;
      connectedRef.current = false;
      setConnected(false);
    };
  }, [roomId, userId, applySnapshot]);

  const scheduleHttpFallback = useCallback(
    (fn: () => Promise<IGameSnapshot>, seqAtSend: number) => {
      if (fallbackTimerRef.current != null) {
        window.clearTimeout(fallbackTimerRef.current);
      }
      fallbackTimerRef.current = window.setTimeout(() => {
        fallbackTimerRef.current = null;
        if (lastSeqRef.current > seqAtSend) return;
        void fn()
          .then((g) => applySnapshot(g, false))
          .catch(() => {
            inFlightActionRef.current = false;
            if (!roomId) return;
            void ensureGameSnapshot(roomId)
              .then((g) => applySnapshot(g, false))
              .catch(() => undefined);
          });
      }, 2500);
      // Absolute safety: never leave roll/move gated forever
      window.setTimeout(() => {
        if (inFlightActionRef.current && lastSeqRef.current <= seqAtSend) {
          inFlightActionRef.current = false;
        }
      }, 8000);
    },
    [applySnapshot, roomId]
  );

  /** Primary: WebSocket. HTTP only if no newer actionSeq arrives. */
  const rollDice = useCallback(() => {
    if (!roomId || !userId) return;
    if (inFlightActionRef.current) return;
    const rollKey = `${lastSeqRef.current}|${roomId}|roll`;
    if (lastRollKeyRef.current === rollKey) return;
    lastRollKeyRef.current = rollKey;
    inFlightActionRef.current = true;
    onlinePerf.markActionSent("roll");
    const seqAtSend = lastSeqRef.current;

    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: `/app/room/${roomId}/roll`,
        headers: STOMP_JSON,
        body: JSON.stringify({ userId }),
      });
      scheduleHttpFallback(() => httpRollDice(roomId, userId), seqAtSend);
      return;
    }

    void httpRollDice(roomId, userId)
      .then((g) => applySnapshot(g, false))
      .catch(() => {
        inFlightActionRef.current = false;
      });
  }, [roomId, userId, applySnapshot, scheduleHttpFallback]);

  const moveToken = useCallback(
    (tokenIndex: number, diceIndex: number) => {
      if (!roomId || !userId) return;
      if (inFlightActionRef.current) return;
      const moveKey = `${lastSeqRef.current}|${roomId}|move|${tokenIndex}|${diceIndex}`;
      if (lastMoveKeyRef.current === moveKey) return;
      lastMoveKeyRef.current = moveKey;
      inFlightActionRef.current = true;
      onlinePerf.markActionSent("move");
      const seqAtSend = lastSeqRef.current;

      if (clientRef.current?.connected) {
        clientRef.current.publish({
          destination: `/app/room/${roomId}/move`,
          headers: STOMP_JSON,
          body: JSON.stringify({ userId, tokenIndex, diceIndex }),
        });
        scheduleHttpFallback(
          () => httpMoveToken(roomId, userId, tokenIndex, diceIndex),
          seqAtSend
        );
        return;
      }

      void httpMoveToken(roomId, userId, tokenIndex, diceIndex)
        .then((g) => applySnapshot(g, false))
        .catch(() => {
          inFlightActionRef.current = false;
        });
    },
    [roomId, userId, applySnapshot, scheduleHttpFallback]
  );

  return {
    snapshot,
    lastEvent,
    connected,
    loadError,
    rollDice,
    moveToken,
    isActionInFlight,
    setSnapshot: (s: unknown) => applySnapshot(s, false),
  };
}
