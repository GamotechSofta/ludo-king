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
import { fetchWalletBalance, leaveRoom } from "./api/ludoApi";
import { startBackgroundMusic, stopBackgroundMusic } from "./utils/sounds";

type HistoryState = { screen: TLobbyScreen };

/** Detect Aakda WebView launch params. userId required for platform flow. */
/** Reads the `id` claim from Aakda's HS256 launch token without verifying it. */
function claimsFromToken(token: string | null): Record<string, unknown> | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const parsed = JSON.parse(json);
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}

function parsePlatformQuery(): PlatformQuery | "missing-userid" | null {
  const params = new URLSearchParams(window.location.search);
  // Aakda sends the same JWT as `token`; `id` is the PotLudo-compatible alias.
  const token = params.get("token") || params.get("id");
  const claims = claimsFromToken(token);
  const claimUserId = typeof claims?.id === "string" ? claims.id : null;
  const userId = params.get("userId") || claimUserId;
  const gameId = params.get("gameId") || params.get("game_id");
  const sessionId = params.get("sessionId");
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
  const displayNameRaw =
    params.get("username") ||
    params.get("name") ||
    params.get("displayName") ||
    params.get("playerName") ||
    params.get("userName") ||
    (typeof claims?.username === "string" ? claims.username : null) ||
    (typeof claims?.name === "string" ? claims.name : null);
  const displayName =
    displayNameRaw && displayNameRaw.trim()
      ? displayNameRaw.trim().slice(0, 64)
      : undefined;

  // Optimistic only — reconciled against POST /api/wallet/balance on launch.
  const balanceRaw = params.get("balance");
  const balanceNum = balanceRaw != null ? Number(balanceRaw) : Number(claims?.balance);
  const launchBalance = Number.isFinite(balanceNum) && balanceNum >= 0 ? balanceNum : undefined;
  const currency =
    params.get("currency") ||
    (typeof claims?.currency === "string" ? claims.currency : null) ||
    undefined;

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
    displayName,
    launchBalance,
    currency,
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
  const [onlineSnapshot, setOnlineSnapshot] = useState<IGameSnapshot | null>(
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
  /** True once an Aakda launch bound this session, even without a returnUrl. */
  const [platformSession, setPlatformSession] = useState(false);
  const [initialTurn, setInitialTurn] = useState(0);

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

  /** Release server room so the same userId can queue a fresh match. */
  const releaseOnlineRoom = useCallback(async () => {
    const roomId = onlineRoomId;
    const userId = guest?.id;
    if (roomId && userId) {
      try {
        await leaveRoom(roomId, userId);
      } catch {
        /* room may already be completed */
      }
    }
    setOnlineRoomId(null);
    setOnlineRoomCode("");
    setOnlineSnapshot(null);
  }, [onlineRoomId, guest]);

  const applyScreen = useCallback((next: TLobbyScreen) => {
    if (next === "home") {
      setGameConfig(null);
      setOnlineRoomId(null);
      setOnlineSnapshot(null);
      setResultEntries([]);
    }
    if (next === "modes" || next === "setup" || next === "onlineSetup") {
      setGameConfig(null);
      setOnlineSnapshot(null);
      setResultEntries([]);
    }
    if (next === "onlineSetup") {
      setOnlineRoomId(null);
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
      void releaseOnlineRoom().then(() => exitToPlatform());
      return;
    }
    goTo("home", true);
  }, [goTo, platformReturnUrl, platformQuery, exitToPlatform, releaseOnlineRoom]);

  const goBack = useCallback(() => {
    if (platformReturnUrl || platformQuery) {
      void releaseOnlineRoom().then(() => exitToPlatform());
      return;
    }
    if (window.history.state?.screen) {
      window.history.back();
      return;
    }
    goHome();
  }, [goHome, platformReturnUrl, platformQuery, exitToPlatform, releaseOnlineRoom]);

  useEffect(() => {
    if (platformQuery || platformError) return;
    window.history.replaceState({ screen: "home" } satisfies HistoryState, "");
  }, [platformQuery, platformError]);

  // The stake is debited per match, so re-read the wallet before each new one.
  useEffect(() => {
    if (screen !== "onlineSetup" || !platformSession || !guest?.id) return;
    let cancelled = false;
    void fetchWalletBalance(guest.id)
      .then((res) => {
        if (!cancelled && typeof res.balance === "number") {
          setWalletBalance(res.balance);
        }
      })
      .catch(() => {
        /* keep the last known balance */
      });
    return () => {
      cancelled = true;
    };
  }, [screen, platformSession, guest]);

  useEffect(() => {
    const lobbyBgm =
      !!platformQuery ||
      screen === "home" ||
      screen === "onlineSetup" ||
      screen === "onlineLobby";
    if (lobbyBgm) {
      startBackgroundMusic();
    } else {
      stopBackgroundMusic();
    }
  }, [screen, platformQuery]);

  useEffect(() => {
    const onPopState = (event: PopStateEvent) => {
      if (platformReturnUrl || platformQuery) {
        void releaseOnlineRoom().then(() => exitToPlatform());
        return;
      }
      const next = (event.state as HistoryState | null)?.screen ?? "home";
      applyScreen(next);
    };
    window.addEventListener("popstate", onPopState);
    return () => window.removeEventListener("popstate", onPopState);
  }, [applyScreen, platformReturnUrl, platformQuery, exitToPlatform, releaseOnlineRoom]);

  const handleSelectMode = (_nextMode: TPlayMode) => {
    // Online button → server matchmaking, then smooth Computer Game play
    setMode("online");
    goTo("onlineSetup");
  };

  const handleStart = (config: IGameConfig) => {
    setGameConfig(config);
    setInitialTurn(0);
    goTo("game");
  };

  const handleOfflineGameOver = useCallback(
    (entries: IResultEntry[]) => {
      setResultEntries(entries);
      goTo("results");
    },
    [goTo]
  );

  const handlePlayAgain = async () => {
    await releaseOnlineRoom();
    setResultEntries([]);
    setGameConfig(null);
    goTo("onlineSetup", true);
  };

  const handleOnlineExit = useCallback(async () => {
    await releaseOnlineRoom();
    if (platformReturnUrl || platformQuery) {
      exitToPlatform();
      return;
    }
    goTo("onlineSetup", true);
  }, [releaseOnlineRoom, platformReturnUrl, platformQuery, exitToPlatform, goTo]);

  const handleOnlineQueued = useCallback(
    (g: IGuestUser, roomId: string, roomCode: string) => {
      setGuest(g);
      setOnlineRoomId(roomId);
      setOnlineRoomCode(roomCode);
      setMode("online");
      goTo("onlineLobby");
    },
    [goTo]
  );

  /** Match found → server-authoritative OnlineGame (shared room state via WS/HTTP). */
  const handleOnlineStart = useCallback(
    (_room: IOnlineRoom, game?: IGameSnapshot | null) => {
      setOnlineSnapshot(game ?? null);
      setMode("online");
      goTo("onlineGame", true);
    },
    [goTo]
  );

  const handlePlatformReady = useCallback(
    (
      g: IGuestUser,
      returnUrl?: string | null,
      wallet?: { balance: number; entryFee: number; walletEnabled: boolean }
    ) => {
      setGuest(g);
      setMode("online");
      setPlatformSession(true);
      if (returnUrl) setPlatformReturnUrl(returnUrl);
      // The stake is fixed for every platform match, so the pot must show even
      // when no live wallet is configured (backend then keeps a local ledger).
      if (wallet) setEntryFee(wallet.entryFee || 0);
      if (wallet?.walletEnabled) {
        // Balance after debit happens server-side on queue — refresh approx
        setWalletBalance(
          Math.max(0, wallet.balance - (wallet.entryFee || 0))
        );
      }
      setPlatformQuery(null);
      window.history.replaceState({ screen: "home" }, "", "/");
      goTo("home", true);
    },
    [goTo]
  );

  if (platformError) {
    return (
      <div
        style={{
          minHeight: "var(--vv-height, 100dvh)",
          height: "var(--vv-height, 100dvh)",
          width: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          padding: 24,
          color: "#fff",
          textAlign: "center",
          fontFamily: "Fredoka, sans-serif",
          backgroundColor: "#0a2a5c",
          backgroundImage: "var(--bg-image)",
          backgroundSize: "cover",
          backgroundPosition: "center",
          boxSizing: "border-box",
        }}
      >
        <h2 style={{ marginBottom: 8 }}>
          {platformError === "Open this game from Aakda app"
            ? "Open this game from Aakda app"
            : "Can't start match"}
        </h2>
        <p style={{ opacity: 0.85 }}>{platformError}</p>
      </div>
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

  if (screen === "onlineGame" && guest && onlineRoomId) {
    return (
      <AppWrapper>
        <OnlineGame
          key={onlineRoomId}
          guest={guest}
          roomId={onlineRoomId}
          initialSnapshot={onlineSnapshot}
          walletBalance={walletBalance}
          entryFee={entryFee}
          onExit={handleOnlineExit}
          onPlayAgain={handlePlayAgain}
        />
      </AppWrapper>
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
          initialTurn={initialTurn}
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
      {screen === "home" && (
        <Home
          onPlay={() => goTo("onlineSetup")}
          userId={guest?.id}
        />
      )}
      {screen === "modes" && (
        <ModeSelect onBack={goBack} onSelect={handleSelectMode} />
      )}
      {screen === "setup" && (
        <Setup mode="computer" onBack={goBack} onStart={handleStart} />
      )}
      {screen === "onlineSetup" && (
        <OnlineSetup
          onBack={goBack}
          onQueued={handleOnlineQueued}
          platformGuest={platformSession ? guest : null}
          entryFee={entryFee}
          walletBalance={walletBalance}
        />
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
      {screen === "results" && (
        <Results
          entries={resultEntries}
          onPlayAgain={handlePlayAgain}
          onHome={goHome}
        />
      )}
    </AppWrapper>
  );
};

export default React.memo(App);
