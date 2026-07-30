/**
 * Cell-by-cell pawn animation.
 * Exactly one board box per step: hop → land → pause → next box.
 * Uses rAF yields (not flushSync) so React can paint without blocking the main thread.
 */

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

/** Wait until React has committed and the browser has painted. */
export async function afterPaint(): Promise<void> {
  await nextFrame();
  await nextFrame();
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

    onStep(i);
    // Yield so React commits and the browser paints this cell before waiting
    await afterPaint();
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
