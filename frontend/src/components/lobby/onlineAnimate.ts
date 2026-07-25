/**
 * High-performance cell-by-cell pawn stepping for online play.
 * Uses requestAnimationFrame (not chained setTimeout) for stable timing.
 */

export type AnimCancel = { cancelled: boolean };

/** Wait until the next animation frame. */
export function nextFrame(): Promise<number> {
  return new Promise((resolve) => {
    requestAnimationFrame((t) => resolve(t));
  });
}

/**
 * Run `count` steps, invoking `onStep(index)` at ~stepMs intervals via rAF.
 * First step runs immediately so movement feels instant.
 */
export async function runRafSteps(
  count: number,
  stepMs: number,
  onStep: (index: number) => void | Promise<void>,
  cancel?: AnimCancel
): Promise<void> {
  if (count <= 0) return;

  await onStep(0);
  if (count === 1 || cancel?.cancelled) return;

  let stepIndex = 1;
  let stepStartedAt = performance.now();

  return new Promise((resolve) => {
    const tick = async (now: number) => {
      if (cancel?.cancelled) {
        resolve();
        return;
      }
      if (now - stepStartedAt >= stepMs) {
        stepStartedAt = now;
        await onStep(stepIndex);
        stepIndex += 1;
        if (stepIndex >= count || cancel?.cancelled) {
          resolve();
          return;
        }
      }
      requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
  });
}

/** Soft delay that yields to rAF instead of blocking the main thread with timers. */
export async function rafDelay(ms: number, cancel?: AnimCancel): Promise<void> {
  if (ms <= 0) return;
  const start = performance.now();
  while (performance.now() - start < ms) {
    if (cancel?.cancelled) return;
    await nextFrame();
  }
}
