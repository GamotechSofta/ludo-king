import React, { useEffect, useState } from "react";
import type { IGameConfig, ILobbyPlayer, TPlayMode } from "./types";
import type { TTotalPlayers } from "../../interfaces";
import { getOneBotName, getRandomBotNames } from "../../data/botNames";
import "./styles.css";

const COLORS = ["red", "green", "yellow", "blue"] as const;

const isGenericBotName = (name: string) => /^Bot\s*\d+$/i.test(name.trim());

const buildPlayers = (
  total: TTotalPlayers,
  mode: TPlayMode,
  previous: ILobbyPlayer[] = []
): ILobbyPlayer[] => {
  const usedNames: string[] = [];
  const freshBotNames =
    mode === "computer" ? getRandomBotNames(total) : [];
  let freshIndex = 0;

  const nextFreshBotName = () => {
    while (freshIndex < freshBotNames.length) {
      const candidate = freshBotNames[freshIndex++];
      if (!usedNames.includes(candidate)) {
        usedNames.push(candidate);
        return candidate;
      }
    }
    const fallback = getOneBotName(usedNames);
    usedNames.push(fallback);
    return fallback;
  };

  return Array.from({ length: total }, (_, i) => {
    const existing = previous[i];
    const isBot = mode === "computer" ? i !== 0 : existing?.isBot ?? false;

    let name: string;
    if (mode === "computer" && isBot) {
      const keepExisting =
        !!existing?.isBot &&
        !!existing.name &&
        !isGenericBotName(existing.name) &&
        !usedNames.includes(existing.name);
      if (keepExisting) {
        name = existing.name;
        usedNames.push(name);
      } else {
        name = nextFreshBotName();
      }
    } else if (existing?.name && !existing.isBot) {
      name = existing.name;
      usedNames.push(name);
    } else {
      name = `Player ${i + 1}`;
      usedNames.push(name);
    }

    return {
      id: existing?.id ?? String(i + 1),
      name,
      isBot,
    };
  });
};

interface SetupProps {
  mode: TPlayMode;
  onBack: () => void;
  onStart: (config: IGameConfig) => void;
}

const Setup = ({ mode, onBack, onStart }: SetupProps) => {
  const [totalPlayers, setTotalPlayers] = useState<TTotalPlayers>(4);
  const [players, setPlayers] = useState<ILobbyPlayer[]>(() =>
    buildPlayers(4, mode)
  );

  useEffect(() => {
    setPlayers((prev) => buildPlayers(totalPlayers, mode, prev));
  }, [totalPlayers, mode]);

  const updateName = (index: number, name: string) => {
    setPlayers((prev) =>
      prev.map((p, i) => (i === index ? { ...p, name } : p))
    );
  };

  const toggleBot = (index: number) => {
    if (mode !== "computer" || index === 0) return;
    setPlayers((prev) => {
      const usedNames = prev.map((p) => p.name);
      return prev.map((p, i) => {
        if (i !== index) return p;
        const isBot = !p.isBot;
        return {
          ...p,
          isBot,
          name: isBot
            ? getOneBotName(usedNames.filter((_, idx) => idx !== i))
            : `Player ${i + 1}`,
        };
      });
    });
  };

  const handleStart = () => {
    onStart({ mode, totalPlayers, players });
  };

  return (
    <div className="lobby">
      <div className="lobby-top" style={{ width: "100%" }}>
        <button className="lobby-back" type="button" onClick={onBack}>
          ← Back
        </button>
        <h2 className="lobby-heading">
          {mode === "computer" ? "Vs Computer" : "Pass & Play"}
        </h2>
        <p className="lobby-sub">Select players and start the race</p>

        <div className="lobby-panel">
          <div className="player-count">
            {([2, 3, 4] as TTotalPlayers[]).map((count) => (
              <button
                key={count}
                type="button"
                className={totalPlayers === count ? "active" : ""}
                onClick={() => setTotalPlayers(count)}
              >
                {count}P
              </button>
            ))}
          </div>

          <div className="player-list">
            {players.map((player, index) => (
              <div className="player-row" key={player.id}>
                <div className={`player-swatch ${COLORS[index]}`} />
                <input
                  value={player.name}
                  maxLength={16}
                  onChange={(e) => updateName(index, e.target.value)}
                  aria-label={`Name for seat ${index + 1}`}
                />
                {mode === "computer" && index > 0 && (
                  <button
                    type="button"
                    className={`bot-toggle ${player.isBot ? "on" : ""}`}
                    onClick={() => toggleBot(index)}
                  >
                    {player.isBot ? "BOT" : "HUMAN"}
                  </button>
                )}
              </div>
            ))}
          </div>

          <button className="lobby-btn primary" type="button" onClick={handleStart}>
            START GAME
          </button>
        </div>
      </div>
    </div>
  );
};

export default React.memo(Setup);
