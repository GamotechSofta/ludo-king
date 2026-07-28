import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import {
  cancelQueue,
  getRoomState,
  leaveRoom,
  markRoomReady,
} from "../../api/ludoApi";
import {
  preloadGameSounds,
  startMatchSearchLoop,
  stopMatchSearchLoop,
} from "../../utils/sounds";
import ludoKingLogo from "../../assets/ludo-king-logo.png";
import treasureChest from "../../assets/treasure-chest.png";
import Avatar from "../avatar";
import { displayPlayerName } from "./onlineSnapshotBoard";
import SearchingProfileScroll from "./SearchingProfileScroll";
import {
  getCountdownDisplay,
  getReadyStatusMessage,
  getSearchElapsedSec,
  getSearchRemainingSec,
  getSearchPhase,
  getSearchStatusMessage,
  type TSearchPhase,
} from "./matchSearchFlow";
import type { IGameSnapshot, IGuestUser, IOnlineRoom } from "./types";
import "./styles.css";

interface OnlineLobbyProps {
  guest: IGuestUser;
  roomId: string;
  roomCode: string;
  walletBalance?: number | null;
  entryFee?: number;
  onBack: () => void;
  onStart: (room: IOnlineRoom, game?: IGameSnapshot | null) => void;
}

type TRoomPlayer = NonNullable<IOnlineRoom["players"]>[number];

const formatTimer = (totalSec: number) => {
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
};

const playerDisplayName = (
  player: TRoomPlayer,
  usedNames: string[]
) =>
  displayPlayerName(
    player.username,
    `${player.userId}:${player.seatIndex}`,
    usedNames,
    !!player.bot
  );

const OnlineLobby = ({
  guest,
  roomId,
  onBack,
  onStart,
}: OnlineLobbyProps) => {
  const [room, setRoom] = useState<IOnlineRoom | null>(null);
  const [countdown, setCountdown] = useState<number | "GO" | null>(null);
  const [error, setError] = useState("");
  const [readyBusy, setReadyBusy] = useState(false);
  const [now, setNow] = useState(Date.now());
  const [searchStart] = useState(() => Date.now());
  const [joinFlash, setJoinFlash] = useState<string | null>(null);
  const [joiningIds, setJoiningIds] = useState<Set<string>>(new Set());
  const [prepMessage, setPrepMessage] = useState<string | null>(null);
  const [networkLost, setNetworkLost] = useState(false);
  const [searchPhase, setSearchPhase] = useState<TSearchPhase>("NEARBY");

  const prevPlayerIdsRef = useRef<Set<string>>(new Set());
  const prevPlayerCountRef = useRef(1);
  const failCountRef = useRef(0);
  const joinFlashTimerRef = useRef<number | null>(null);
  const pollRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    preloadGameSounds();
  }, []);

  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 250);
    return () => window.clearInterval(t);
  }, []);

  const clearJoinFlashTimer = () => {
    if (joinFlashTimerRef.current != null) {
      window.clearTimeout(joinFlashTimerRef.current);
      joinFlashTimerRef.current = null;
    }
  };

  const showJoinFlash = useCallback((message: string, ms = 2200) => {
    clearJoinFlashTimer();
    setJoinFlash(message);
    joinFlashTimerRef.current = window.setTimeout(() => {
      setJoinFlash(null);
      joinFlashTimerRef.current = null;
    }, ms);
  }, []);

  useEffect(() => {
    let alive = true;
    let started = false;

    const tick = async () => {
      try {
        const state = await getRoomState(roomId);
        if (!alive) return;

        failCountRef.current = 0;
        setNetworkLost(false);
        setRoom(state.room);
        if (state.searchPhase) {
          setSearchPhase(state.searchPhase as TSearchPhase);
        }

        if (
          state.room.status === "COMPLETED" ||
          state.displayStatus === "FINISHED"
        ) {
          setError("Previous match ended — go back and start a new one.");
          return;
        }

        if (typeof state.countdown === "number") {
          setCountdown(state.countdown <= 0 ? "GO" : state.countdown);
        } else if (state.room.countdownValue != null) {
          setCountdown(
            state.room.countdownValue <= 0 ? "GO" : state.room.countdownValue
          );
        } else if (!state.room.countdownEndsAt) {
          setCountdown(null);
        }

        if (
          (state.room.status === "IN_PROGRESS" ||
            state.displayStatus === "PLAYING") &&
          !started
        ) {
          if (!state.game) {
            setError("Match expired — go back and start a new game.");
            return;
          }
          started = true;
          onStart(state.room, state.game ?? null);
        }
      } catch (e) {
        if (!alive) return;
        failCountRef.current += 1;
        if (failCountRef.current >= 3) {
          setNetworkLost(true);
        } else {
          setError(e instanceof Error ? e.message : "Failed to load room");
        }
      }
    };

    pollRef.current = () => {
      void tick();
    };

    void tick();
    const id = window.setInterval(tick, 250);
    return () => {
      alive = false;
      window.clearInterval(id);
      clearJoinFlashTimer();
    };
  }, [roomId, onStart, showJoinFlash]);

  const seats = room?.maxPlayers || 4;
  const isReadyPhase = room?.status === "READY";
  const isSearching = room?.status === "WAITING";
  const me = room?.players?.find((p) => p.userId === guest.id);
  const iAmReady = !!me?.ready;

  const displayCountdown = useMemo(() => {
    const fromDeadline = getCountdownDisplay(room?.countdownEndsAt, now);
    if (fromDeadline != null) return fromDeadline;
    return countdown;
  }, [room?.countdownEndsAt, now, countdown]);

  const inCountdown = displayCountdown != null;
  const playersFound = room?.players?.length ?? 1;

  useEffect(() => {
    if (isSearching && !networkLost) {
      startMatchSearchLoop(0.55);
    } else {
      stopMatchSearchLoop();
    }
    return () => stopMatchSearchLoop();
  }, [isSearching, networkLost]);

  const searchTimerSec = useMemo(
    () =>
      getSearchElapsedSec(room?.fillDeadlineAt, now, searchStart),
    [room?.fillDeadlineAt, now, searchStart]
  );

  const searchRemainingSec = useMemo(
    () =>
      getSearchRemainingSec(room?.fillDeadlineAt, now, searchStart),
    [room?.fillDeadlineAt, now, searchStart]
  );

  const effectiveSearchPhase: TSearchPhase = isSearching
    ? searchPhase
    : getSearchPhase(searchTimerSec);

  const waitingForOthers =
    iAmReady &&
    isReadyPhase &&
    !inCountdown &&
    (room?.players ?? []).some(
      (p) => !p.bot && !p.ready && p.userId !== guest.id
    );

  const usedNames = useMemo(
    () => (room?.players ?? []).map((p) => p.username),
    [room?.players]
  );

  const mePlayer = room?.players?.find((p) => p.userId === guest.id);
  const meName = mePlayer
    ? playerDisplayName(mePlayer, usedNames.filter((n) => n !== mePlayer.username))
    : guest.username;

  const opponentSlots = useMemo(() => {
    const others =
      room?.players
        ?.filter((p) => p.userId !== guest.id)
        .sort((a, b) => a.seatIndex - b.seatIndex) ?? [];
    return Array.from({ length: Math.max(0, seats - 1) }, (_, i) => others[i] ?? null);
  }, [room?.players, guest.id, seats]);

  // Detect joins / leaves for animation + status flash
  useEffect(() => {
    const players = room?.players ?? [];
    const currentIds = new Set(players.map((p) => p.userId));
    const newIds = [...currentIds].filter(
      (id) => !prevPlayerIdsRef.current.has(id) && id !== guest.id
    );

    if (newIds.length > 0 && (isSearching || isReadyPhase)) {
      newIds.forEach((id, i) => {
        window.setTimeout(() => {
          setJoiningIds((prev) => new Set([...prev, id]));
          window.setTimeout(() => {
            setJoiningIds((prev) => {
              const next = new Set(prev);
              next.delete(id);
              return next;
            });
          }, 900);
        }, i * 320);
      });
      showJoinFlash(`Player Joined (${players.length}/${seats})`);
    }

    if (
      isSearching &&
      players.length < prevPlayerCountRef.current &&
      players.length > 0
    ) {
      showJoinFlash("Opponent left — searching again…");
    }

    prevPlayerIdsRef.current = currentIds;
    prevPlayerCountRef.current = players.length;
  }, [room?.players, guest.id, isSearching, isReadyPhase, seats, showJoinFlash]);

  const handleReady = useCallback(async () => {
    if (readyBusy || iAmReady) return;
    setReadyBusy(true);
    setError("");
    setPrepMessage(null);
    try {
      const updated = await markRoomReady(roomId, guest.id);
      setRoom(updated);
      if (updated.countdownEndsAt) {
        setCountdown(getCountdownDisplay(updated.countdownEndsAt, Date.now()) ?? 3);
      } else if (updated.countdownValue != null) {
        setCountdown(
          updated.countdownValue <= 0 ? "GO" : updated.countdownValue
        );
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not ready up");
    } finally {
      setReadyBusy(false);
    }
  }, [readyBusy, iAmReady, roomId, guest.id]);

  const handleBack = useCallback(() => {
    stopMatchSearchLoop();
    void cancelQueue(guest.id).catch(() => undefined);
    void leaveRoom(roomId, guest.id).catch(() => undefined);
    onBack();
  }, [roomId, guest.id, onBack]);

  const handleRetry = useCallback(() => {
    failCountRef.current = 0;
    setNetworkLost(false);
    setError("");
    pollRef.current?.();
  }, []);

  const searchStatus = getSearchStatusMessage(
    effectiveSearchPhase,
    playersFound,
    seats,
    joinFlash
  );

  const readyStatus = getReadyStatusMessage(
    prepMessage,
    waitingForOthers,
    iAmReady
  );

  const countdownLabel =
    displayCountdown === "GO"
      ? "GO!"
      : displayCountdown != null
        ? String(displayCountdown)
        : "";

  const renderAvatarFrame = (
    player: TRoomPlayer | null,
    options?: {
      searching?: boolean;
      compact?: boolean;
      scrollDelayMs?: number;
      slotKey?: string | number;
    }
  ) => {
    const {
      searching = false,
      compact = false,
      scrollDelayMs = 0,
      slotKey = 0,
    } = options ?? {};

    const isJoining = player ? joiningIds.has(player.userId) : false;
    const frameClass = [
      "find-players-avatar-frame",
      compact ? "compact" : "",
      searching ? "searching" : "",
      player ? "filled" : "",
      isJoining ? "joining" : "",
    ]
      .filter(Boolean)
      .join(" ");

    if (player) {
      return (
        <div className={frameClass} key={`slot-${slotKey}-${player.userId}`}>
          <div className="find-players-avatar-glow" aria-hidden />
          <Avatar
            photo={player.avatar}
            name={player.username}
            className="find-players-avatar"
          />
          {player.ready || player.bot ? (
            <span className="find-players-ready-badge" aria-label="Ready">
              ✓
            </span>
          ) : null}
        </div>
      );
    }

    if (searching) {
      return (
        <div className={frameClass} key={`slot-${slotKey}-search`}>
          <SearchingProfileScroll delayMs={scrollDelayMs} />
        </div>
      );
    }

    return (
      <div className={`${frameClass} empty`} key={`slot-${slotKey}-empty`}>
        <div className="find-players-avatar-placeholder" aria-hidden />
      </div>
    );
  };

  return (
    <div className="lobby find-players-lobby">
      <div className="find-players">
        <button
          className="find-players-back"
          type="button"
          onClick={handleBack}
          aria-label="Back"
          disabled={inCountdown}
        >
          ←
        </button>

        <img
          className="find-players-logo"
          src={ludoKingLogo}
          alt="Ludo King"
        />

        <h2 className="find-players-heading">ONLINE MULTIPLAYER</h2>

        <div className="find-players-me">
          <div className="find-players-avatar-frame filled">
            <Avatar
              photo={mePlayer?.avatar}
              name={meName}
              className="find-players-avatar"
            />
            {iAmReady ? (
              <span className="find-players-ready-badge" aria-label="Ready">
                ✓
              </span>
            ) : null}
          </div>
          <span className="find-players-name find-players-name-me">{meName}</span>
        </div>

        {inCountdown ? (
          <div
            className={`find-players-countdown ${
              displayCountdown === "GO" ? "go" : ""
            }`}
            aria-live="polite"
            key={countdownLabel}
          >
            {countdownLabel}
          </div>
        ) : seats === 2 ? (
          <>
            <div className="find-players-vs" aria-hidden>
              VS
            </div>
            <div className="find-players-opponent-single">
              {renderAvatarFrame(opponentSlots[0], {
                searching: isSearching && !opponentSlots[0],
                scrollDelayMs: 0,
                slotKey: 0,
              })}
              {opponentSlots[0] ? (
                <span
                  className={`find-players-name ${
                    joiningIds.has(opponentSlots[0].userId) ? "joining" : ""
                  }`}
                >
                  {playerDisplayName(
                    opponentSlots[0],
                    usedNames.filter((n) => n !== opponentSlots[0]!.username)
                  )}
                </span>
              ) : null}
            </div>
          </>
        ) : (
          <>
            <div className="find-players-chest" aria-hidden>
              <img
                className="find-players-chest-img"
                src={treasureChest}
                alt=""
              />
            </div>
            <div className="find-players-opponents-row">
              {opponentSlots.map((player, i) => (
                <div className="find-players-opponent-slot" key={i}>
                  {renderAvatarFrame(player, {
                    searching: isSearching && !player,
                    compact: true,
                    scrollDelayMs: i * 200,
                    slotKey: i,
                  })}
                  <span
                    className={`find-players-name ${
                      player && joiningIds.has(player.userId) ? "joining" : ""
                    }`}
                  >
                    {player
                      ? playerDisplayName(
                          player,
                          usedNames.filter((n) => n !== player.username)
                        )
                      : "\u00A0"}
                  </span>
                </div>
              ))}
            </div>
          </>
        )}

        {isSearching && !inCountdown ? (
          <>
            <div className="find-players-status" aria-live="polite">
              <span className="find-players-spinner" aria-hidden />
              {searchStatus.toUpperCase()}
            </div>
            <div className="find-players-timer">
              <span className="find-players-stopwatch" aria-hidden />
              <span>{formatTimer(searchRemainingSec)}</span>
            </div>
          </>
        ) : null}

        {isReadyPhase && !inCountdown ? (
          <>
            <div
              className="find-players-status find-players-status-match"
              aria-live="polite"
            >
              {readyStatus.toUpperCase()}
            </div>
            {!iAmReady ? (
              <button
                type="button"
                className="find-players-ready"
                disabled={readyBusy}
                onClick={() => void handleReady()}
              >
                {readyBusy ? "…" : "READY"}
              </button>
            ) : null}
          </>
        ) : null}

        {error && !networkLost ? (
          <p className="find-players-error">{error}</p>
        ) : null}
      </div>

      {networkLost ? (
        <div className="find-players-network-overlay" role="alertdialog">
          <div className="find-players-network-card">
            <h3>Connection Lost</h3>
            <p>Could not reach the matchmaking server.</p>
            <div className="find-players-network-actions">
              <button type="button" onClick={handleRetry}>
                Retry
              </button>
              <button type="button" className="secondary" onClick={handleBack}>
                Exit
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
};

export default React.memo(OnlineLobby);
