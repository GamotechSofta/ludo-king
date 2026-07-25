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

const STOMP_JSON = { "content-type": "application/json" };
/** Fast poll when WebSocket is down so opponent moves still feel live. */
const POLL_DISCONNECTED_MS = 400;
/** Slow safety poll when WS is connected (WS is primary). */
const POLL_CONNECTED_MS = 4000;

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
  const positions = Object.entries(snap.tokenPositions || {})
    .map(([c, p]) => `${c}:${(p || []).join(".")}`)
    .join("|");
  return [
    snap.actionSeq ?? 0,
    snap.phase,
    snap.currentSeatIndex,
    snap.lastActionType || "",
    (snap.diceList || []).join(","),
    snap.turnStartedAt || "",
    positions,
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
  const lastWsAtRef = useRef(0);

  const applySnapshot = useCallback((snap: unknown, fromWs = false) => {
    const normalized = normalizeSnapshot(snap);
    if (!normalized) return false;
    const sig = snapshotSig(normalized);
    // Ignore duplicate / out-of-order poll payloads right after a live WS push
    if (sig === lastSigRef.current) {
      if (fromWs) lastWsAtRef.current = Date.now();
      return true;
    }
    if (!fromWs && Date.now() - lastWsAtRef.current < 800) {
      return true;
    }
    lastSigRef.current = sig;
    if (fromWs) lastWsAtRef.current = Date.now();
    setSnapshot(normalized);
    setLoadError("");
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
      void client.deactivate();
      clientRef.current = null;
      connectedRef.current = false;
      setConnected(false);
    };
  }, [roomId, userId, applySnapshot]);

  const rollDice = useCallback(() => {
    if (!roomId || !userId) return;
    if (clientRef.current?.connected) {
      clientRef.current.publish({
        destination: `/app/room/${roomId}/roll`,
        headers: STOMP_JSON,
        body: JSON.stringify({ userId }),
      });
      return;
    }
    void httpRollDice(roomId, userId)
      .then((g) => applySnapshot(g, false))
      .catch(() => undefined);
  }, [roomId, userId, applySnapshot]);

  const moveToken = useCallback(
    (tokenIndex: number, diceIndex: number) => {
      if (!roomId || !userId) return;
      if (clientRef.current?.connected) {
        clientRef.current.publish({
          destination: `/app/room/${roomId}/move`,
          headers: STOMP_JSON,
          body: JSON.stringify({ userId, tokenIndex, diceIndex }),
        });
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
