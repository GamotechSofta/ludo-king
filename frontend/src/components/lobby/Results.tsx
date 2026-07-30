import React from "react";
import type { IResultEntry } from "./types";
import { partitionResults } from "./resultHelpers";
import CoinsWinIcon from "./CoinsWinIcon";
import "./resultStyles.css";

interface ResultsProps {
  entries: IResultEntry[];
  onPlayAgain: () => void;
  onHome: () => void;
  potAmount?: number;
  balance?: number | null;
}

const Results = ({
  entries,
  onPlayAgain,
  onHome,
  potAmount = 0,
  balance = null,
}: ResultsProps) => {
  const { winner } = partitionResults(entries);
  const didWin = !!winner?.isYou;
  const winnerName = winner?.name || "Winner";
  const winnerColor = (winner?.color || "blue").toLowerCase();
  const isForfeitWin =
    didWin && entries.some((entry) => !entry.isYou && entry.exited);
  const formattedPot = Number.isInteger(potAmount)
    ? String(potAmount)
    : potAmount.toFixed(2);

  return (
    <div
      className={`match-result-scrim ${didWin ? "is-win" : "is-loss"}`}
      role="presentation"
    >
      <aside
        className={`match-result-panel ${didWin ? "is-win" : "is-loss"}`}
        role="dialog"
        aria-modal="true"
        aria-label={didWin ? "You won the match" : "Match finished"}
      >
        {didWin && (
          <div className="match-result-confetti" aria-hidden>
            {Array.from({ length: 18 }, (_, index) => (
              <span
                key={index}
                className={`match-result-confetti-bit bit-${index % 6}`}
              />
            ))}
          </div>
        )}

        <div className="match-result-hero">
          <div className="match-result-crown" aria-hidden>
            {didWin ? "♛" : "✦"}
          </div>
          <div className="match-result-ribbon">
            <strong>{didWin ? "YOU WON!" : "GAME OVER"}</strong>
          </div>
          <p className="match-result-congrats">
            {didWin ? "Congratulations!" : "Better luck next time"}
          </p>
          <p className="match-result-subtitle">
            {didWin
              ? isForfeitWin
                ? "Opponent left — you are the winner!"
                : "You claimed the pot."
              : `${winnerName} won the match`}
          </p>
        </div>

        <div className="match-result-winner-card">
          <div className="match-result-goti-wrap">
            <svg
              className={`match-result-goti ${winnerColor}`}
              viewBox="0 0 64 76"
              aria-hidden
            >
              <circle cx="32" cy="66" r="11" />
              <path d="M32 4C20.5 4 12 14 12 25.5C12 38 32 70 32 70S52 38 52 25.5C52 14 43.5 4 32 4Z" />
              <circle className="match-result-goti-center" cx="32" cy="24" r="11.5" />
            </svg>
            <span className="match-result-winner-badge">Winner</span>
          </div>
          <div className="match-result-winner-copy">
            <span className="match-result-player-label">Winner</span>
            <strong className="match-result-player-name">{winnerName}</strong>
            {potAmount > 0 && (
              <div className="match-result-pot-chip">
                <CoinsWinIcon />
                <span>Pot ₹{formattedPot}</span>
              </div>
            )}
            {balance != null && (
              <span className="match-result-balance">
                Balance ₹{balance.toFixed(2)}
              </span>
            )}
          </div>
        </div>

        <div className="match-result-actions">
          <button
            className="match-result-button match-result-button-primary"
            type="button"
            onClick={onPlayAgain}
          >
            Play Again
          </button>
          <button
            className="match-result-button match-result-button-secondary"
            type="button"
            onClick={onHome}
          >
            Home
          </button>
        </div>
      </aside>
    </div>
  );
};

export default React.memo(Results);
