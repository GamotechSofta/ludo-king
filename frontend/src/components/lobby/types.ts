export type TLobbyScreen =
  | "home"
  | "modes"
  | "setup"
  | "game"
  | "onlineSetup"
  | "onlineLobby"
  | "onlineGame"
  | "results";

export type TPlayMode = "computer" | "local" | "online";

export interface ILobbyPlayer {
  id: string;
  name: string;
  isBot: boolean;
}

export interface IGameConfig {
  mode: TPlayMode;
  totalPlayers: 2 | 3 | 4;
  players: ILobbyPlayer[];
}

export interface IGuestUser {
  id: string;
  username: string;
  name: string;
  rating: number;
  avatarId: string;
}

export interface IOnlineRoom {
  id: string;
  roomCode: string;
  status: string;
  maxPlayers: number;
  players: Array<{
    userId: string;
    username: string;
    color: string;
    bot: boolean;
    seatIndex: number;
    connectionStatus?: string;
    ready?: boolean;
    avatar?: string;
    rating?: number;
  }>;
  fillDeadlineAt?: string;
  region?: string;
  countdownEndsAt?: string;
  countdownValue?: number;
  reconnectDeadlineAt?: string;
}

export interface IGameSnapshot {
  roomId: string;
  phase: string;
  currentSeatIndex: number;
  currentColor: string;
  diceValue: number;
  diceList: number[];
  tokenPositions: Record<string, number[]>;
  /** Color per seat index (server order after shuffle). Prefer over Object.keys. */
  seatColors?: string[];
  legalTokenIndexes: number[];
  legalMoves?: Array<{ tokenIndex: number; diceIndex: number }>;
  userIds?: string[];
  usernames?: string[];
  finished?: boolean[];
  /** AFK removed after 2 missed turns in the match. */
  eliminated?: boolean[];
  isBot?: boolean[];
  standings?: number[];
  winnerSeat?: number | null;
  turnStartedAt?: string;
  turnTimeoutSeconds?: number;
  turnSecondsRemaining?: number;
  consecutiveSixes?: number;
  /** Per-seat streak of consecutive turn timeouts (AFK warning). */
  consecutiveTimeouts?: number[];
  bonusRoll?: boolean;
  lastActionType?: string | null;
  lastActionSeat?: number | null;
  lastActionTokenIndex?: number | null;
  lastActionDice?: number | null;
  lastActionFrom?: number | null;
  lastActionTo?: number | null;
  actionSeq?: number;
}

export interface IResultEntry {
  /** 1 = winner, 0 = lost (no rank 2/3/4). */
  rank: number;
  name: string;
  color?: string;
  isBot?: boolean;
  isYou?: boolean;
  lost?: boolean;
  won?: boolean;
  /** AFK eliminated or left before finish. */
  exited?: boolean;
  /** Still active mid-match (elimination summary). */
  playing?: boolean;
}
