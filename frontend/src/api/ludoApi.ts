import type { IGameSnapshot, IGuestUser, IOnlineRoom } from "../components/lobby/types";

const API_BASE = process.env.REACT_APP_API_URL || "http://localhost:3000";

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    },
  });
  if (!res.ok) {
    const text = await res.text();
    let message = text || res.statusText;
    try {
      const parsed = JSON.parse(text);
      if (parsed && typeof parsed.error === "string") {
        message = parsed.error;
      }
    } catch {
      // not JSON — keep raw text
    }
    throw new Error(message);
  }
  return res.json() as Promise<T>;
}

export const createGuest = (username: string) =>
  json<IGuestUser>("/api/guest", {
    method: "POST",
    body: JSON.stringify({ username }),
  });

export const queueMatch = (
  userId: string,
  username: string,
  maxPlayers: number,
  stakeTier = "FREE"
) =>
  json<{ status: string; roomId: string; roomCode: string; room: IOnlineRoom }>(
    "/api/rooms/queue",
    {
      method: "POST",
      body: JSON.stringify({ userId, username, maxPlayers, stakeTier }),
    }
  );

export const createPrivateRoom = (
  userId: string,
  username: string,
  maxPlayers: number
) =>
  json<IOnlineRoom>("/api/rooms/private", {
    method: "POST",
    body: JSON.stringify({ userId, username, maxPlayers }),
  });

export const joinRoom = (roomCode: string, userId: string, username: string) =>
  json<IOnlineRoom>(`/api/rooms/${encodeURIComponent(roomCode)}/join`, {
    method: "POST",
    body: JSON.stringify({ userId, username }),
  });

export const leaveRoom = (roomId: string, userId: string) =>
  json<{ ok: boolean }>(`/api/rooms/${roomId}/leave`, {
    method: "POST",
    body: JSON.stringify({ userId }),
  });

export const markRoomReady = (roomId: string, userId: string) =>
  json<IOnlineRoom>(`/api/rooms/${roomId}/ready`, {
    method: "POST",
    body: JSON.stringify({ userId }),
  });

export const getRoomState = (roomId: string) =>
  json<{
    room: IOnlineRoom;
    game?: IGameSnapshot;
    displayStatus?: string;
    playersJoined?: number;
    maxPlayers?: number;
    readyCount?: number;
    allReady?: boolean;
    countdown?: number;
  }>(`/api/rooms/${roomId}/state`);

/** Always returns a game snapshot (rehydrates engine if needed). */
export const ensureGameSnapshot = (roomId: string) =>
  json<IGameSnapshot>(`/api/rooms/${roomId}/game`);

export const httpRollDice = (roomId: string, userId: string) =>
  json<IGameSnapshot>(`/api/rooms/${roomId}/game/roll`, {
    method: "POST",
    body: JSON.stringify({ userId }),
  });

export const httpMoveToken = (
  roomId: string,
  userId: string,
  tokenIndex: number,
  diceIndex: number
) =>
  json<IGameSnapshot>(`/api/rooms/${roomId}/game/move`, {
    method: "POST",
    body: JSON.stringify({ userId, tokenIndex, diceIndex }),
  });

export const getApiBase = () => API_BASE;

export interface IPlatformLaunchResult {
  success: boolean;
  userId: string;
  gameId: string;
  sessionId?: string | null;
  displayName: string;
  returnUrl?: string | null;
  walletEnabled?: boolean;
  entryFee?: number;
  betOptions?: number[];
  balance?: number | null;
  balanceError?: string | null;
}

/** Bind Aakda launch params into Spring HTTP session (cookies). */
export const platformLaunch = (body: {
  userId: string;
  gameId?: string;
  sessionId?: string;
  token?: string;
  returnUrl?: string;
  displayName?: string;
}) =>
  json<IPlatformLaunchResult>("/api/platform/launch", {
    method: "POST",
    body: JSON.stringify(body),
    credentials: "include",
  });

export const fetchWalletBalance = (userId: string) =>
  json<{
    success: boolean;
    balance: number;
    walletEnabled?: boolean;
    currency?: string;
  }>(`/api/platform/balance?userId=${encodeURIComponent(userId)}`);

export const fetchEconomy = () =>
  json<{
    success: boolean;
    walletEnabled: boolean;
    entryFee: number;
    gameId: string;
  }>("/api/platform/economy");

