import "./index.css";
import bgLudoPattern from "./assets/bg-ludo-pattern.webp";
import App from "./App";
import React from "react";
import ReactDOM from "react-dom/client";
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

// Always unregister so browser refresh loads the latest build (no stale PWA cache)
serviceWorkerRegistration.unregister();
