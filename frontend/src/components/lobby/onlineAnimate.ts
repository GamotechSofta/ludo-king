/**
 * Cell-by-cell pawn animation.
 * Exactly one board box per step, slowly: hop → land → pause → next box.
 */

import { flushSync } from "react-dom";

export type AnimCancel = { cancelled: boolean };

/** Backgrounded tabs stop firing rAF — without this the animation never ends. */
const FRAME_TIMEOUT_MS = 100;

export function nextFrame(): Promise<number> {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (t: number) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timer);
      resolve(t);
    };
    const timer = window.setTimeout(
      () => finish(performance.now()),
      FRAME_TIMEOUT_MS
    );
    requestAnimationFrame(finish);
  });
}

export async function rafDelay(ms: number, cancel?: AnimCancel): Promise<void> {
  if (ms <= 0) return;
  const start = performance.now();
  while (performance.now() - start < ms) {
    if (cancel?.cancelled) return;
    await nextFrame();
  }
}

/** @deprecated use runCellByCellSteps */
export async function runRafSteps(
  count: number,
  stepMs: number,
  onStep: (index: number) => void,
  cancel?: AnimCancel
): Promise<void> {
  return runCellByCellSteps(count, stepMs, onStep, cancel);
}

/**
 * Move through `count` boxes, one at a time.
 * Never skips a cell. Waits for each hop (+ optional pause) before the next.
 */
export async function runCellByCellSteps(
  count: number,
  stepMs: number,
  onStep: (index: number) => void,
  cancel?: AnimCancel,
  pauseAfterLandMs = 80
): Promise<void> {
  if (count <= 0) return;

  for (let i = 0; i < count; i++) {
    if (cancel?.cancelled) return;

    // Paint exactly this one box
    flushSync(() => {
      onStep(i);
    });
    await nextFrame();
    if (cancel?.cancelled) return;

    // Let CSS slide finish on this box only
    await rafDelay(stepMs, cancel);
    if (cancel?.cancelled) return;

    // Brief rest on the box so the eye registers "one step"
    if (pauseAfterLandMs > 0 && i < count - 1) {
      await rafDelay(pauseAfterLandMs, cancel);
    }
  }
}
