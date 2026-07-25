import React, { useCallback, useEffect, useState } from "react";
import { createGuest, platformLaunch, queueMatch } from "../../api/ludoApi";
import type { IGuestUser } from "./types";
import "./styles.css";

export type TPlayers = 2 | 3 | 4;

export interface PlatformQuery {
  userId: string;
  gameId: string;
  sessionId?: string;
  token?: string;
  returnUrl?: string;
  /** Optional preselect from URL ?players=2|3|4 */
  players?: TPlayers;
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

type ReadyCtx = {
  guest: IGuestUser;
  returnUrl?: string | null;
  walletEnabled: boolean;
  entryFee: number;
  balance: number;
};

/** Boots from Aakda WebView — pick 2P/3P/4P then queue. */
const PlatformLaunch = ({ query, onReady, onError }: Props) => {
  const [phase, setPhase] = useState<"boot" | "pick" | "joining">("boot");
  const [message, setMessage] = useState("Starting Ludo…");
  const [balanceLabel, setBalanceLabel] = useState<string | null>(null);
  const [maxPlayers, setMaxPlayers] = useState<TPlayers>(query.players || 2);
  const [ctx, setCtx] = useState<ReadyCtx | null>(null);
  const [busy, setBusy] = useState(false);

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

        const walletEnabled = !!launched.walletEnabled;
        const entryFee = launched.entryFee ?? 0;
        const balance =
          typeof launched.balance === "number" ? launched.balance : null;

        if (walletEnabled && balance != null) {
          setBalanceLabel(`₹${balance.toFixed(2)} · Entry ₹${entryFee}`);
        }

        if (walletEnabled && balance != null && balance < entryFee) {
          onError(
            `Insufficient balance (₹${balance.toFixed(
              2
            )}). Need ₹${entryFee} to play.`
          );
          return;
        }
        if (walletEnabled && balance == null && launched.balanceError) {
          onError(launched.balanceError || "Wallet busy, retry");
          return;
        }

        setMessage("Preparing player…");
        await createGuest(launched.displayName || displayName);
        if (cancelled) return;

        const guest: IGuestUser = {
          id: launched.userId,
          username: launched.displayName || displayName,
          name: launched.displayName || displayName,
          rating: 1000,
          avatarId: "1",
        };

        setCtx({
          guest,
          returnUrl: launched.returnUrl,
          walletEnabled,
          entryFee,
          balance: balance ?? 0,
        });
        setPhase("pick");
        setMessage("Choose players");
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
  }, [query, onError]);

  const handlePlay = useCallback(async () => {
    if (!ctx || busy) return;
    setBusy(true);
    setPhase("joining");
    setMessage(
      ctx.walletEnabled
        ? `Joining ${maxPlayers}P (entry ₹${ctx.entryFee})…`
        : `Finding ${maxPlayers}P match…`
    );
    try {
      const queued = await queueMatch(
        ctx.guest.id,
        ctx.guest.username,
        maxPlayers,
        "FREE"
      );
      onReady(ctx.guest, queued.roomId, queued.roomCode, ctx.returnUrl, {
        balance: ctx.balance,
        entryFee: ctx.entryFee,
        walletEnabled: ctx.walletEnabled,
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Failed to join";
      if (/insufficient/i.test(msg)) onError("Insufficient balance");
      else if (/wallet/i.test(msg)) onError("Wallet busy, retry");
      else onError(msg);
      setPhase("pick");
      setBusy(false);
    }
  }, [ctx, busy, maxPlayers, onReady, onError]);

  if (phase === "pick" && ctx) {
    return (
      <div className="lobby">
        <div className="lobby-top" style={{ width: "100%" }}>
          <h2 className="lobby-heading">Ludo King</h2>
          <p className="lobby-sub">
            {ctx.guest.username}
            {balanceLabel ? ` · ${balanceLabel}` : ""}
          </p>

          <div className="lobby-panel">
            <p className="lobby-footer-note">Players</p>
            <div className="player-count">
              {([2, 3, 4] as TPlayers[]).map((count) => (
                <button
                  key={count}
                  type="button"
                  className={maxPlayers === count ? "active" : ""}
                  onClick={() => setMaxPlayers(count)}
                  disabled={busy}
                >
                  {count}P
                </button>
              ))}
            </div>

            <button
              className="lobby-btn primary"
              type="button"
              disabled={busy}
              onClick={() => void handlePlay()}
              style={{ marginTop: 12 }}
            >
              PLAY · {maxPlayers}P
              {ctx.walletEnabled && ctx.entryFee > 0
                ? ` · ₹${ctx.entryFee}`
                : ""}
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      style={{
        minHeight: "100dvh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: 24,
        fontFamily: "Fredoka, sans-serif",
        textAlign: "center",
        background:
          "linear-gradient(160deg, #0b3d2e 0%, #145c43 45%, #0a2a20 100%)",
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
