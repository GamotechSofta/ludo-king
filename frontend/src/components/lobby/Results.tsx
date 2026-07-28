import React from "react";
import type { IResultEntry } from "./types";
import {
  lostStatusLabel,
  partitionResults,
} from "./resultHelpers";
import "./styles.css";

interface ResultsProps {
  title?: string;
  entries: IResultEntry[];
  onPlayAgain: () => void;
  onHome: () => void;
}

const Results = ({
  title = "Match Results",
  entries,
  onPlayAgain,
  onHome,
}: ResultsProps) => {
  const { winner, lost } = partitionResults(entries);

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <div className="lobby-crown" aria-hidden />
        <h2 className="lobby-heading">{title}</h2>

        <div className="lobby-panel match-results-panel">
          {winner && (
            <section className="match-results-section">
              <h3 className="match-results-section-title winner">🏆 Winner</h3>
              <div className="match-results-winner-card">
                {winner.color && (
                  <div
                    className={`player-swatch ${winner.color.toLowerCase()}`}
                  />
                )}
                <div className="match-results-winner-meta">
                  <span className="match-results-name">
                    {winner.name}
                    {winner.isYou ? " · You" : ""}
                  </span>
                  <span className="match-results-rank">Rank 1</span>
                </div>
              </div>
            </section>
          )}

          {lost.length > 0 && (
            <section className="match-results-section">
              <h3 className="match-results-section-title lost">❌ Lost</h3>
              <ol className="match-results-lost-list">
                {lost.map((entry) => (
                  <li
                    className="match-results-lost-row"
                    key={`${entry.name}-${entry.color || "x"}`}
                  >
                    {entry.color && (
                      <div
                        className={`player-swatch ${entry.color.toLowerCase()}`}
                      />
                    )}
                    <span className="match-results-name">
                      {entry.name}
                      {entry.isYou ? " · You" : ""}
                    </span>
                    <span className="match-results-lost-tag">
                      {entry.playing ? "Playing" : lostStatusLabel(entry)}
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          )}

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
