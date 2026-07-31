import React, { useEffect, useState } from "react";
import {
  isMusicEnabled,
  startBackgroundMusic,
  subscribeMusicEnabled,
  toggleMusicEnabled,
} from "../../utils/sounds";
import "./musicToggle.css";

interface MusicToggleProps {
  className?: string;
  /** When turning music back on, start BGM immediately. */
  resumeOnEnable?: boolean;
}

const MusicToggle = ({ className = "", resumeOnEnable = true }: MusicToggleProps) => {
  const [enabled, setEnabled] = useState(isMusicEnabled);

  useEffect(() => subscribeMusicEnabled(setEnabled), []);

  const onToggle = () => {
    const next = toggleMusicEnabled();
    if (next && resumeOnEnable) {
      startBackgroundMusic();
    }
  };

  return (
    <button
      type="button"
      className={`music-toggle ${enabled ? "is-on" : "is-off"} ${className}`.trim()}
      onClick={onToggle}
      aria-pressed={enabled}
      aria-label={enabled ? "Turn music off" : "Turn music on"}
      title={enabled ? "Music on" : "Music off"}
    >
      <svg viewBox="0 0 24 24" width="22" height="22" aria-hidden>
        <path
          fill="currentColor"
          d="M23 0l-15.996 3.585v13.04c-2.979-.589-6.004 1.671-6.004 4.154 0 2.137 1.671 3.221 3.485 3.221 2.155 0 4.512-1.528 4.515-4.638v-10.9l12-2.459v8.624c-2.975-.587-6 1.664-6 4.141 0 2.143 1.715 3.232 3.521 3.232 2.14 0 4.476-1.526 4.479-4.636V0z"
        />
        {!enabled && (
          <path
            fill="none"
            stroke="currentColor"
            strokeWidth="2.4"
            strokeLinecap="round"
            d="M3.2 3.2l17.6 17.6"
          />
        )}
      </svg>
    </button>
  );
};

export default React.memo(MusicToggle);
