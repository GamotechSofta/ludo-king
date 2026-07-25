import React, { useEffect, useState } from "react";
import { createGuest, platformLaunch, queueMatch } from "../../api/ludoApi";
import type { IGuestUser } from "./types";

export interface PlatformQuery {
  userId: string;
  gameId: string;
  sessionId?: string;
  token?: string;
  returnUrl?: string;
}

interface Props {
  query: PlatformQuery;
  onReady: (
    guest: IGuestUser,
    roomId: string,
    roomCode: string,
    returnUrl?: string | null
  ) => void;
  onError: (message: string) => void;
}

/** Boots online match from Aakda WebView query params (no login screen). */
const PlatformLaunch = ({ query, onReady, onError }: Props) => {
  const [message, setMessage] = useState("Starting Ludo…");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        if (!query.userId?.trim()) {
          onError("Open this game from Aakda app");
          return;
        }
        setMessage("Connecting…");
        const displayName = "Player";
        const launched = await platformLaunch({
          userId: query.userId.trim(),
          gameId: query.gameId || "LUDO",
          sessionId: query.sessionId,
          token: query.token,
          returnUrl: query.returnUrl,
          displayName,
        });
        if (cancelled) return;

        setMessage("Preparing player…");
        // Ensure a guest profile exists; identity for the match is platform userId
        await createGuest(launched.displayName || displayName);
        if (cancelled) return;

        const guest: IGuestUser = {
          id: launched.userId,
          username: launched.displayName || displayName,
          name: launched.displayName || displayName,
          rating: 1000,
          avatarId: "1",
        };

        setMessage("Finding a match…");
        const queued = await queueMatch(guest.id, guest.username, 4, "FREE");
        if (cancelled) return;
        onReady(guest, queued.roomId, queued.roomCode, launched.returnUrl);
      } catch (e) {
        if (!cancelled) {
          onError(e instanceof Error ? e.message : "Failed to launch game");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [query, onReady, onError]);

  return (
    <div
      style={{
        minHeight: "100dvh",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        fontFamily: "Fredoka, sans-serif",
        textAlign: "center",
        background: "linear-gradient(160deg, #0b3d2e 0%, #145c43 45%, #0a2a20 100%)",
        color: "#f5fff8",
      }}
    >
      <p style={{ fontSize: 18, margin: 0 }}>{message}</p>
    </div>
  );
};

export default React.memo(PlatformLaunch);
