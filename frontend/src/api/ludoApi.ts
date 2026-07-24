import type { IGameSnapshot, IGuestUser, IOnlineRoom } from "../components/lobby/types";

const API_BASE = process.env.REACT_APP_API_URL || "http://localhost:3000";

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers || {}),
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(text || res.statusText);
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
  json<IOnlineRoom>(`/api/rooms/${roomCode}/join`, {
    method: "POST",
    body: JSON.stringify({ userId, username }),
  });

export const getRoomState = (roomId: string) =>
  json<{ room: IOnlineRoom; game?: IGameSnapshot }>(
    `/api/rooms/${roomId}/state`
  );

export const getApiBase = () => API_BASE;
