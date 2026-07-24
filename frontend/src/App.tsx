import React, { useCallback, useState } from "react";
import { AppWrapper } from "./components/wrapper";
import Game from "./components/game";
import {
  Home,
  ModeSelect,
  Setup,
  type IGameConfig,
  type IGuestUser,
  type IOnlineRoom,
  type IResultEntry,
  type TLobbyScreen,
  type TPlayMode,
} from "./components/lobby";
import OnlineSetup from "./components/lobby/OnlineSetup";
import OnlineLobby from "./components/lobby/OnlineLobby";
import OnlineGame from "./components/lobby/OnlineGame";
import Results from "./components/lobby/Results";
import type { IUser, TTotalPlayers } from "./interfaces";
import { ETypeGame } from "./utils/constants";

const App = () => {
  const [screen, setScreen] = useState<TLobbyScreen>("home");
  const [mode, setMode] = useState<TPlayMode>("computer");
  const [gameConfig, setGameConfig] = useState<IGameConfig | null>(null);
  const [resultEntries, setResultEntries] = useState<IResultEntry[]>([]);

  const [guest, setGuest] = useState<IGuestUser | null>(null);
  const [onlineRoomId, setOnlineRoomId] = useState<string | null>(null);
  const [onlineRoomCode, setOnlineRoomCode] = useState("");

  const goHome = () => {
    setGameConfig(null);
    setOnlineRoomId(null);
    setResultEntries([]);
    setScreen("home");
  };

  const handleSelectMode = (nextMode: TPlayMode) => {
    setMode(nextMode);
    if (nextMode === "online") {
      setScreen("onlineSetup");
      return;
    }
    setScreen("setup");
  };

  const handleStart = (config: IGameConfig) => {
    setGameConfig(config);
    setScreen("game");
  };

  const handleOfflineGameOver = useCallback((entries: IResultEntry[]) => {
    setResultEntries(entries);
    setScreen("results");
  }, []);

  const handlePlayAgain = () => {
    setResultEntries([]);
    if (mode === "online") {
      setOnlineRoomId(null);
      setScreen("onlineSetup");
      return;
    }
    if (gameConfig) {
      setScreen("setup");
      return;
    }
    setScreen("modes");
  };

  const handleOnlineQueued = (
    g: IGuestUser,
    roomId: string,
    roomCode: string
  ) => {
    setGuest(g);
    setOnlineRoomId(roomId);
    setOnlineRoomCode(roomCode);
    setScreen("onlineLobby");
  };

  const handleOnlineStart = useCallback((_room: IOnlineRoom) => {
    setScreen("onlineGame");
  }, []);

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
          onExit={goHome}
          onGameOver={handleOfflineGameOver}
        />
      </AppWrapper>
    );
  }

  return (
    <AppWrapper>
      {screen === "home" && <Home onPlay={() => setScreen("modes")} />}
      {screen === "modes" && (
        <ModeSelect onBack={() => setScreen("home")} onSelect={handleSelectMode} />
      )}
      {screen === "setup" && (
        <Setup
          mode={mode}
          onBack={() => setScreen("modes")}
          onStart={handleStart}
        />
      )}
      {screen === "onlineSetup" && (
        <OnlineSetup
          onBack={() => setScreen("modes")}
          onQueued={handleOnlineQueued}
        />
      )}
      {screen === "onlineLobby" && guest && onlineRoomId && (
        <OnlineLobby
          guest={guest}
          roomId={onlineRoomId}
          roomCode={onlineRoomCode}
          onBack={() => setScreen("onlineSetup")}
          onStart={handleOnlineStart}
        />
      )}
      {screen === "onlineGame" && guest && onlineRoomId && (
        <OnlineGame
          guest={guest}
          roomId={onlineRoomId}
          onExit={goHome}
          onPlayAgain={handlePlayAgain}
        />
      )}
      {screen === "results" && (
        <Results
          title="Match Results"
          entries={resultEntries}
          onPlayAgain={handlePlayAgain}
          onHome={goHome}
        />
      )}
    </AppWrapper>
  );
};

export default React.memo(App);
