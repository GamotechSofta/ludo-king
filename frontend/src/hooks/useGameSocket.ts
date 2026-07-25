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
/** Fast poll when WebSocket is down so opponent moves still feel live. */
const POLL_DISCONNECTED_MS = 500;
/** Rare safety poll while WS is healthy — avoid constant re-renders. */
const POLL_CONNECTED_MS = 4500;

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
  const clientRef = useRef<Client | null>(null);
  const connectedRef = useRef(false);
  const lastSigRef = useRef(
    initialSnapshot ? snapshotSig(initialSnapshot) : ""
  );
  const lastSeqRef = useRef(initialSnapshot?.actionSeq ?? 0);
  const lastWsAtRef = useRef(0);
  /** Ignore HTTP echo of an action we already applied from WS (or vice versa). */
  const inFlightActionRef = useRef(false);

  const applySnapshot = useCallback((snap: unknown, fromWs = false) => {
    const normalized = normalizeSnapshot(snap);
    if (!normalized) return false;
    const sig = snapshotSig(normalized);
    const nextSeq = normalized.actionSeq ?? 0;

    // Duplicate / unchanged state
    if (sig === lastSigRef.current) {
      if (fromWs) lastWsAtRef.current = Date.now();
      return true;
    }
    // Stale packet (older version)
    if (nextSeq < lastSeqRef.current) {
      return true;
    }
    // Stale poll while a live WS frame just arrived with same seq
    if (
      !fromWs &&
      nextSeq === lastSeqRef.current &&
      Date.now() - lastWsAtRef.current < 400
    ) {
      return true;
    }

    lastSigRef.current = sig;
    lastSeqRef.current = nextSeq;
    if (fromWs) lastWsAtRef.current = Date.now();
    onlinePerf.markSnapshotApplied(fromWs, nextSeq);
    setSnapshot(normalized);
    setLoadError("");
    inFlightActionRef.current = false;
    return true;
  }, []);

  // REST poll: primary when WS is down; light safety net when connected
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
        client.publish({
          destination: `/app/room/${roomId}/state`,
          headers: STOMP_JSON,
          body,
        });
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
      void client.deactivate();
      clientRef.current = null;
      connectedRef.current = false;
      setConnected(false);
    };
  }, [roomId, userId, applySnapshot]);

  /**
   * Single action path: WS when connected (room fan-out), else HTTP.
   * Never dual-submit — that would double-roll / double-move on the server.
   */
  const rollDice = useCallback(() => {
    if (!roomId || !userId) return;
    if (inFlightActionRef.current) return;
    inFlightActionRef.current = true;
    onlinePerf.markActionSent("roll");
    const seqAtSend = lastSeqRef.current;

    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: `/app/room/${roomId}/roll`,
        headers: STOMP_JSON,
        body: JSON.stringify({ userId }),
      });
      window.setTimeout(() => {
        if (lastSeqRef.current > seqAtSend) return;
        void httpRollDice(roomId, userId)
          .then((g) => applySnapshot(g, false))
          .catch(() => {
            inFlightActionRef.current = false;
          });
      }, 2000);
      return;
    }

    void httpRollDice(roomId, userId)
      .then((g) => applySnapshot(g, false))
      .catch(() => {
        inFlightActionRef.current = false;
      });
  }, [roomId, userId, applySnapshot]);

  const moveToken = useCallback(
    (tokenIndex: number, diceIndex: number) => {
      if (!roomId || !userId) return;
      onlinePerf.markActionSent("move");
      const seqAtSend = lastSeqRef.current;

      if (clientRef.current?.connected) {
        clientRef.current.publish({
          destination: `/app/room/${roomId}/move`,
          headers: STOMP_JSON,
          body: JSON.stringify({ userId, tokenIndex, diceIndex }),
        });
        window.setTimeout(() => {
          if (lastSeqRef.current > seqAtSend) return;
          void httpMoveToken(roomId, userId, tokenIndex, diceIndex)
            .then((g) => applySnapshot(g, false))
            .catch(() => undefined);
        }, 2000);
        return;
      }

      void httpMoveToken(roomId, userId, tokenIndex, diceIndex)
        .then((g) => applySnapshot(g, false))
        .catch(() => undefined);
    },
    [roomId, userId, applySnapshot]
  );

  return {
    snapshot,
    connected,
    loadError,
    rollDice,
    moveToken,
    setSnapshot: (s: unknown) => applySnapshot(s, false),
  };
}
