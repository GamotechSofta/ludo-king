import React, { useState } from "react";
import type { IGuestUser } from "./types";
import { createGuest, createPrivateRoom, joinRoom, queueMatch } from "../../api/ludoApi";
import "./styles.css";

type TPlayers = 2 | 3 | 4;

interface OnlineSetupProps {
  onBack: () => void;
  onQueued: (
    guest: IGuestUser,
    roomId: string,
    roomCode: string,
    maxPlayers: TPlayers
  ) => void;
}

const OnlineSetup = ({ onBack, onQueued }: OnlineSetupProps) => {
  const [name, setName] = useState("Player");
  const [maxPlayers, setMaxPlayers] = useState<TPlayers>(4);
  const [roomCode, setRoomCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const ensureGuest = async () => {
    const guest = await createGuest(name.trim() || "Player");
    localStorage.setItem("ludoGuest", JSON.stringify(guest));
    return guest;
  };

  const handleQuickMatch = async () => {
    setBusy(true);
    setError("");
    try {
      const guest = await ensureGuest();
      const res = await queueMatch(guest.id, guest.username, maxPlayers);
      if (!res.roomId) throw new Error("No room returned");
      onQueued(guest, res.roomId, res.roomCode, maxPlayers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Matchmaking failed");
    } finally {
      setBusy(false);
    }
  };

  const handlePrivate = async () => {
    setBusy(true);
    setError("");
    try {
      const guest = await ensureGuest();
      const room = await createPrivateRoom(guest.id, guest.username, maxPlayers);
      onQueued(guest, room.id, room.roomCode, maxPlayers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not create room");
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = async () => {
    setBusy(true);
    setError("");
    try {
      const guest = await ensureGuest();
      const room = await joinRoom(
        roomCode.trim().toUpperCase(),
        guest.id,
        guest.username
      );
      onQueued(guest, room.id, room.roomCode, room.maxPlayers as TPlayers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not join room");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <button className="lobby-back" type="button" onClick={onBack}>
          ← Back
        </button>
        <h2 className="lobby-heading">Online Play</h2>
        <p className="lobby-sub">Match with players or bots in realtime</p>

        <div className="lobby-panel">
          <div className="player-row" style={{ marginBottom: 12 }}>
            <input
              value={name}
              maxLength={12}
              onChange={(e) => setName(e.target.value)}
              placeholder="Your name"
              aria-label="Your name"
            />
          </div>

          <div className="player-count">
            {([2, 3, 4] as TPlayers[]).map((count) => (
              <button
                key={count}
                type="button"
                className={maxPlayers === count ? "active" : ""}
                onClick={() => setMaxPlayers(count)}
              >
                {count}P
              </button>
            ))}
          </div>

          <button
            className="lobby-btn primary"
            type="button"
            disabled={busy}
            onClick={handleQuickMatch}
          >
            QUICK MATCH
          </button>

          <button
            className="lobby-btn secondary"
            type="button"
            disabled={busy}
            onClick={handlePrivate}
            style={{ marginTop: 10 }}
          >
            CREATE PRIVATE ROOM
          </button>

          <div className="player-row" style={{ marginTop: 14 }}>
            <input
              value={roomCode}
              maxLength={6}
              onChange={(e) => setRoomCode(e.target.value.toUpperCase())}
              placeholder="ROOM CODE"
              aria-label="Room code"
            />
            <button
              type="button"
              className="bot-toggle on"
              disabled={busy || roomCode.length < 4}
              onClick={handleJoin}
            >
              JOIN
            </button>
          </div>

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

export default React.memo(OnlineSetup);
