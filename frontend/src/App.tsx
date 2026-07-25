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
import PlatformLaunch, {
  type PlatformQuery,
} from "./components/lobby/PlatformLaunch";
import Results from "./components/lobby/Results";
import type { IUser, TTotalPlayers } from "./interfaces";
import { ETypeGame } from "./utils/constants";

type HistoryState = { screen: TLobbyScreen };

/** Detect Aakda WebView launch params. userId required for platform flow. */
function parsePlatformQuery(): PlatformQuery | "missing-userid" | null {
  const params = new URLSearchParams(window.location.search);
  const userId = params.get("userId");
  const gameId = params.get("gameId");
  const sessionId = params.get("sessionId");
  const token = params.get("token");
  const returnUrl = params.get("returnUrl");
  const playersRaw = params.get("players") || params.get("maxPlayers");
  const playersNum = playersRaw ? Number(playersRaw) : NaN;
  const players =
    playersNum === 2 || playersNum === 3 || playersNum === 4
      ? (playersNum as 2 | 3 | 4)
      : undefined;
  const betRaw = params.get("bet");
  const betNum = betRaw ? Number(betRaw) : NaN;
  const bet = Number.isFinite(betNum) && betNum > 0 ? betNum : undefined;

  const hasAnyPlatformHint =
    userId != null ||
    sessionId != null ||
    token != null ||
    (gameId != null && gameId.length > 0) ||
    returnUrl != null;

  if (!hasAnyPlatformHint) {
    return null;
  }
  if (!userId || !userId.trim()) {
    return "missing-userid";
  }
  return {
    userId: userId.trim(),
    gameId: (gameId && gameId.trim()) || "LUDO",
    sessionId: sessionId || undefined,
    token: token || undefined,
    returnUrl: returnUrl || undefined,
    players,
    bet,
  };
}

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

  const initialPlatform = parsePlatformQuery();
  const [platformQuery, setPlatformQuery] = useState<PlatformQuery | null>(
    initialPlatform !== null && initialPlatform !== "missing-userid"
      ? initialPlatform
      : null
  );
  const [platformError, setPlatformError] = useState<string | null>(
    initialPlatform === "missing-userid"
      ? "Open this game from Aakda app"
      : null
  );
  const [platformReturnUrl, setPlatformReturnUrl] = useState<string | null>(
    initialPlatform !== null && initialPlatform !== "missing-userid"
      ? initialPlatform.returnUrl || null
      : null
  );
  const [walletBalance, setWalletBalance] = useState<number | null>(null);
  const [entryFee, setEntryFee] = useState(0);

  const exitToPlatform = useCallback(() => {
    if (platformReturnUrl) {
      window.location.href = platformReturnUrl;
      return;
    }
    // Fallback page for WebView without returnUrl
    document.body.innerHTML =
      '<div style="font-family:sans-serif;padding:24px;text-align:center">' +
      "<h2>Return to Aakda</h2>" +
      "<p>You can close this screen and go back to the Aakda app.</p></div>";
  }, [platformReturnUrl]);

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
    if (platformReturnUrl || platformQuery) {
      exitToPlatform();
      return;
    }
    goTo("home", true);
  }, [goTo, platformReturnUrl, platformQuery, exitToPlatform]);

  const goBack = useCallback(() => {
    if (platformReturnUrl || platformQuery) {
      exitToPlatform();
      return;
    }
    if (window.history.state?.screen) {
      window.history.back();
      return;
    }
    goHome();
  }, [goHome, platformReturnUrl, platformQuery, exitToPlatform]);

  useEffect(() => {
    if (platformQuery || platformError) return;
    window.history.replaceState({ screen: "home" } satisfies HistoryState, "");
  }, [platformQuery, platformError]);

  useEffect(() => {
    const onPopState = (event: PopStateEvent) => {
      if (platformReturnUrl || platformQuery) {
        exitToPlatform();
        return;
      }
      const next = (event.state as HistoryState | null)?.screen ?? "home";
      applyScreen(next);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [applyScreen, platformReturnUrl, platformQuery, exitToPlatform]);

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
    if (platformReturnUrl || platformQuery) {
      exitToPlatform();
      return;
    }
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

  const handlePlatformReady = useCallback(
    (
      g: IGuestUser,
      roomId: string,
      roomCode: string,
      returnUrl?: string | null,
      wallet?: { balance: number; entryFee: number; walletEnabled: boolean }
    ) => {
      setGuest(g);
      setOnlineRoomId(roomId);
      setOnlineRoomCode(roomCode);
      setMode("online");
      if (returnUrl) setPlatformReturnUrl(returnUrl);
      if (wallet?.walletEnabled) {
        // Balance after debit happens server-side on queue — refresh approx
        setWalletBalance(
          Math.max(0, wallet.balance - (wallet.entryFee || 0))
        );
        setEntryFee(wallet.entryFee || 0);
      }
      setPlatformQuery(null);
      window.history.replaceState({ screen: "onlineLobby" }, "", "/");
      goTo("onlineLobby", true);
    },
    [goTo]
  );

  if (platformError) {
    return (
      <AppWrapper>
        <div
          style={{
            minHeight: "100dvh",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            padding: 24,
            color: "#fff",
            textAlign: "center",
            fontFamily: "Fredoka, sans-serif",
          }}
        >
          <h2 style={{ marginBottom: 8 }}>Open this game from Aakda app</h2>
          <p style={{ opacity: 0.85 }}>{platformError}</p>
        </div>
      </AppWrapper>
    );
  }

  if (platformQuery) {
    return (
      <PlatformLaunch
        query={platformQuery}
        onReady={handlePlatformReady}
        onError={setPlatformError}
      />
    );
  }

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
          walletBalance={walletBalance}
          entryFee={entryFee}
          onBack={goBack}
          onStart={handleOnlineStart}
        />
      )}
      {screen === "onlineGame" && guest && onlineRoomId && (
        <OnlineGame
          guest={guest}
          roomId={onlineRoomId}
          initialSnapshot={onlineGameSnap}
          walletBalance={walletBalance}
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
