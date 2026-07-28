import React, { useState } from "react";
import type { IGuestUser } from "./types";
import { createGuest, queueMatch } from "../../api/ludoApi";
import CoinsWinIcon from "./CoinsWinIcon";
import "./styles.css";

type TPlayers = 2 | 4;

const ENTRY_AMOUNT = 100;
const WIN_BY_PLAYERS: Record<TPlayers, number> = { 2: 180, 4: 320 };

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
  const [maxPlayers, setMaxPlayers] = useState<TPlayers>(4);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const winAmount = WIN_BY_PLAYERS[maxPlayers];

  const ensureGuest = async () => {
    const guest = await createGuest("Player");
    localStorage.setItem("ludoGuest", JSON.stringify(guest));
    return guest;
  };

  const handleQuickMatch = async () => {
    setBusy(true);
    setError("");
    try {
      const guest = await ensureGuest();
      const res = await queueMatch(guest.id, guest.username, maxPlayers, "FREE", {
        rating: guest.rating,
        region: "IN",
        avatarId: guest.avatarId,
      });
      if (!res.roomId) throw new Error("No room returned");
      onQueued(guest, res.roomId, res.roomCode, maxPlayers);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Matchmaking failed");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="lobby">
      <div className="find-match">
      <button className="find-match-back" type="button" onClick={onBack} aria-label="Back">
        ←
      </button>

      <section className="find-match-card find-match-card-players">
        <h3 className="find-match-title">SELECT PLAYERS</h3>
        <div className="find-match-players">
          <label
            className={`find-match-player-row ${
              maxPlayers === 2 ? "selected" : ""
            }`}
          >
            <input
              type="radio"
              name="players"
              className="find-match-player-input"
              checked={maxPlayers === 2}
              disabled={busy}
              onChange={() => setMaxPlayers(2)}
            />
            <span className="find-match-player-radio" aria-hidden="true">
              {maxPlayers === 2 ? (
                <span className="find-match-player-check">✓</span>
              ) : null}
            </span>
            <span className="find-match-player-text">2 PLAYERS</span>
          </label>
          <label
            className={`find-match-player-row ${
              maxPlayers === 4 ? "selected" : ""
            }`}
          >
            <input
              type="radio"
              name="players"
              className="find-match-player-input"
              checked={maxPlayers === 4}
              disabled={busy}
              onChange={() => setMaxPlayers(4)}
            />
            <span className="find-match-player-radio" aria-hidden="true">
              {maxPlayers === 4 ? (
                <span className="find-match-player-check">✓</span>
              ) : null}
            </span>
            <span className="find-match-player-text">4 PLAYERS</span>
          </label>
        </div>
      </section>

      <section className="find-match-card find-match-card-game">
        <h3 className="find-match-title">SELECT GAME</h3>
        <div className="find-match-game-body">
          <div className="find-match-stake-box">
            <div className="find-match-win">
              <CoinsWinIcon />
              <div className="find-match-win-meta">
                <span className="find-match-win-label">WIN</span>
                <span className="find-match-win-value">
                  {winAmount.toLocaleString()}
                </span>
              </div>
            </div>
            <div className="find-match-entry">
              Entry: {ENTRY_AMOUNT.toLocaleString()}
            </div>
          </div>
          <button
            type="button"
            className="find-match-play"
            disabled={busy}
            onClick={() => void handleQuickMatch()}
          >
            Play
          </button>
        </div>
      </section>

      {error ? <p className="find-match-error">{error}</p> : null}
      </div>
    </div>
  );
};

export default React.memo(OnlineSetup);
