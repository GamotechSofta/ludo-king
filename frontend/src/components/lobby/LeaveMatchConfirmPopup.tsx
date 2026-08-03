import React from "react";
import "./styles.css";

interface LeaveMatchConfirmPopupProps {
  isTwoPlayer?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

const LeaveMatchConfirmPopup = ({
  isTwoPlayer = false,
  onConfirm,
  onCancel,
}: LeaveMatchConfirmPopupProps) => {
  const warningText = isTwoPlayer
    ? "If you leave now you will be marked as LOST and your opponent will win."
    : "If you leave now you will be marked as LOST and removed from the match.";

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
            Leave &amp; Lose
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
