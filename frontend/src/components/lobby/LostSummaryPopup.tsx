import React from "react";
import type { IResultEntry } from "./types";
import CoinsWinIcon from "./CoinsWinIcon";
import { MAX_PLAYER_CHANCES } from "../../utils/constants";
import { lostStatusLabel } from "./resultHelpers";
import "./styles.css";

interface LostSummaryPopupProps {
  entries: IResultEntry[];
  entryAmount: number;
  isTwoPlayer?: boolean;
  /** Why the player was eliminated — timeout vs leaving the match. */
  exitReason?: "timeout" | "left";
  onExit: () => void;
  onWatch: () => void;
}

const LostSummaryPopup = ({
  entries,
  entryAmount,
  isTwoPlayer = false,
  exitReason = "timeout",
  onExit,
  onWatch,
}: LostSummaryPopupProps) => {
  const you = entries.find((e) => e.isYou);
  const others = entries.filter((e) => !e.isYou);
  const playingCount = entries.filter((e) => e.playing).length;
  const canWatch = !isTwoPlayer && playingCount > 0;

  const statusLabel = (entry: IResultEntry) => {
    if (entry.won || entry.rank === 1) return "Rank 1";
    if (entry.playing) return "Playing";
    return lostStatusLabel(entry);
  };

  return (
    <div
      className="lost-summary-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="lost-summary-title"
    >
      <div className="lost-summary-card">
        <div className="lost-summary-badge" aria-hidden>
          LOST
        </div>
        <h2 id="lost-summary-title" className="lost-summary-title">
          You&apos;re Out
        </h2>
        <p className="lost-summary-sub">
          {exitReason === "left"
            ? isTwoPlayer
              ? "You left the match — marked as LOST. Opponent wins."
              : "You left the match — marked as LOST."
            : isTwoPlayer
              ? `${MAX_PLAYER_CHANCES} turn timeouts — match ended. Opponent wins.`
              : `${MAX_PLAYER_CHANCES} turn timeouts used — you have been eliminated.`}
        </p>

        {entryAmount > 0 && (
          <div className="lost-summary-stat-row">
            <span className="lost-summary-stat-label">Entry fee</span>
            <span className="lost-summary-stat-value lost-summary-stat-negative">
              <CoinsWinIcon />
              -{entryAmount}
            </span>
          </div>
        )}
        <div className="lost-summary-stat-row">
          <span className="lost-summary-stat-label">Your result</span>
          <span className="lost-summary-stat-value lost-summary-stat-negative">
            LOST
          </span>
        </div>
        {canWatch && (
          <div className="lost-summary-stat-row">
            <span className="lost-summary-stat-label">Match status</span>
            <span className="lost-summary-stat-value">
              {playingCount} player{playingCount === 1 ? "" : "s"} still playing
            </span>
          </div>
        )}
        {isTwoPlayer && (
          <div className="lost-summary-stat-row">
            <span className="lost-summary-stat-label">Match status</span>
            <span className="lost-summary-stat-value">Ended</span>
          </div>
        )}

        <div className="lost-summary-players">
          <h3 className="lost-summary-players-title">Match Summary</h3>
          <ol className="lost-summary-list">
            {you && (
              <li className="lost-summary-row you lost">
                {you.color && (
                  <div className={`player-swatch ${you.color.toLowerCase()}`} />
                )}
                <span className="lost-summary-name">
                  {you.name}
                  <small>You</small>
                </span>
                <span className="lost-summary-status lost">LOST</span>
              </li>
            )}
            {others.map((entry) => (
              <li
                className={`lost-summary-row${entry.lost ? " lost" : ""}${
                  entry.playing ? " playing" : ""
                }`}
                key={`${entry.name}-${entry.color}`}
              >
                {entry.color && (
                  <div
                    className={`player-swatch ${entry.color.toLowerCase()}`}
                  />
                )}
                <span className="lost-summary-name">{entry.name}</span>
                <span
                  className={`lost-summary-status${
                    entry.lost ? " lost" : entry.playing ? " playing" : ""
                  }`}
                >
                  {statusLabel(entry)}
                </span>
              </li>
            ))}
          </ol>
        </div>

        <div className="lost-summary-actions">
          {canWatch && (
            <button
              className="lobby-btn secondary lost-summary-btn"
              type="button"
              onClick={onWatch}
            >
              Watch Match
            </button>
          )}
          <button
            className="lobby-btn primary lost-summary-btn"
            type="button"
            onClick={onExit}
          >
            {isTwoPlayer ? "View Results" : "Exit to Home"}
          </button>
        </div>
      </div>
    </div>
  );
};

export default React.memo(LostSummaryPopup);
