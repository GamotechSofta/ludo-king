import React, { useCallback, useEffect, useState } from "react";
import { createGuest, platformLaunch, queueMatch } from "../../api/ludoApi";
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

type ReadyCtx = {
  guest: IGuestUser;
  returnUrl?: string | null;
  walletEnabled: boolean;
  betOptions: number[];
  balance: number;
};

const DEFAULT_BETS = [10, 20, 50, 100];

/** Boots from Aakda WebView — pick players + bet, then queue. */
const PlatformLaunch = ({ query, onReady, onError }: Props) => {
  useWindowResize();
  const [phase, setPhase] = useState<"boot" | "pick" | "joining">("boot");
  const [message, setMessage] = useState("Starting Ludo…");
  const [balanceLabel, setBalanceLabel] = useState<string | null>(null);
  const [maxPlayers, setMaxPlayers] = useState<TPlayers>(query.players || 2);
  const [bet, setBet] = useState<number>(query.bet || 10);
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
        const betOptions =
          launched.betOptions && launched.betOptions.length > 0
            ? launched.betOptions
            : DEFAULT_BETS;
        const balance =
          typeof launched.balance === "number" ? launched.balance : null;

        const initialBet =
          query.bet && betOptions.includes(query.bet)
            ? query.bet
            : betOptions[0];
        setBet(initialBet);

        if (walletEnabled && balance != null) {
          setBalanceLabel(`Balance ₹${balance.toFixed(2)}`);
        }

        if (walletEnabled && balance == null && launched.balanceError) {
          onError(launched.balanceError || "Wallet busy, retry");
          return;
        }

        setMessage("Preparing player…");
        const resolvedName =
          (launched.displayName && launched.displayName.trim()) || displayName;
        await createGuest(resolvedName);
        if (cancelled) return;

        const guest: IGuestUser = {
          id: launched.userId,
          username: resolvedName,
          name: resolvedName,
          rating: 1000,
          avatarId: "1",
        };

        setCtx({
          guest,
          returnUrl: launched.returnUrl,
          walletEnabled,
          betOptions,
          balance: balance ?? 0,
        });
        setPhase("pick");
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
    if (ctx.walletEnabled && ctx.balance < bet) {
      onError(
        `Insufficient balance (₹${ctx.balance.toFixed(2)}). Need ₹${bet}.`
      );
      return;
    }
    setBusy(true);
    setPhase("joining");
    setMessage(
      ctx.walletEnabled
        ? `Joining ${maxPlayers}P · Bet ₹${bet}…`
        : `Finding ${maxPlayers}P match…`
    );
    try {
      const stakeTier = ctx.walletEnabled ? `BET_${bet}` : "FREE";
      const queued = await queueMatch(
        ctx.guest.id,
        ctx.guest.username,
        maxPlayers,
        stakeTier
      );
      onReady(ctx.guest, queued.roomId, queued.roomCode, ctx.returnUrl, {
        balance: ctx.balance,
        entryFee: bet,
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
  }, [ctx, busy, maxPlayers, bet, onReady, onError]);

  if (phase === "pick" && ctx) {
    return (
      <div
        className="lobby platform-fill"
        style={{
          ...fillStyle,
          justifyContent: "center",
        }}
      >
        <div className="lobby-top" style={{ width: "100%", maxWidth: 420 }}>
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

            <p className="lobby-footer-note" style={{ marginTop: 14 }}>
              Bet
            </p>
            <div className="player-count">
              {ctx.betOptions.map((amount) => {
                const tooPoor = ctx.walletEnabled && ctx.balance < amount;
                return (
                  <button
                    key={amount}
                    type="button"
                    className={bet === amount ? "active" : ""}
                    onClick={() => setBet(amount)}
                    disabled={busy || tooPoor}
                    title={tooPoor ? "Insufficient balance" : undefined}
                  >
                    ₹{amount}
                  </button>
                );
              })}
            </div>

            <button
              className="lobby-btn primary"
              type="button"
              disabled={
                busy || (ctx.walletEnabled && ctx.balance < bet)
              }
              onClick={() => void handlePlay()}
              style={{ marginTop: 12 }}
            >
              PLAY · {maxPlayers}P · ₹{bet}
            </button>
          </div>
        </div>
      </div>
    );
  }

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
