import React, { useCallback, useEffect, useState } from "react";
import { AppWrapper } from "./components/wrapper";
import Game from "./components/game";
import {
  Home,
  ModeSelect,
  Setup,
  type IGameConfig,
  type IGameSnapshot,
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

type HistoryState = { screen: TLobbyScreen };

const App = () => {
  const [screen, setScreen] = useState<TLobbyScreen>("home");
  const [mode, setMode] = useState<TPlayMode>("computer");
  const [gameConfig, setGameConfig] = useState<IGameConfig | null>(null);
  const [resultEntries, setResultEntries] = useState<IResultEntry[]>([]);

  const [guest, setGuest] = useState<IGuestUser | null>(null);
  const [onlineRoomId, setOnlineRoomId] = useState<string | null>(null);
  const [onlineRoomCode, setOnlineRoomCode] = useState("");
  const [onlineGameSnap, setOnlineGameSnap] = useState<IGameSnapshot | null>(
    null
  );

  const applyScreen = useCallback((next: TLobbyScreen) => {
    if (next === "home") {
      setGameConfig(null);
      setOnlineRoomId(null);
      setOnlineGameSnap(null);
      setResultEntries([]);
    }
    if (next === "modes" || next === "setup" || next === "onlineSetup") {
      setGameConfig(null);
      setResultEntries([]);
    }
    if (next === "onlineSetup") {
      setOnlineRoomId(null);
      setOnlineGameSnap(null);
    }
    setScreen(next);
  }, []);

  const goTo = useCallback(
    (next: TLobbyScreen, replace = false) => {
      const state: HistoryState = { screen: next };
      if (replace) {
        window.history.replaceState(state, "");
      } else {
        window.history.pushState(state, "");
      }
      applyScreen(next);
    },
    [applyScreen]
  );

  const goHome = useCallback(() => {
    goTo("home", true);
  }, [goTo]);

  const goBack = useCallback(() => {
    if (window.history.state?.screen) {
      window.history.back();
      return;
    }
    goHome();
  }, [goHome]);

  useEffect(() => {
    window.history.replaceState({ screen: "home" } satisfies HistoryState, "");
  }, []);

  useEffect(() => {
    const onPopState = (event: PopStateEvent) => {
      const next = (event.state as HistoryState | null)?.screen ?? "home";
      applyScreen(next);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [applyScreen]);

  const handleSelectMode = (nextMode: TPlayMode) => {
    setMode(nextMode);
    if (nextMode === "online") {
      goTo("onlineSetup");
      return;
    }
    goTo("setup");
  };

  const handleStart = (config: IGameConfig) => {
    setGameConfig(config);
    goTo("game");
  };

  const handleOfflineGameOver = useCallback(
    (entries: IResultEntry[]) => {
      setResultEntries(entries);
      goTo("results");
    },
    [goTo]
  );

  const handlePlayAgain = () => {
    setResultEntries([]);
    if (mode === "online") {
      setOnlineRoomId(null);
      goTo("onlineSetup", true);
      return;
    }
    if (gameConfig) {
      goTo("setup", true);
      return;
    }
    goTo("modes", true);
  };

  const handleOnlineQueued = (
    g: IGuestUser,
    roomId: string,
    roomCode: string
  ) => {
    setGuest(g);
    setOnlineRoomId(roomId);
    setOnlineRoomCode(roomCode);
    goTo("onlineLobby");
  };

  const handleOnlineStart = useCallback(
    (_room: IOnlineRoom, game?: IGameSnapshot | null) => {
      if (game) {
        setOnlineGameSnap(game);
      }
      goTo("onlineGame");
    },
    [goTo]
  );

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
          onExit={goBack}
          onGameOver={handleOfflineGameOver}
        />
      </AppWrapper>
    );
  }

  return (
    <AppWrapper>
      {screen === "home" && <Home onPlay={() => goTo("modes")} />}
      {screen === "modes" && (
        <ModeSelect onBack={goBack} onSelect={handleSelectMode} />
      )}
      {screen === "setup" && (
        <Setup mode={mode} onBack={goBack} onStart={handleStart} />
      )}
      {screen === "onlineSetup" && (
        <OnlineSetup onBack={goBack} onQueued={handleOnlineQueued} />
      )}
      {screen === "onlineLobby" && guest && onlineRoomId && (
        <OnlineLobby
          guest={guest}
          roomId={onlineRoomId}
          roomCode={onlineRoomCode}
          onBack={goBack}
          onStart={handleOnlineStart}
        />
      )}
      {screen === "onlineGame" && guest && onlineRoomId && (
        <OnlineGame
          guest={guest}
          roomId={onlineRoomId}
          initialSnapshot={onlineGameSnap}
          onExit={goBack}
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
