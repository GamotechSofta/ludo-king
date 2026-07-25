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

  const applySnapshot = useCallback((snap: unknown) => {
    const normalized = normalizeSnapshot(snap);
    if (!normalized) return false;
    setSnapshot(normalized);
    setLoadError("");
    return true;
  }, []);

  // REST: keep pulling until we have a board (and keep polling for bot turns without WS)
  useEffect(() => {
    if (!roomId) return;
    let alive = true;
    let timer: number | undefined;
    let delayMs = 400;

    const pull = async () => {
      try {
        const game = await ensureGameSnapshot(roomId);
        if (!alive) return;
        if (!applySnapshot(game)) {
          setLoadError("Game state missing token positions");
        }
        delayMs = 1500;
      } catch (e) {
        if (!alive) return;
        setLoadError(e instanceof Error ? e.message : "Failed to load game");
        delayMs = 2000;
      }
      if (alive) {
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
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
          try {
            applySnapshot(JSON.parse(msg.body));
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
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
      onWebSocketError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      void client.deactivate();
      clientRef.current = null;
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
      .then(applySnapshot)
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
        .then(applySnapshot)
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
    setSnapshot: applySnapshot,
  };
}
