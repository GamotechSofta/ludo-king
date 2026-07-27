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
        <h2 className="lobby-heading">Online</h2>
        <p className="lobby-sub">Find players · then smooth Ludo</p>
      </div>

      <div className="lobby-actions">
        <button
          className="lobby-btn mode"
          type="button"
          onClick={() => onSelect("online")}
        >
          <span className="mode-icon online" aria-hidden />
          <span className="mode-copy">
            <span>Online</span>
            <small>Quick match · auto fill</small>
          </span>
        </button>
      </div>
    </div>
  );
};

export default React.memo(ModeSelect);
