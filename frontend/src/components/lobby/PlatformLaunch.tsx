import React, { useEffect, useRef, useState } from "react";
import { platformLaunch, queueMatch } from "../../api/ludoApi";
import { useWindowResize } from "../../hooks";
import type { IGuestUser } from "./types";
import "./styles.css";

const fillStyle: React.CSSProperties = {
  minHeight: "var(--vv-height, 100dvh)",
  height: "var(--vv-height, 100dvh)",
  width: "100%",
  boxSizing: "border-box",
  backgroundColor: "#0a2a5c",
  backgroundImage: "var(--bg-image)",
  backgroundSize: "cover",
  backgroundPosition: "center center",
  backgroundRepeat: "no-repeat",
};

export type TPlayers = 2 | 3 | 4;

export interface PlatformQuery {
  userId: string;
  gameId: string;
  sessionId?: string;
  token?: string;
  returnUrl?: string;
  players?: TPlayers;
  /** Optional preselect ?bet=20 */
  bet?: number;
  /** Aakda / launch query: displayName | name | username */
  displayName?: string;
}

interface Props {
  query: PlatformQuery;
  onReady: (
    guest: IGuestUser,
    roomId: string,
    roomCode: string,
    returnUrl?: string | null,
    wallet?: { balance: number; entryFee: number; walletEnabled: boolean }
  ) => void;
  onError: (message: string) => void;
}

const DEFAULT_PLAYERS: TPlayers = 2;
const FIXED_ENTRY_FEE = 100;

/** Boots from Aakda WebView and queues straight into a match — no setup screen. */
const PlatformLaunch = ({ query, onReady, onError }: Props) => {
  useWindowResize();
  const [message, setMessage] = useState("Starting Ludo…");
  const [balanceLabel, setBalanceLabel] = useState<string | null>(null);
  /** Queueing debits the wallet, so it must never run twice for one launch. */
  const launchedRef = useRef(false);

  useEffect(() => {
    if (launchedRef.current) return;
    launchedRef.current = true;

    let cancelled = false;
    (async () => {
      try {
        if (!query.userId?.trim()) {
          onError("Open this game from Aakda app");
          return;
        }
        setMessage("Connecting…");
        const displayName =
          (query.displayName && query.displayName.trim()) || "Player";
        const launched = await platformLaunch({
          userId: query.userId.trim(),
          gameId: query.gameId || "LUDO",
          sessionId: query.sessionId,
          token: query.token,
          returnUrl: query.returnUrl,
          displayName,
        });
        if (cancelled) return;

        const walletEnabled = !!launched.walletEnabled;
        const balance =
          typeof launched.balance === "number" ? launched.balance : null;

        if (!walletEnabled) {
          onError("Wallet unavailable, retry");
          return;
        }

        if (balance != null) {
          setBalanceLabel(`Balance ₹${balance.toFixed(2)}`);
        }

        if (balance == null) {
          onError(launched.balanceError || "Wallet busy, retry");
          return;
        }

        // This game has one fixed real-money stake, matching the reference.
        // Ignore URL-provided bets so a client cannot lower the entry fee.
        const bet = FIXED_ENTRY_FEE;
        const maxPlayers = query.players || DEFAULT_PLAYERS;

        if (balance < bet) {
          onError(
            `Insufficient balance (₹${(balance ?? 0).toFixed(
              2
            )}). Need ₹${bet}.`
          );
          return;
        }

        const resolvedName =
          (launched.displayName && launched.displayName.trim()) || displayName;

        // platformLaunch already upserts this identity under the authoritative
        // Aakda userId. Creating a second guest here produced an orphan profile.
        const guest: IGuestUser = {
          id: launched.userId,
          username: resolvedName,
          name: resolvedName,
          rating: 1000,
          avatarId: "1",
        };

        setMessage(
          walletEnabled
            ? `Joining ${maxPlayers}P · Bet ₹${bet}…`
            : `Finding ${maxPlayers}P match…`
        );

        const stakeTier = `BET_${bet}`;
        const queued = await queueMatch(
          guest.id,
          guest.username,
          maxPlayers,
          stakeTier
        );
        if (cancelled) return;

        onReady(guest, queued.roomId, queued.roomCode, launched.returnUrl, {
          balance: balance ?? 0,
          entryFee: bet,
          walletEnabled,
        });
      } catch (e) {
        if (!cancelled) {
          const msg = e instanceof Error ? e.message : "Failed to launch game";
          if (/insufficient/i.test(msg)) onError("Insufficient balance");
          else if (/wallet/i.test(msg)) onError("Wallet busy, retry");
          else onError(msg);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [query, onReady, onError]);

  return (
    <div
      className="platform-fill"
      style={{
        ...fillStyle,
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        fontFamily: "Fredoka, sans-serif",
        textAlign: "center",
        color: "#f5fff8",
        gap: 12,
      }}
    >
      {balanceLabel && (
        <p style={{ margin: 0, fontSize: 14, opacity: 0.9 }}>{balanceLabel}</p>
      )}
      <p style={{ fontSize: 18, margin: 0 }}>{message}</p>
    </div>
  );
};

export default React.memo(PlatformLaunch);
