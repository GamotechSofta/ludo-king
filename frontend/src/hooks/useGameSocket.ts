import { useCallback, useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { IGameSnapshot } from "../components/lobby/types";
import {
  getApiBase,
  getRoomState,
  httpMoveToken,
  httpRollDice,
} from "../api/ludoApi";

const STOMP_JSON = { "content-type": "application/json" };

export function useGameSocket(roomId: string | null, userId: string | null) {
  const [snapshot, setSnapshot] = useState<IGameSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  const applySnapshot = useCallback((snap: IGameSnapshot | null | undefined) => {
    if (!snap || !snap.tokenPositions) return;
    setSnapshot(snap);
  }, []);

  // REST bootstrap + poll (works even if STOMP/SockJS fails)
  useEffect(() => {
    if (!roomId) return;
    let alive = true;

    const pull = async () => {
      try {
        const state = await getRoomState(roomId);
        if (!alive) return;
        if (state.game) {
          applySnapshot(state.game);
        }
      } catch {
        // ignore transient errors
      }
    };

    void pull();
    const id = window.setInterval(pull, 2000);
    return () => {
      alive = false;
      window.clearInterval(id);
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
            applySnapshot(JSON.parse(msg.body) as IGameSnapshot);
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

  return { snapshot, connected, rollDice, moveToken, setSnapshot: applySnapshot };
}
