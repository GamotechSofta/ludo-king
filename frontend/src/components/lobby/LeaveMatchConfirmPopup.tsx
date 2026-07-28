import React from "react";
import "./styles.css";

interface LeaveMatchConfirmPopupProps {
  isTwoPlayer?: boolean;
  isHumanMatch?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const LeaveMatchConfirmPopup = ({
  isTwoPlayer = false,
  isHumanMatch = true,
  onConfirm,
  onCancel,
}: LeaveMatchConfirmPopupProps) => {
  const warningText = isHumanMatch
    ? isTwoPlayer
      ? "You will be marked as LOST and your opponent will win."
      : "You will be marked as LOST and removed from the match."
    : "You will leave this match.";

  return (
    <div
      className="lost-summary-overlay"
      role="dialog"
      aria-modal="true"
      aria-labelledby="leave-match-title"
    >
      <div className="lost-summary-card">
        <div className="lost-summary-badge" aria-hidden>
          LEAVE?
        </div>
        <h2 id="leave-match-title" className="lost-summary-title">
          Leave Match?
        </h2>
        <p className="lost-summary-sub">{warningText}</p>

        <div className="lost-summary-stat-row">
          <span className="lost-summary-stat-label">Match status</span>
          <span className="lost-summary-stat-value">In progress</span>
        </div>

        <div className="lost-summary-actions">
          <button
            className="lobby-btn primary lost-summary-btn"
            type="button"
            onClick={onConfirm}
          >
            Leave Match
          </button>
          <button
            className="lobby-btn secondary lost-summary-btn"
            type="button"
            onClick={onCancel}
          >
            Keep Playing
          </button>
        </div>
      </div>
    </div>
  );
};

export default React.memo(LeaveMatchConfirmPopup);
