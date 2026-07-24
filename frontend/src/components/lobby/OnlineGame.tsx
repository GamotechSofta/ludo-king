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
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null);

  useEffect(() => {
    if (snapshot?.phase === "FINISHED") {
      const t = window.setTimeout(() => setShowResults(true), 600);
      return () => window.clearTimeout(t);
    }
    setShowResults(false);
  }, [snapshot?.phase]);

  useEffect(() => {
    if (
      snapshot?.turnSecondsRemaining == null ||
      snapshot.phase === "FINISHED"
    ) {
      setSecondsLeft(null);
      return;
    }
    setSecondsLeft(snapshot.turnSecondsRemaining);
    const id = window.setInterval(() => {
      setSecondsLeft((prev) =>
        prev == null ? null : Math.max(0, prev - 1)
      );
    }, 1000);
    return () => window.clearInterval(id);
  }, [
    snapshot?.turnStartedAt,
    snapshot?.turnSecondsRemaining,
    snapshot?.phase,
    snapshot?.currentSeatIndex,
  ]);

  const mySeat = useMemo(() => {
    if (!snapshot?.userIds) return -1;
    return snapshot.userIds.findIndex((id) => id === guest.id);
  }, [snapshot?.userIds, guest.id]);

  const isMyTurn =
    mySeat >= 0 && snapshot?.currentSeatIndex === mySeat && connected;

  const canRoll = isMyTurn && snapshot?.phase === "AWAITING_ROLL";
  const canMove = isMyTurn && snapshot?.phase === "AWAITING_MOVE";

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
        className="game-back-arrow"
        type="button"
        aria-label="Back"
        onClick={onExit}
        style={{ position: "absolute", top: 10, left: 10, alignSelf: "flex-start" }}
      >
        <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden>
          <path
            d="M15.5 4.5L8 12l7.5 7.5"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.6"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </button>

      <h2 className="lobby-heading" style={{ marginTop: 16 }}>
        Live Match
      </h2>
      <p className="lobby-sub">
        {connected ? "Connected" : "Connecting…"}
        {mySeat >= 0 ? ` · Seat ${mySeat + 1}` : ""}
        {snapshot?.currentColor ? ` · Turn ${snapshot.currentColor}` : ""}
        {secondsLeft != null ? ` · ${secondsLeft}s` : ""}
      </p>

      <div className="lobby-panel">
        <p className="lobby-footer-note">
          Phase: {snapshot?.phase || "…"}
          {isMyTurn ? " · Your turn" : " · Wait"}
          {snapshot?.consecutiveSixes
            ? ` · Sixes ${snapshot.consecutiveSixes}/3`
            : ""}
        </p>
        <p className="lobby-footer-note">
          Dice: {(snapshot?.diceList || []).join(", ") || "—"}
          {secondsLeft != null
            ? ` · Timer ${secondsLeft}s (skip at 0)`
            : ""}
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
