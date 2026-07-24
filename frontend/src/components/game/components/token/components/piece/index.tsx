import "./styles.css";
import { SIZE_TILE } from "../../../../../../utils/constants";
import React from "react";
import type { TColors } from "../../../../../../interfaces";

interface PieceProps {
  color: TColors;
  style?: React.CSSProperties;
  index?: number;
  debug?: boolean;
}

const PAWN_COLORS: Record<
  string,
  {
    insetLight: string;
    insetMid: string;
    insetDark: string;
    baseLight: string;
    baseMid: string;
    baseDark: string;
    baseWell: string;
  }
> = {
  red: {
    insetLight: "#ff6b63",
    insetMid: "#e53935",
    insetDark: "#b71c1c",
    baseLight: "#ef5350",
    baseMid: "#e53935",
    baseDark: "#c62828",
    baseWell: "#8e0000",
  },
  blue: {
    insetLight: "#64b5f6",
    insetMid: "#1e88e5",
    insetDark: "#0d47a1",
    baseLight: "#42a5f5",
    baseMid: "#1e88e5",
    baseDark: "#1565c0",
    baseWell: "#0a3d91",
  },
  green: {
    insetLight: "#81c784",
    insetMid: "#43a047",
    insetDark: "#1b5e20",
    baseLight: "#66bb6a",
    baseMid: "#43a047",
    baseDark: "#2e7d32",
    baseWell: "#145214",
  },
  yellow: {
    insetLight: "#ffee58",
    insetMid: "#fdd835",
    insetDark: "#f9a825",
    baseLight: "#ffee58",
    baseMid: "#fdd835",
    baseDark: "#fbc02d",
    baseWell: "#c49000",
  },
};

/** Ludo King–style map-pin pawn (colored base + silver pin + glossy inset). */
const Piece = ({ color, style = {}, index = 0, debug = false }: PieceProps) => {
  const colorKey = (color || "RED").toLowerCase();
  const palette = PAWN_COLORS[colorKey] || PAWN_COLORS.red;
  const uid = `${colorKey}-${index}`;

  return (
    <div
      className={`game-token-piece ${colorKey}`}
      style={{ width: SIZE_TILE, height: SIZE_TILE, ...style }}
    >
      <svg
        className="game-token-piece-svg"
        viewBox="0 0 64 80"
        preserveAspectRatio="xMidYMax meet"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        <defs>
          <radialGradient id={`pawn-base-${uid}`} cx="35%" cy="30%" r="70%">
            <stop offset="0%" stopColor={palette.baseLight} />
            <stop offset="65%" stopColor={palette.baseMid} />
            <stop offset="100%" stopColor={palette.baseDark} />
          </radialGradient>
          <linearGradient id={`pawn-pin-${uid}`} x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="40%" stopColor="#f0f0f0" />
            <stop offset="100%" stopColor="#bdbdbd" />
          </linearGradient>
          <radialGradient id={`pawn-inset-${uid}`} cx="32%" cy="28%" r="68%">
            <stop offset="0%" stopColor={palette.insetLight} />
            <stop offset="50%" stopColor={palette.insetMid} />
            <stop offset="100%" stopColor={palette.insetDark} />
          </radialGradient>
          <filter
            id={`pawn-shadow-${uid}`}
            x="-35%"
            y="-15%"
            width="170%"
            height="150%"
          >
            <feDropShadow
              dx="1.2"
              dy="2.2"
              stdDeviation="1.6"
              floodColor="#000000"
              floodOpacity="0.42"
            />
          </filter>
        </defs>

        <circle
          cx="32"
          cy="66"
          r="13.5"
          fill={`url(#pawn-base-${uid})`}
          stroke="rgba(0,0,0,0.4)"
          strokeWidth="1.3"
        />
        <circle
          cx="32"
          cy="66"
          r="8.2"
          fill={palette.baseWell}
          opacity="0.5"
        />

        <g filter={`url(#pawn-shadow-${uid})`}>
          <path
            d="M32 6
               C19.5 6 10 16.2 10 28.5
               C10 42 32 66 32 66
               C32 66 54 42 54 28.5
               C54 16.2 44.5 6 32 6 Z"
            fill={`url(#pawn-pin-${uid})`}
            stroke="#222222"
            strokeWidth="1.5"
            strokeLinejoin="round"
          />
          <circle
            cx="32"
            cy="27"
            r="12.5"
            fill={`url(#pawn-inset-${uid})`}
            stroke="rgba(0,0,0,0.3)"
            strokeWidth="1.1"
          />
          <ellipse
            cx="27"
            cy="21"
            rx="4.2"
            ry="3.2"
            fill="#ffffff"
            opacity="0.38"
          />
        </g>
      </svg>
      {debug && (
        <span style={{ width: SIZE_TILE, height: SIZE_TILE }}>{index}</span>
      )}
    </div>
  );
};

export default React.memo(Piece);
