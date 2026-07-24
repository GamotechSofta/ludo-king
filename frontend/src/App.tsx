import React, { useState } from "react";
import { AppWrapper } from "./components/wrapper";
import Game from "./components/game";
import {
  Home,
  ModeSelect,
  Setup,
  type IGameConfig,
  type TLobbyScreen,
  type TPlayMode,
} from "./components/lobby";
import type { IUser, TTotalPlayers } from "./interfaces";
import { ETypeGame } from "./utils/constants";

const App = () => {
  const [screen, setScreen] = useState<TLobbyScreen>("home");
  const [mode, setMode] = useState<TPlayMode>("computer");
  const [gameConfig, setGameConfig] = useState<IGameConfig | null>(null);

  const handleSelectMode = (nextMode: TPlayMode) => {
    if (nextMode === "online") return;
    setMode(nextMode);
    setScreen("setup");
  };

  const handleStart = (config: IGameConfig) => {
    setGameConfig(config);
    setScreen("game");
  };

  const handleExitGame = () => {
    setGameConfig(null);
    setScreen("home");
  };

  if (screen === "game" && gameConfig) {
    const users: IUser[] = gameConfig.players.map((player) => ({
      id: player.id,
      name: player.name.trim() || "Player",
      isBot: player.isBot,
    }));

    return (
      <AppWrapper>
        <Game
          key={`${gameConfig.mode}-${gameConfig.totalPlayers}-${users
            .map((u) => u.name)
            .join("-")}`}
          initialTurn={0}
          users={users}
          totalPlayers={gameConfig.totalPlayers as TTotalPlayers}
          typeGame={ETypeGame.OFFLINE}
          debug={false}
          onExit={handleExitGame}
        />
      </AppWrapper>
    );
  }

  return (
    <AppWrapper>
      {screen === "home" && <Home onPlay={() => setScreen("modes")} />}
      {screen === "modes" && (
        <ModeSelect
          onBack={() => setScreen("home")}
          onSelect={handleSelectMode}
        />
      )}
      {screen === "setup" && (
        <Setup
          mode={mode}
          onBack={() => setScreen("modes")}
          onStart={handleStart}
        />
      )}
    </AppWrapper>
  );
};

export default React.memo(App);
