import React, { useEffect, useMemo, useState } from "react";
import { getRoomState } from "../../api/ludoApi";
import type { IGuestUser, IOnlineRoom } from "./types";
import "./styles.css";

interface OnlineLobbyProps {
  guest: IGuestUser;
  roomId: string;
  roomCode: string;
  onBack: () => void;
  onStart: (room: IOnlineRoom) => void;
}

const OnlineLobby = ({
  guest,
  roomId,
  roomCode,
  onBack,
  onStart,
}: OnlineLobbyProps) => {
  const [room, setRoom] = useState<IOnlineRoom | null>(null);
  const [error, setError] = useState("");
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
        if (state.room.status === "IN_PROGRESS" && !started) {
          started = true;
          onStart(state.room);
        }
      } catch (e) {
        if (alive) {
          setError(e instanceof Error ? e.message : "Failed to load room");
        }
      }
    };
    void tick();
    const id = window.setInterval(tick, 1200);
    return () => {
      alive = false;
      window.clearInterval(id);
    };
  }, [roomId, onStart]);

  const secondsLeft = useMemo(() => {
    if (!room?.fillDeadlineAt) return null;
    const ms = new Date(room.fillDeadlineAt).getTime() - now;
    return Math.max(0, Math.ceil(ms / 1000));
  }, [room?.fillDeadlineAt, now]);

  const seats = room?.maxPlayers || 4;
  const filled = room?.players?.length || 0;

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <button className="lobby-back" type="button" onClick={onBack}>
          ← Back
        </button>
        <h2 className="lobby-heading">Finding Players</h2>
        <p className="lobby-sub">
          Room <strong>{roomCode}</strong> · {guest.username}
        </p>

        <div className="lobby-tokens" aria-hidden>
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
        </div>

        <div className="lobby-panel">
          <p className="lobby-footer-note" style={{ marginBottom: 12 }}>
            Seats {filled}/{seats}
            {secondsLeft !== null ? ` · Bots in ${secondsLeft}s` : ""}
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
                    ? `${p.username}${p.bot ? " (Bot)" : ""}${
                        p.userId === guest.id ? " · You" : ""
                      }`
                    : "Waiting…"}
                </span>
              </div>
            );
          })}

          <p className="lobby-footer-note">
            Share code <strong>{roomCode}</strong> with friends, or wait for
            auto bot-fill.
          </p>
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
