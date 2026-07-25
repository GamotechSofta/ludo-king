import "./index.css";
import bgLudoPattern from "./assets/bg-ludo-pattern.webp";
import App from "./App";
import React from "react";
import ReactDOM from "react-dom/client";
import resizeScreen, { applyScreenResize } from "./utils/resize-screen";
import * as serviceWorkerRegistration from "./serviceWorkerRegistration";

// SockJS / STOMP expect Node-style global in the browser
(window as unknown as { global: Window }).global = window;

document.documentElement.style.setProperty(
  "--bg-image",
  `url(${bgLudoPattern})`
);

const root = ReactDOM.createRoot(
  document.getElementById("root") as HTMLElement
);

root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

// Fit layout after first paint (refresh / mobile / iOS iframe)
const fit = () => {
  applyScreenResize();
  requestAnimationFrame(applyScreenResize);
};
requestAnimationFrame(fit);
window.addEventListener("load", fit);
window.addEventListener("resize", resizeScreen);
window.addEventListener("orientationchange", resizeScreen);
window.visualViewport?.addEventListener("resize", resizeScreen);
window.visualViewport?.addEventListener("scroll", resizeScreen);

// Always unregister so browser refresh loads the latest build (no stale PWA cache)
serviceWorkerRegistration.unregister();
