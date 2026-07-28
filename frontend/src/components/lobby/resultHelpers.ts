import type { IResultEntry } from "./types";

export const RANK_WIN = 1;
export const RANK_LOST = 0;

export function isWinnerEntry(entry: IResultEntry): boolean {
  return entry.won === true || entry.rank === RANK_WIN;
}

export function partitionResults(entries: IResultEntry[]): {
  winner: IResultEntry | undefined;
  lost: IResultEntry[];
} {
  const winner = entries.find(isWinnerEntry);
  const lost = entries.filter((e) => !isWinnerEntry(e));
  return { winner, lost };
}

export function lostStatusLabel(entry: IResultEntry): string {
  if (entry.exited) return "LOST (Exited)";
  return "LOST";
}
