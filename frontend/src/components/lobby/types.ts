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
  }>;
  fillDeadlineAt?: string;
}

export interface IGameSnapshot {
  roomId: string;
  phase: string;
  currentSeatIndex: number;
  currentColor: string;
  diceValue: number;
  diceList: number[];
  tokenPositions: Record<string, number[]>;
  legalTokenIndexes: number[];
  legalMoves?: Array<{ tokenIndex: number; diceIndex: number }>;
  userIds?: string[];
  usernames?: string[];
  finished?: boolean[];
  isBot?: boolean[];
  standings?: number[];
  winnerSeat?: number | null;
  turnStartedAt?: string;
  turnTimeoutSeconds?: number;
  turnSecondsRemaining?: number;
  consecutiveSixes?: number;
  bonusRoll?: boolean;
}

export interface IResultEntry {
  rank: number;
  name: string;
  color?: string;
  isBot?: boolean;
  isYou?: boolean;
}
