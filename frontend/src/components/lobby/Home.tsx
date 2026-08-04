import React, { useCallback, useState } from "react";
import { fetchGameHistory, type IGameHistoryItem } from "../../api/ludoApi";
import MusicToggle from "./MusicToggle";
import "./styles.css";

interface HomeProps {
  onPlay: () => void;
  userId?: string | null;
}

const formatMoney = (n: number) => {
  if (n == null || Number.isNaN(n)) return "0";
  return Number.isInteger(n) ? String(n) : n.toFixed(2);
};

const Home = ({ onPlay, userId }: HomeProps) => {
  const [showHistory, setShowHistory] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [games, setGames] = useState<IGameHistoryItem[]>([]);

  const openHistory = useCallback(async () => {
    setShowHistory(true);
    setError(null);
    if (!userId?.trim()) {
      setGames([]);
      setError("Sign in or play once to see your history.");
      return;
    }
    setLoading(true);
    try {
      const res = await fetchGameHistory(userId.trim());
      setGames(Array.isArray(res.games) ? res.games : []);
    } catch (e) {
      setGames([]);
      setError(e instanceof Error ? e.message : "Could not load history.");
    } finally {
      setLoading(false);
    }
  }, [userId]);

  return (
    <div className="lobby">
      <MusicToggle className="music-toggle-float" />
      <div className="lobby-top">
        <div className="lobby-brand">
          <div className="lobby-crown" aria-hidden />
          <h1 className="lobby-title">
            LUDO
          </h1>
          <p className="lobby-tagline">Roll · Race · Rule the board</p>
        </div>
        <div className="lobby-tokens" aria-hidden>
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
          <div className="lobby-token" />
        </div>
      </div>

      <div className="lobby-actions">
        <button className="lobby-btn primary" type="button" onClick={onPlay}>
          PLAY NOW
        </button>
        <button className="lobby-btn secondary" type="button" onClick={openHistory}>
          HISTORY
        </button>
        <p className="lobby-footer-note">Online matchmaking · smooth Ludo</p>
      </div>

      {showHistory && (
        <div className="history-overlay" role="dialog" aria-label="Game history">
          <div className="history-panel">
            <div className="history-panel-head">
              <h2>Game History</h2>
              <button
                className="history-close"
                type="button"
                onClick={() => setShowHistory(false)}
              >
                Close
              </button>
            </div>
            <div className="history-panel-body">
              {loading && <p className="history-empty">Loading…</p>}
              {!loading && error && <p className="history-empty">{error}</p>}
              {!loading && !error && games.length === 0 && (
                <p className="history-empty">No games played yet.</p>
              )}
              {!loading &&
                !error &&
                games.map((g) => (
                  <article
                    key={g.gameId}
                    className={`history-card ${g.result === "Win" ? "win" : "loss"}`}
                  >
                    <div className="history-card-top">
                      <span className="history-result">{g.result}</span>
                      <span className="history-when">
                        {g.gameDate} · {g.gameTime}
                      </span>
                    </div>
                    <p className="history-opponent">vs {g.opponentName}</p>
                    <p className="history-reason">{g.reason}</p>
                    <dl className="history-meta">
                      <div>
                        <dt>Game ID</dt>
                        <dd>{g.gameId}</dd>
                      </div>
                      <div>
                        <dt>Bet</dt>
                        <dd>{formatMoney(g.betAmount)}</dd>
                      </div>
                      <div>
                        <dt>Win</dt>
                        <dd>{formatMoney(g.winAmount)}</dd>
                      </div>
                    </dl>
                  </article>
                ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default React.memo(Home);
