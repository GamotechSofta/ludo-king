export type TLobbyScreen = "home" | "modes" | "setup" | "game";

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
