// import { Socket } from "socket.io-client";
import {
  EActionsBoardGame,
  EBoardColors,
  EColors,
  ENextStepGame,
  EOptionsGame,
  EPositionGame,
  EPositionProfile,
  EPositionProfiles,
  ESounds,
  ESufixColors,
  ETypeGame,
  EtypeTile,
  SocketErrors,
  TYPES_CHAT_MESSAGES,
  TYPES_ONLINE_GAMEPLAY,
} from "../utils/constants";

export type TBoardColors = keyof typeof EBoardColors;
export type TColors = keyof typeof EColors;
export type TSufixColors = keyof typeof ESufixColors;
export type TDicevalues = 1 | 2 | 3 | 4 | 5 | 6;
export type TPositionGame = keyof typeof EPositionGame;
export type TtypeTile = keyof typeof EtypeTile;
export type TPositionProfiles = keyof typeof EPositionProfiles;
export type TPositionProfile = keyof typeof EPositionProfile;
export type TActionsBoardGame = keyof typeof EActionsBoardGame;
export type TTypeGame = keyof typeof ETypeGame;
export type TTotalPlayers = 2 | 3 | 4;
export type TTypesOnlineGameplay = keyof typeof TYPES_ONLINE_GAMEPLAY;
export type TSocketErrors = keyof typeof SocketErrors;
export type ISocketError = Record<TSocketErrors, string>;
export type ITypeChatMessage = keyof typeof TYPES_CHAT_MESSAGES;
export type IEOptionsGame = keyof typeof EOptionsGame;
export type IESounds = keyof typeof ESounds;
export type IENextStepGame = keyof typeof ENextStepGame;

export type IPredefinedChatMessages = Record<
  ITypeChatMessage,
  { index: number; value: string }[]
>;

export interface ICoordinate {
  x: number;
  y: number;
}

export interface IPositionsItems {
  index: number;
  coordinate: ICoordinate;
}

export interface IPoint {
  x: number;
  y: number;
  increaseX: number;
  increaseY: number;
  total: number;
  indexBase: number;
}

export type TFinalPositionsValues = Record<TPositionGame, IPositionsItems[]>;
export type TExitTilesValues = Record<TPositionGame, IPoint>;

export interface IPositionGame {
  exitTileIndex: number;
  exitTiles: IPositionsItems[];
  finalPositions: IPositionsItems[];
  startPositions: IPositionsItems[];
  startTileIndex: number;
}

export type TLocationBoardElements = Record<TPositionGame, IPositionGame>;

/**
 * Para los handles de las funciones del componente de Profile...
 */
export type ThandleTimer = (ends: boolean, playerIndex?: number) => void;
export type ThandleSelectDice = (
  diceValue?: TDicevalues,
  isActionSocket?: boolean
) => void;
export type ThandleMuteChat = (playerIndex: number) => void;
export type ThandleDoneDice = (isActionSocket?: boolean) => void;

export interface IProfileHandlers {
  handleTimer: ThandleTimer;
  handleSelectDice: ThandleSelectDice;
  handleDoneDice: ThandleDoneDice;
  handleMuteChat: ThandleMuteChat;
}

export interface IUser {
  id: string;
  name: string;
  isBot?: boolean;
  isOnline?: boolean;
  photo?: string;
  socketID?: string;
}

export interface IPlayer extends IUser {
  isOffline: boolean;
  index: number;
  finished: boolean;
  ranking: number;
  color: TColors;
  isMuted?: boolean;
  chatMessage?: string;
  typeMessage?: ITypeChatMessage;
  counterMessage: number;
}

export interface IDiceList {
  key: number;
  value: TDicevalues;
}

/**
 * totalTokens determina la cantidad de tokens que hay en la celda del token actual,
 * por defecto es 1 si no se pasa..
 * position: determina la posición en la que se muestra el token,
 * sólo se aplica si el valor de totalTokens es mayor que uno.
 * positionTile es el número de la celda en el board
 */
export interface IToken {
  color: TColors;
  coordinate: ICoordinate;
  typeTile: TtypeTile;
  positionTile: number;
  index: number;
  diceAvailable: IDiceList[];
  canSelectToken: boolean;
  totalTokens: number;
  position: number;
  enableTooltip: boolean;
  isMoving: boolean;
  animated: boolean;
  /** Instant reposition without CSS transition (animation start cell). */
  snapPlace?: boolean;
}

export interface IListTokens {
  index: number;
  positionGame: TPositionGame;
  tokens: IToken[];
}

export interface IActionsTurn {
  timerActivated: boolean;
  disabledDice: boolean;
  showDice: boolean;
  diceValue: 0 | TDicevalues;
  diceList: IDiceList[];
  diceRollNumber: number;
  isDisabledUI: boolean;
  actionsBoardGame?: TActionsBoardGame;
  /** Trailing consecutive sixes this turn (offline single-die flow). */
  consecutiveSixes?: number;
  /** Unique roll id so turn changes never treat a new roll as a duplicate. */
  rollId?: string;
  /** Online: seconds left on the current turn (server-synced). */
  turnSecondsRemaining?: number | null;
  /** Online: full turn length in seconds (default 20). */
  turnTimeoutSeconds?: number;
}

export type TTokenByPositionType = Record<TtypeTile, IToken[]>;

export interface IActionsMoveToken {
  isRunning: boolean;
  tokenIndex: number;
  totalCellsMove: number;
  cellsCounter: number;
}

export type TShowTotalTokens = Record<number, number>;

export interface IGameOver {
  showModal: boolean;
  gameOver: boolean;
}
export interface DataOfflineGame {
  initialTurn: number;
  users: IUser[];
  totalPlayers: TTotalPlayers;
  boardColor: TBoardColors;
}

export interface IAuthOptions {
  socialName: string;
  routerURL: string;
}

export interface IAuth {
  isAuth: boolean;
  authOptions: IAuthOptions[];
  user?: IUser;
  serviceError?: boolean;
}

export interface IDataPlayWithFriends {
  type: TTypesOnlineGameplay;
  roomName: string;
  totalPlayers: TTotalPlayers;
  initialColor?: TColors;
}

export interface IDataSocketUser {
  id: string;
  name: string;
}
export interface IDataSocket {
  type: TTypesOnlineGameplay;
  totalPlayers: TTotalPlayers | 0;
  roomName: string;
  initialColor?: TColors;
  playAsGuest: boolean;
  user: IDataSocketUser;
}

/**
 * Interface de los datos del usuario que llegan por el socket
 */
export interface IUserSocket {
  id: string;
  name: string;
  photo?: string;
  socketID: string;
  color: TColors;
}

/**
 * Interface de la data que llega del socket
 */
export interface IDataRoom {
  initialTurnUserID: string;
  isFull: boolean;
  roomName: string;
  users: IUserSocket[];
  totalPlayers: TTotalPlayers;
}

/**
 * Interface que se le pasa al componente de Game...
 */
export interface IDataOnline {
  totalPlayers: TTotalPlayers;
  initialTurn: number;
  users: IUser[];
  boardColor: TBoardColors;
  roomName: string;
  typeGame: TTypeGame;
  //   socket: Socket;
}

/**
 * Establece el orden en que se mostrará en la UI
 */
export type TDataRoomUserOrder = Record<number, IUserSocket>;

/**
 * Interface para el estado que guarda la información para renderizar
 * los usuarios que se están conectando...
 */
export interface IDataRoomSocket {
  isFull: boolean;
  boardColor: TBoardColors;
  totalPlayers: TTotalPlayers;
  orderPlayers: TDataRoomUserOrder;
}

export interface ISelectTokenValues {
  diceIndex: number;
  tokenIndex: number;
}

export interface ISocketListenChatMessage {
  userID: string;
  type: ITypeChatMessage;
  messageIndex: number;
}

export interface ISocketListenData {
  [EActionsBoardGame.ROLL_DICE]: TDicevalues;
  [EActionsBoardGame.SELECT_TOKEN]: ISelectTokenValues;
  [EActionsBoardGame.OPPONENT_LEAVE]: string;
  [EActionsBoardGame.CHAT_MESSAGE]: ISocketListenChatMessage;
  [EActionsBoardGame.DONE_DICE]: boolean;
  [EActionsBoardGame.DONE_TOKEN_MOVEMENT]: IENextStepGame;
}
/**
 * Interface de la data que llega del socket al cliente...
 */
export interface ISocketListenActions {
  change: boolean;
  type: TActionsBoardGame;
  data: ISocketListenData;
}

/**
 * Interface para la data que se envía del cliente al socket...
 */
export interface ISocketActions {
  type: TActionsBoardGame;
  roomName: string;
  [EActionsBoardGame.ROLL_DICE]: TDicevalues;
  [EActionsBoardGame.SELECT_TOKEN]: ISelectTokenValues;
  [EActionsBoardGame.OPPONENT_LEAVE]: string;
  [EActionsBoardGame.CHAT_MESSAGE]: ISocketListenChatMessage;
  [EActionsBoardGame.DONE_DICE]: boolean;
  [EActionsBoardGame.DONE_TOKEN_MOVEMENT]: IENextStepGame;
}

/**
 * Para las opciones del juego...
 */
export type IOptionsGame = Record<IEOptionsGame, boolean>;
export interface IOptionsContext {
  optionsGame: IOptionsGame;
  toogleOptions: (type: IEOptionsGame) => void;
  playSound: (type: IESounds) => void;
}

/**
 * Tipo para el serviworker
 */
export interface IServiceWorker {
  serviceWorkerInitialized?: boolean;
  serviceWorkerUpdated?: boolean;
  serviceWorkerRegistration?: ServiceWorkerRegistration;
}
