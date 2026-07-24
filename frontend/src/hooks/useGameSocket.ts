import { useCallback, useEffect, useRef, useState } from "react";
import { Client, IMessage } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type { IGameSnapshot } from "../components/lobby/types";
import { getApiBase } from "../api/ludoApi";

export function useGameSocket(roomId: string | null, userId: string | null) {
  const [snapshot, setSnapshot] = useState<IGameSnapshot | null>(null);
  const [connected, setConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);

  useEffect(() => {
    if (!roomId || !userId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(`${getApiBase()}/ws`) as WebSocket,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true);
        client.subscribe(`/topic/room/${roomId}`, (msg: IMessage) => {
          try {
            setSnapshot(JSON.parse(msg.body));
          } catch {
            // ignore
          }
        });
        client.publish({
          destination: `/app/room/${roomId}/join`,
          body: JSON.stringify({ userId }),
        });
        client.publish({
          destination: `/app/room/${roomId}/state`,
          body: JSON.stringify({ userId }),
        });
      },
      onDisconnect: () => setConnected(false),
      onStompError: () => setConnected(false),
    });

    clientRef.current = client;
    client.activate();

    return () => {
      void client.deactivate();
      clientRef.current = null;
    };
  }, [roomId, userId]);

  const rollDice = useCallback(() => {
    if (!clientRef.current || !roomId || !userId) return;
    clientRef.current.publish({
      destination: `/app/room/${roomId}/roll`,
      body: JSON.stringify({ userId }),
    });
  }, [roomId, userId]);

  const moveToken = useCallback(
    (tokenIndex: number, diceIndex: number) => {
      if (!clientRef.current || !roomId || !userId) return;
      clientRef.current.publish({
        destination: `/app/room/${roomId}/move`,
        body: JSON.stringify({ userId, tokenIndex, diceIndex }),
      });
    },
    [roomId, userId]
  );

  return { snapshot, connected, rollDice, moveToken, setSnapshot };
}
