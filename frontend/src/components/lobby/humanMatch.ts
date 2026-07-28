import type { IGameSnapshot } from "./types";

/** Every seat is a real human (no bots in snapshot). */
export function isAllHumanMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot?.isBot?.length) return false;
  return snapshot.isBot.every((b) => !b);
}

/** 2-player online match with two humans — stricter sync / forfeit rules apply. */
export function isTwoPlayerHumanMatch(
  snapshot: IGameSnapshot | null | undefined
): boolean {
  if (!snapshot) return false;
  const seats =
    snapshot.seatColors?.length ??
    snapshot.userIds?.length ??
    snapshot.usernames?.length ??
    0;
  return seats === 2 && isAllHumanMatch(snapshot);
}
