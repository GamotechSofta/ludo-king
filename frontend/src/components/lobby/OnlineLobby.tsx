import React, { useCallback, useEffect, useMemo, useState } from "react";
import { getRoomState, leaveRoom, markRoomReady } from "../../api/ludoApi";
import type { IGameSnapshot, IGuestUser, IOnlineRoom } from "./types";
import "./styles.css";

interface OnlineLobbyProps {
  guest: IGuestUser;
  roomId: string;
  roomCode: string;
  walletBalance?: number | null;
  entryFee?: number;
  onBack: () => void;
  onStart: (room: IOnlineRoom, game?: IGameSnapshot | null) => void;
}

/**
 * Matchmaking lobby only — ready / countdown / seat list.
 * Does not touch board, dice, or pawn UI.
 */
const OnlineLobby = ({
  guest,
  roomId,
  roomCode,
  walletBalance,
  entryFee,
  onBack,
  onStart,
}: OnlineLobbyProps) => {
  const [room, setRoom] = useState<IOnlineRoom | null>(null);
  const [countdown, setCountdown] = useState<number | "GO" | null>(null);
  const [error, setError] = useState("");
  const [readyBusy, setReadyBusy] = useState(false);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(t);
  }, []);

  useEffect(() => {
    let alive = true;
    let started = false;
    const tick = async () => {
      try {
        const state = await getRoomState(roomId);
        if (!alive) return;
        setRoom(state.room);
        if (typeof state.countdown === "number") {
          setCountdown(state.countdown <= 0 ? "GO" : state.countdown);
        } else if (state.room.countdownValue != null) {
          setCountdown(
            state.room.countdownValue <= 0 ? "GO" : state.room.countdownValue
          );
        }
        if (
          (state.room.status === "IN_PROGRESS" ||
            state.displayStatus === "PLAYING") &&
          !started
        ) {
          started = true;
          onStart(state.room, state.game ?? null);
        }
      } catch (e) {
        if (alive) {
          setError(e instanceof Error ? e.message : "Failed to load room");
        }
      }
    };
    void tick();
    const id = window.setInterval(tick, 800);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, [roomId, onStart]);

  const secondsLeft = useMemo(() => {
    if (!room?.fillDeadlineAt || room.status !== "WAITING") return null;
    const ms = new Date(room.fillDeadlineAt).getTime() - now;
    return Math.max(0, Math.ceil(ms / 1000));
  }, [room?.fillDeadlineAt, room?.status, now]);

  const seats = room?.maxPlayers || 4;
  const filled = room?.players?.length || 0;
  const me = room?.players?.find((p) => p.userId === guest.id);
  const isReadyPhase = room?.status === "READY";
  const iAmReady = !!me?.ready;

  const handleReady = useCallback(async () => {
    if (readyBusy || iAmReady) return;
    setReadyBusy(true);
    setError("");
    try {
      const updated = await markRoomReady(roomId, guest.id);
      setRoom(updated);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not ready up");
    } finally {
      setReadyBusy(false);
    }
  }, [readyBusy, iAmReady, roomId, guest.id]);

  const handleBack = useCallback(() => {
    void leaveRoom(roomId, guest.id).catch(() => undefined);
    onBack();
  }, [roomId, guest.id, onBack]);

  const title =
    countdown != null
      ? countdown === "GO"
        ? "GO!"
        : String(countdown)
      : isReadyPhase
      ? "Waiting for Players…"
      : "Finding Players";

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <button className="lobby-back" type="button" onClick={handleBack}>
          ← Back
        </button>
        <h2 className="lobby-heading">{title}</h2>
        <p className="lobby-sub">
          Room <strong>{roomCode}</strong> · {guest.username}
          {walletBalance != null
            ? ` · ₹${walletBalance.toFixed(2)}${
                entryFee ? ` (entry ₹${entryFee})` : ""
              }`
            : ""}
        </p>

        <div className="lobby-tokens" aria-hidden>
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
        </div>

        <div className="lobby-panel">
          <p className="lobby-footer-note" style={{ marginBottom: 12 }}>
            {filled} / {seats} Players Joined
            {room?.status === "WAITING" && secondsLeft != null
              ? ` · Searching ${secondsLeft}s`
              : ""}
            {isReadyPhase
              ? ` · Ready ${
                  room?.players?.filter((p) => p.bot || p.ready).length || 0
                }/${seats}`
              : ""}
          </p>

          {Array.from({ length: seats }).map((_, i) => {
            const p = room?.players?.find((x) => x.seatIndex === i);
            return (
              <div className="player-row" key={i}>
                <div
                  className={`player-swatch ${
                    p ? p.color.toLowerCase() : "blue"
                  }`}
                  style={{ opacity: p ? 1 : 0.25 }}
                />
                <span style={{ flex: 1, fontWeight: 600 }}>
                  {p
                    ? `${p.username}${
                        p.userId === guest.id ? " · You" : ""
                      }${p.ready || p.bot ? " ✓" : ""}`
                    : "Waiting…"}
                </span>
              </div>
            );
          })}

          {isReadyPhase && !iAmReady && (
            <button
              className="lobby-btn primary"
              type="button"
              disabled={readyBusy}
              onClick={() => void handleReady()}
              style={{ marginTop: 14 }}
            >
              {readyBusy ? "…" : "READY"}
            </button>
          )}

          {isReadyPhase && iAmReady && countdown == null && (
            <p className="lobby-footer-note" style={{ marginTop: 12 }}>
              You are ready — waiting for others…
            </p>
          )}

          {room?.status === "WAITING" && (
            <p className="lobby-footer-note">
              Share code <strong>{roomCode}</strong> with friends.
            </p>
          )}
          {error && (
            <p className="lobby-footer-note" style={{ color: "#ffd0d0" }}>
              {error}
            </p>
          )}
        </div>
      </div>
    </div>
  );
};

export default React.memo(OnlineLobby);
