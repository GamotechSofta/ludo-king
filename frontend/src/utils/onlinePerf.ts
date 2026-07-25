/**
 * Lightweight online multiplayer performance probes (dev / optional).
 * Does not affect gameplay — only measures latency / FPS.
 */

const enabled =
  typeof process !== "undefined" &&
  process.env.NODE_ENV === "development";

type Sample = { name: string; ms: number; at: number };

const samples: Sample[] = [];
const MAX = 80;

let frames = 0;
let fpsWindowStart = 0;
let lastFps = 0;
let rafId = 0;

function push(name: string, ms: number) {
  if (!enabled) return;
  samples.push({ name, ms, at: performance.now() });
  if (samples.length > MAX) samples.shift();
}

export const onlinePerf = {
  markActionSent(kind: "roll" | "move") {
    if (!enabled) return;
    (window as unknown as { __ludoActionAt?: number }).__ludoActionAt =
      performance.now();
    push(`${kind}:sent`, 0);
  },

  markSnapshotApplied(fromWs: boolean, actionSeq?: number) {
    if (!enabled) return;
    const w = window as unknown as { __ludoActionAt?: number };
    if (w.__ludoActionAt) {
      push(fromWs ? "ws:rtt" : "http:rtt", performance.now() - w.__ludoActionAt);
      w.__ludoActionAt = undefined;
    }
    push("snap:apply", actionSeq ?? 0);
  },

  markRender(ms: number) {
    push("render", ms);
  },

  markServerHint(ms: number) {
    push("server", ms);
  },

  startFpsProbe() {
    if (!enabled || rafId) return;
    frames = 0;
    fpsWindowStart = performance.now();
    const loop = (t: number) => {
      frames += 1;
      if (t - fpsWindowStart >= 1000) {
        lastFps = frames;
        frames = 0;
        fpsWindowStart = t;
      }
      rafId = requestAnimationFrame(loop);
    };
    rafId = requestAnimationFrame(loop);
  },

  stopFpsProbe() {
    if (rafId) cancelAnimationFrame(rafId);
    rafId = 0;
  },

  getFps() {
    return lastFps;
  },

  dump() {
    if (!enabled) return [];
    return samples.slice();
  },
};
