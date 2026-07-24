import { $ } from "./helpers";
import { BASE_WIDTH, BASE_HEIGHT } from "./constants";

const getViewport = () => {
  const vv = window.visualViewport;
  return {
    width: vv?.width ?? window.innerWidth,
    height: vv?.height ?? window.innerHeight,
  };
};

/** Fit fixed game frame into viewport — works on mobile + desktop + refresh */
export const applyScreenResize = () => {
  const container = $(".container") as HTMLElement | null;
  const screen = $(".screen") as HTMLElement | null;
  const root = $("#root") as HTMLElement | null;
  if (!container || !screen) return;

  const { width: vw, height: vh } = getViewport();
  const scale = Math.min(vw / BASE_WIDTH, vh / BASE_HEIGHT);

  // Outer box matches scaled size so page does not scroll / clip
  container.style.width = `${Math.floor(BASE_WIDTH * scale)}px`;
  container.style.height = `${Math.floor(BASE_HEIGHT * scale)}px`;
  container.style.margin = "0 auto";
  container.style.overflow = "hidden";

  // Inner keeps design coords; CSS transform scales visually
  screen.style.width = `${BASE_WIDTH}px`;
  screen.style.height = `${BASE_HEIGHT}px`;
  screen.style.transform = `scale(${scale})`;
  screen.style.transformOrigin = "top left";

  if (root) {
    root.style.cssText = [
      "width:100%",
      "height:100%",
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
