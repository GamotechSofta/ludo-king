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

/** Larger pin; aspect matches SVG so tip centers correctly. */
export const PAWN_WIDTH = SIZE_TILE * 1.28;
export const PAWN_HEIGHT = PAWN_WIDTH * (76 / 64);
/**
 * Anchor on the colored base disc (cy=66), not the sharp tip (y=70),
 * so the pawn sits centered on nest soft-pads / path cells.
 */
export const PAWN_TIP_RATIO = 66 / 76;

const Piece = ({ color, style = {}, index = 0, debug = false }: PieceProps) => {
  const colorKey = (color || "RED").toLowerCase();
  const palette = PAWN_COLORS[colorKey] || PAWN_COLORS.red;
  const uid = `${colorKey}-${index}`;

  const baseStyle: React.CSSProperties = {
    width: PAWN_WIDTH,
    height: PAWN_HEIGHT,
    left: (SIZE_TILE - PAWN_WIDTH) / 2,
    top: SIZE_TILE / 2 - PAWN_HEIGHT * PAWN_TIP_RATIO,
  };

  return (
    <div
      className={`game-token-piece ${colorKey}`}
      style={{ ...baseStyle, ...style }}
    >
      <svg
        className="game-token-piece-svg"
        viewBox="0 0 64 76"
        preserveAspectRatio="xMidYMid meet"
        xmlns="http://www.w3.org/2000/svg"
        aria-hidden
      >
        <defs>
          <radialGradient id={`pawn-base-${uid}`} cx="35%" cy="30%" r="70%">
            <stop offset="0%" stopColor={palette.baseLight} />
            <stop offset="65%" stopColor={palette.baseMid} />
            <stop offset="100%" stopColor={palette.baseDark} />
          </radialGradient>
          <linearGradient
            id={`pawn-pin-${uid}`}
            x1="20%"
            y1="0%"
            x2="80%"
            y2="100%"
          >
            <stop offset="0%" stopColor="#ffffff" />
            <stop offset="45%" stopColor="#f0f0f0" />
            <stop offset="100%" stopColor="#b0b0b0" />
          </linearGradient>
          <radialGradient id={`pawn-inset-${uid}`} cx="32%" cy="28%" r="68%">
            <stop offset="0%" stopColor={palette.insetLight} />
            <stop offset="50%" stopColor={palette.insetMid} />
            <stop offset="100%" stopColor={palette.insetDark} />
          </radialGradient>
        </defs>

        {/* Colored base disc under tip */}
        <circle
          cx="32"
          cy="66"
          r="11"
          fill={`url(#pawn-base-${uid})`}
          stroke="rgba(0,0,0,0.35)"
          strokeWidth="1.1"
        />
        <circle
          cx="32"
          cy="66"
          r="6.5"
          fill={palette.baseWell}
          opacity="0.45"
        />

        <path
          d="M32 4
             C20.5 4 12 14 12 25.5
             C12 38 32 70 32 70
             C32 70 52 38 52 25.5
             C52 14 43.5 4 32 4 Z"
          fill={`url(#pawn-pin-${uid})`}
          stroke="#2a2a2a"
          strokeWidth="1.35"
          strokeLinejoin="round"
        />
        <circle
          cx="32"
          cy="24"
          r="11.5"
          fill={`url(#pawn-inset-${uid})`}
          stroke="rgba(0,0,0,0.28)"
          strokeWidth="1"
        />
        <ellipse
          cx="27.5"
          cy="18.5"
          rx="3.8"
          ry="2.8"
          fill="#ffffff"
          opacity="0.4"
        />
      </svg>
      {debug && <span>{index}</span>}
    </div>
  );
};

export default React.memo(Piece);
