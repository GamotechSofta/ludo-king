import React from "react";
import type { TPlayMode } from "./types";
import "./styles.css";

interface ModeSelectProps {
  onBack: () => void;
  onSelect: (mode: TPlayMode) => void;
}

const ModeSelect = ({ onBack, onSelect }: ModeSelectProps) => {
  return (
    <div className="lobby">
      <div className="lobby-top">
        <button className="lobby-back" type="button" onClick={onBack}>
          ← Back
        </button>
        <h2 className="lobby-heading">Choose Mode</h2>
        <p className="lobby-sub">Pick how you want to play</p>
      </div>

      <div className="lobby-actions">
        <button
          className="lobby-btn mode"
          type="button"
          onClick={() => onSelect("computer")}
        >
          <span className="mode-icon computer" aria-hidden />
          <span className="mode-copy">
            <span>Computer</span>
            <small>Challenge smart bots</small>
          </span>
        </button>

        <button
          className="lobby-btn mode"
          type="button"
          onClick={() => onSelect("local")}
        >
          <span className="mode-icon local" aria-hidden />
          <span className="mode-copy">
            <span>Pass &amp; Play</span>
            <small>Same device · 2–4 friends</small>
          </span>
        </button>

        <button className="lobby-btn mode disabled" type="button" disabled>
          <span className="lobby-badge">SOON</span>
          <span className="mode-icon online" aria-hidden />
          <span className="mode-copy">
            <span>Online</span>
            <small>Play worldwide in realtime</small>
          </span>
        </button>
      </div>
    </div>
  );
};

export default React.memo(ModeSelect);
