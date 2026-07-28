/** Must match backend RoomService.FILL_SECONDS */
export const SEARCH_FILL_SECONDS = 15;

export type TSearchPhase = "NEARBY" | "EXPANDED" | "WIDE" | "BOT_FILL";

export const getSearchPhase = (elapsedSec: number): TSearchPhase => {
  if (elapsedSec < 5) return "NEARBY";
  if (elapsedSec < 10) return "EXPANDED";
  if (elapsedSec < 15) return "WIDE";
  return "BOT_FILL";
};

export const getSearchRemainingSec = (
  fillDeadlineAt: string | undefined,
  nowMs: number,
  fallbackStartMs: number
): number => {
  if (fillDeadlineAt) {
    const deadlineMs = new Date(fillDeadlineAt).getTime();
    if (Number.isFinite(deadlineMs)) {
      return Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
    }
  }
  const elapsed = Math.min(
    SEARCH_FILL_SECONDS,
    Math.floor((nowMs - fallbackStartMs) / 1000)
  );
  return Math.max(0, SEARCH_FILL_SECONDS - elapsed);
};

export const getSearchElapsedSec = (
  fillDeadlineAt: string | undefined,
  nowMs: number,
  fallbackStartMs: number
): number => {
  if (fillDeadlineAt) {
    const deadlineMs = new Date(fillDeadlineAt).getTime();
    if (Number.isFinite(deadlineMs)) {
      const remaining = Math.max(0, Math.ceil((deadlineMs - nowMs) / 1000));
      return Math.min(SEARCH_FILL_SECONDS, SEARCH_FILL_SECONDS - remaining);
    }
  }
  return Math.min(
    SEARCH_FILL_SECONDS,
    Math.floor((nowMs - fallbackStartMs) / 1000)
  );
};

export const getSearchStatusMessage = (
  phase: TSearchPhase,
  playersFound: number,
  maxPlayers: number,
  joinFlash: string | null
): string => {
  if (joinFlash) return joinFlash;
  if (playersFound >= maxPlayers) return "Almost Ready...";
  if (playersFound > 1) return `${playersFound}/${maxPlayers} Players Found`;

  switch (phase) {
    case "NEARBY":
      return "Finding nearby players...";
    case "EXPANDED":
      return "Expanding search radius...";
    case "WIDE":
      return "Searching wider regions...";
    case "BOT_FILL":
      return "Adding players...";
    default:
      return "Searching...";
  }
};

/** Must match backend RoomService.COUNTDOWN_SECONDS (1s per label). */
export const COUNTDOWN_TOTAL_MS = 4000;
export const COUNTDOWN_STEP_MS = 1000;

export function getCountdownDisplay(
  countdownEndsAt: string | undefined,
  nowMs: number
): number | "GO" | null {
  if (!countdownEndsAt) return null;
  const endMs = new Date(countdownEndsAt).getTime();
  if (!Number.isFinite(endMs)) return null;

  const remaining = endMs - nowMs;
  if (remaining <= 0) return "GO";

  const elapsed = COUNTDOWN_TOTAL_MS - remaining;
  const step = Math.floor(elapsed / COUNTDOWN_STEP_MS);
  const labels: (number | "GO")[] = [3, 2, 1, "GO"];
  if (step < 0) return 3;
  if (step >= labels.length) return "GO";
  return labels[step];
}

export const getReadyStatusMessage = (
  prepMessage: string | null,
  waitingForOthers: boolean,
  iAmReady: boolean
): string => {
  if (prepMessage) return prepMessage;
  if (waitingForOthers) return "Waiting for other players…";
  if (iAmReady) return "Preparing Match...";
  return "MATCH FOUND!";
};
