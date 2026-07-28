import type { IGameSnapshot } from "./types";

function seatCount(snapshot: IGameSnapshot): number {
  return (
    snapshot.seatColors?.length ??
    snapshot.userIds?.length ??
    snapshot.usernames?.length ??
    snapshot.isBot?.length ??
    0
  );
}

/** Every seat is a real human (no bots in snapshot). */
export function isAllHumanMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot?.isBot?.length) return false;
  return snapshot.isBot.every((b) => !b);
}

/** Supported human online modes: 2-player or 4-player queue. */
export function isHumanOnlineMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot || !isAllHumanMatch(snapshot)) return false;
  const seats = seatCount(snapshot);
  return seats === 2 || seats === 4;
}

/** @deprecated use {@link isHumanOnlineMatch} */
export function isTwoPlayerHumanMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot || !isAllHumanMatch(snapshot)) return false;
  return seatCount(snapshot) === 2;
}

export function isFourPlayerHumanMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot || !isAllHumanMatch(snapshot)) return false;
  return seatCount(snapshot) === 4;
}
