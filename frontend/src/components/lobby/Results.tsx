import React from "react";
import type { IResultEntry } from "./types";
import "./styles.css";

interface ResultsProps {
  title?: string;
  entries: IResultEntry[];
  onPlayAgain: () => void;
  onHome: () => void;
}

const Results = ({
  title = "Results",
  entries,
  onPlayAgain,
  onHome,
}: ResultsProps) => {
  const sorted = [...entries].sort((a, b) => a.rank - b.rank);
  const winner = sorted[0];

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <div className="lobby-crown" aria-hidden />
        <h2 className="lobby-heading">{title}</h2>
        {winner && (
          <p className="lobby-sub">
            Winner: <strong>{winner.name}</strong>
            {winner.isYou ? " (You)" : ""}
          </p>
        )}

        <div className="lobby-panel">
          <ol style={{ listStyle: "none", padding: 0, margin: "0 0 16px" }}>
            {sorted.map((entry) => (
              <li className="player-row" key={`${entry.rank}-${entry.name}`}>
                <span
                  style={{
                    minWidth: 28,
                    fontWeight: 800,
                    color: entry.rank === 1 ? "#ffe56a" : "#fff",
                  }}
                >
                  {entry.rank}
                  {entry.rank === 1 ? "st" : entry.rank === 2 ? "nd" : entry.rank === 3 ? "rd" : "th"}
                </span>
                {entry.color && (
                  <div className={`player-swatch ${entry.color.toLowerCase()}`} />
                )}
                <span style={{ flex: 1, fontWeight: 600 }}>
                  {entry.name}
                  {entry.isYou ? " · You" : ""}
                </span>
              </li>
            ))}
          </ol>

          <button className="lobby-btn primary" type="button" onClick={onPlayAgain}>
            PLAY AGAIN
          </button>
          <button
            className="lobby-btn secondary"
            type="button"
            onClick={onHome}
            style={{ marginTop: 10 }}
          >
            HOME
          </button>
        </div>
      </div>
    </div>
  );
};

export default React.memo(Results);
