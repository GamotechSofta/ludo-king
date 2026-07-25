import { $ } from "./helpers";
import { BASE_WIDTH, BASE_HEIGHT } from "./constants";

const getViewport = () => {
  const vv = window.visualViewport;
  return {
    width: Math.round(vv?.width ?? window.innerWidth),
    height: Math.round(vv?.height ?? window.innerHeight),
  };
};

/**
 * Lock html/body/#root to the real visible viewport.
 * Critical inside iOS Safari iframes where 100vh/% height collapses.
 */
export const applyViewportFill = () => {
  const { width: vw, height: vh } = getViewport();
  const h = `${vh}px`;
  const w = `${vw}px`;

  document.documentElement.style.setProperty("--vv-height", h);
  document.documentElement.style.setProperty("--vv-width", w);
  document.documentElement.style.height = h;
  document.documentElement.style.width = w;
  document.body.style.height = h;
  document.body.style.width = w;
  document.body.style.minHeight = h;

  const root = $("#root") as HTMLElement | null;
  const hasGameFrame = !!$(".container");

  if (root && !hasGameFrame) {
    // Platform / full-bleed screens (no scaled canvas)
    root.style.cssText = [
      `width:${w}`,
      `height:${h}`,
      `min-height:${h}`,
      "display:flex",
      "flex-direction:column",
      "align-items:stretch",
      "justify-content:stretch",
      "overflow:auto",
      "-webkit-overflow-scrolling:touch",
    ].join(";");
  }
};

/** Fit fixed game frame into viewport — works on mobile + desktop + iframe */
export const applyScreenResize = () => {
  applyViewportFill();

  const container = $(".container") as HTMLElement | null;
  const screen = $(".screen") as HTMLElement | null;
  const root = $("#root") as HTMLElement | null;
  if (!container || !screen) return;

  const { width: vw, height: vh } = getViewport();
  // Prefer filling width on phones/iframes so the frame isn't tiny; fall back if too tall
  let scale = vw / BASE_WIDTH;
  if (BASE_HEIGHT * scale > vh) {
    scale = Math.min(vw / BASE_WIDTH, vh / BASE_HEIGHT);
  }

  const outW = Math.floor(BASE_WIDTH * scale);
  const outH = Math.floor(BASE_HEIGHT * scale);

  container.style.width = `${outW}px`;
  container.style.height = `${outH}px`;
  container.style.margin = "0 auto";
  container.style.overflow = "hidden";

  screen.style.width = `${BASE_WIDTH}px`;
  screen.style.height = `${BASE_HEIGHT}px`;
  screen.style.transform = `scale(${scale})`;
  screen.style.transformOrigin = "top left";

  if (root) {
    root.style.cssText = [
      "width:100%",
      `height:${vh}px`,
      `min-height:${vh}px`,
      "display:flex",
      "align-items:center",
      "justify-content:center",
      "overflow:hidden",
    ].join(";");
  }

  document.documentElement.style.setProperty("--app-scale", String(scale));
};

let debounceTimer = 0;

const resizeScreen = () => {
  window.clearTimeout(debounceTimer);
  debounceTimer = window.setTimeout(applyScreenResize, 40);
};

export default resizeScreen;
