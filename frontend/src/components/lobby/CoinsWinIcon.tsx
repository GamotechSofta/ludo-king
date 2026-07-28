import React, { useId } from "react";

const CoinsWinIcon = () => {
  const uid = useId().replace(/:/g, "");
  const face = `coinFace-${uid}`;
  const edge = `coinEdge-${uid}`;
  const rim = `coinRim-${uid}`;

  return (
    <svg
      className="find-match-win-icon"
      viewBox="0 0 48 48"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden
    >
      <defs>
        <linearGradient id={face} x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#FFE566" />
          <stop offset="55%" stopColor="#F5B820" />
          <stop offset="100%" stopColor="#E8940A" />
        </linearGradient>
        <linearGradient id={edge} x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#D48808" />
          <stop offset="100%" stopColor="#A86400" />
        </linearGradient>
        <linearGradient id={rim} x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#FFC940" />
          <stop offset="100%" stopColor="#E09008" />
        </linearGradient>
      </defs>

      <ellipse cx="24" cy="42" rx="14" ry="3.5" fill="rgba(0,0,0,0.22)" />

      <g stroke="#7A4A00" strokeLinejoin="round">
        <g transform="translate(6, 22)">
          <ellipse cx="10" cy="14" rx="9" ry="3.2" fill={`url(#${edge})`} strokeWidth="1.1" />
          <rect x="1" y="8.5" width="18" height="5.5" fill={`url(#${edge})`} strokeWidth="0" />
          <ellipse cx="10" cy="8.5" rx="9" ry="3.2" fill={`url(#${face})`} strokeWidth="1.1" />
          <ellipse cx="10" cy="8.5" rx="6.5" ry="2.3" fill="none" stroke="#C88A00" strokeWidth="0.8" />
        </g>

        <g transform="translate(6, 16)">
          <ellipse cx="10" cy="14" rx="9" ry="3.2" fill={`url(#${edge})`} strokeWidth="1.1" />
          <rect x="1" y="8.5" width="18" height="5.5" fill={`url(#${edge})`} strokeWidth="0" />
          <ellipse cx="10" cy="8.5" rx="9" ry="3.2" fill={`url(#${face})`} strokeWidth="1.1" />
          <ellipse cx="10" cy="8.5" rx="6.5" ry="2.3" fill="none" stroke="#C88A00" strokeWidth="0.8" />
        </g>

        <g transform="translate(6, 10)">
          <ellipse cx="10" cy="14" rx="9" ry="3.2" fill={`url(#${edge})`} strokeWidth="1.1" />
          <rect x="1" y="8.5" width="18" height="5.5" fill={`url(#${edge})`} strokeWidth="0" />
          <ellipse cx="10" cy="8.5" rx="9" ry="3.2" fill={`url(#${face})`} strokeWidth="1.1" />
          <ellipse cx="10" cy="8.5" rx="6.5" ry="2.3" fill="none" stroke="#C88A00" strokeWidth="0.8" />
        </g>

        <g transform="translate(26, 14) rotate(18 8 10)">
          <ellipse cx="8" cy="16" rx="8" ry="2.8" fill={`url(#${edge})`} strokeWidth="1.1" />
          <rect x="0" y="11" width="16" height="5" fill={`url(#${edge})`} strokeWidth="0" />
          <ellipse cx="8" cy="11" rx="8" ry="2.8" fill={`url(#${rim})`} strokeWidth="1.1" />
          <ellipse cx="8" cy="11" rx="5.8" ry="2" fill="none" stroke="#C88A00" strokeWidth="0.8" />
          <path
            d="M8 8.2 L9.4 10.4 L11.9 10.8 L10.1 12.5 L10.5 15 L8 13.7 L5.5 15 L5.9 12.5 L4.1 10.8 L6.6 10.4 Z"
            fill="#FFE880"
            stroke="#C88A00"
            strokeWidth="0.6"
          />
        </g>
      </g>
    </svg>
  );
};

export default React.memo(CoinsWinIcon);
