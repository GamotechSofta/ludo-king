import React, { useEffect, useMemo, useState } from "react";
import { useGameSocket } from "../../hooks/useGameSocket";
import type { IGuestUser, IGameSnapshot, IResultEntry } from "./types";
import Results from "./Results";
import "./styles.css";

interface OnlineGameProps {
  guest: IGuestUser;
  roomId: string;
  onExit: () => void;
  onPlayAgain: () => void;
}

const OnlineGame = ({ guest, roomId, onExit, onPlayAgain }: OnlineGameProps) => {
  const { snapshot, connected, rollDice, moveToken } = useGameSocket(
    roomId,
    guest.id
  );
  const [showResults, setShowResults] = useState(false);

  useEffect(() => {
    if (snapshot?.phase === "FINISHED") {
      const t = window.setTimeout(() => setShowResults(true), 600);
      return () => window.clearTimeout(t);
    }
    setShowResults(false);
  }, [snapshot?.phase]);

  const mySeat = useMemo(() => {
    if (!snapshot?.userIds) return -1;
    return snapshot.userIds.findIndex((id) => id === guest.id);
  }, [snapshot?.userIds, guest.id]);

  const isMyTurn =
    mySeat >= 0 && snapshot?.currentSeatIndex === mySeat && connected;

  const canRoll = isMyTurn && snapshot?.phase === "WAITING_ROLL";
  const canMove = isMyTurn && snapshot?.phase === "WAITING_MOVE";

  const resultEntries: IResultEntry[] = useMemo(
    () => buildResults(snapshot, guest.id),
    [snapshot, guest.id]
  );

  if (showResults && snapshot?.phase === "FINISHED") {
    return (
      <Results
        title="Match Results"
        entries={resultEntries}
        onPlayAgain={onPlayAgain}
        onHome={onExit}
      />
    );
  }

  const legalMoves =
    snapshot?.legalMoves?.length
      ? snapshot.legalMoves
      : (snapshot?.legalTokenIndexes || []).map((tokenIndex) => ({
          tokenIndex,
          diceIndex: 0,
        }));

  return (
    <div
      className="lobby"
      style={{ justifyContent: "flex-start", paddingTop: 48 }}
    >
      <button
        className="game-exit-btn"
        type="button"
        onClick={onExit}
        style={{ alignSelf: "flex-start" }}
      >
        ← Home
      </button>

      <h2 className="lobby-heading" style={{ marginTop: 16 }}>
        Live Match
      </h2>
      <p className="lobby-sub">
        {connected ? "Connected" : "Connecting…"}
        {mySeat >= 0 ? ` · Seat ${mySeat + 1}` : ""}
        {snapshot?.currentColor ? ` · Turn ${snapshot.currentColor}` : ""}
      </p>

      <div className="lobby-panel">
        <p className="lobby-footer-note">
          Phase: {snapshot?.phase || "…"}
          {isMyTurn ? " · Your turn" : " · Wait"}
        </p>
        <p className="lobby-footer-note">
          Dice pool: {(snapshot?.diceList || []).join(", ") || "—"}
        </p>

        <button
          className="lobby-btn primary"
          type="button"
          disabled={!canRoll}
          onClick={rollDice}
        >
          ROLL DICE
        </button>

        {canMove && (
          <div
            style={{
              marginTop: 12,
              display: "flex",
              flexDirection: "column",
              gap: 8,
            }}
          >
            <p className="lobby-footer-note">Choose a move</p>
            {legalMoves.map((m) => (
              <button
                key={`${m.tokenIndex}-${m.diceIndex}`}
                type="button"
                className="lobby-btn secondary"
                onClick={() => moveToken(m.tokenIndex, m.diceIndex)}
              >
                Token {m.tokenIndex + 1} with dice #
                {(snapshot?.diceList || [])[m.diceIndex] ?? "?"}
              </button>
            ))}
          </div>
        )}

        <div style={{ marginTop: 16 }}>
          {snapshot?.usernames?.map((name, seat) => {
            const color = Object.keys(snapshot.tokenPositions || {})[seat];
            const positions = color
              ? snapshot.tokenPositions[color]
              : undefined;
            return (
              <div className="player-row" key={`${seat}-${name}`}>
                {color && (
                  <div className={`player-swatch ${color.toLowerCase()}`} />
                )}
                <span style={{ flex: 1, fontSize: "0.85rem", fontWeight: 600 }}>
                  {name}
                  {snapshot.isBot?.[seat] ? " (Bot)" : ""}
                  {seat === mySeat ? " · You" : ""}
                  {seat === snapshot.currentSeatIndex ? " · Turn" : ""}
                </span>
                <span style={{ fontSize: "0.75rem", opacity: 0.85 }}>
                  {positions ? `[${positions.join(",")}]` : ""}
                </span>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

function buildResults(
  snapshot: IGameSnapshot | null,
  myId: string
): IResultEntry[] {
  if (!snapshot?.usernames || !snapshot.standings) return [];
  return snapshot.usernames.map((name, seat) => ({
    rank: snapshot.standings![seat] || seat + 1,
    name,
    color: Object.keys(snapshot.tokenPositions || {})[seat],
    isBot: snapshot.isBot?.[seat],
    isYou: snapshot.userIds?.[seat] === myId,
  }));
}

export default React.memo(OnlineGame);
