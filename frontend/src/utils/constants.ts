import type {
  IActionsMoveToken,
  IActionsTurn,
  IOptionsGame,
  IPredefinedChatMessages,
  ISocketError,
  ISocketListenActions,
  TDicevalues,
} from "../interfaces";

/**
 * Dimensiones del juego...
 */
export const BASE_HEIGHT = 732;
export const BASE_WIDTH = 412;
export const SIZE_BOARD = BASE_WIDTH - 22;
export const SIZE_TILE = SIZE_BOARD / 15;
export const DIE_SIZE_TOOLTIP = SIZE_TILE + SIZE_TILE * 0.15;

// Para los tipos de boards.
/**
 * RGYB -> Rojo, verde, amarillo y azul.
 * BRGY -> Azul, Rojom verde y amarillo.
 * YBRG -> Amarillo, azul, rojo y verde
 * GYBR -> Verde, amarillo, azul y rojo...
 */
export enum EBoardColors {
  RGYB = "RGYB",
  BRGY = "BRGY",
  YBRG = "YBRG",
  GYBR = "GYBR",
}

/**
 * Para el nombre de los colores que se manejan en el board...
 */
export enum EColors {
  RED = "RED",
  BLUE = "BLUE",
  YELLOW = "YELLOW",
  GREEN = "GREEN",
}

/**
 * Guarda los sufijos de los colores...
 */
export enum ESufixColors {
  R = "RED",
  B = "BLUE",
  Y = "YELLOW",
  G = "GREEN",
}

/**
 * Ubicación de los diferentes juagdores en el board...
 */
export enum EPositionGame {
  BOTTOM_LEFT = "BOTTOM_LEFT",
  TOP_LEFT = "TOP_LEFT",
  TOP_RIGHT = "TOP_RIGHT",
  BOTTOM_RIGHT = "BOTTOM_RIGHT",
}

export enum EPositionProfiles {
  TOP = "TOP",
  BOTTOM = "BOTTOM",
}

export enum EPositionProfile {
  LEFT = "LEFT",
  RIGHT = "RIGHT",
}

/**
 * Tipos de celdas en las cuales pueden estar una ficha/token...
 */
export enum EtypeTile {
  JAIL = "JAIL",
  NORMAL = "NORMAL",
  EXIT = "EXIT",
  END = "END",
}

export enum EActionsBoardGame {
  "ROLL_DICE" = "ROLL_DICE",
  "SELECT_TOKEN" = "SELECT_TOKEN",
  "OPPONENT_LEAVE" = "OPPONENT_LEAVE",
  "CHAT_MESSAGE" = "CHAT_MESSAGE",
  "START_GAME" = "START_GAME",
  "DONE_DICE" = "DONE_DICE",
  "DONE_TOKEN_MOVEMENT" = "DONE_TOKEN_MOVEMENT",
}

export enum ENextStepGame {
  "ROLL_DICE_AGAIN" = "ROLL_DICE_AGAIN",
  "MOVE_TOKENS_AGAIN" = "MOVE_TOKENS_AGAIN",
  "NEXT_TURN" = "NEXT_TURN",
}

export enum ETypeGame {
  "OFFLINE" = "OFFLINE",
  "ONLINE" = "ONLINE",
}

/**
 * Profundidades para los tokens...
 */
export const BASE_ZINDEX_TOKEN = 1;
export const ZINDEX_TOKEN_SELECT = 7;

/**
 * Valor por defecto de las acciones del turno en el board...
 */
export const DEFAULT_VALUE_ACTION_TURN: IActionsTurn = {
  timerActivated: false,
  disabledDice: true,
  diceValue: 0,
  diceList: [],
  diceRollNumber: 0,
  actionsBoardGame: EActionsBoardGame.ROLL_DICE,
  showDice: false,
  isDisabledUI: false,
};

/**
 * Valor del dado para salir de la carcel...
 */
export const DICE_VALUE_GET_OUT_JAIL: TDicevalues = 6;

/**
 * El máximo de dados que se pueden lanzar por turno...
 */
export const MAXIMUM_DICE_PER_TURN = 3;

/**
 * El total de tokes que se podrán mostrar en una celda,
 * después de este valor no se mostrará y se hará visible,
 * otro componente que mostrará la totalidad de tokens en la celda.
 */
export const MAXIMUM_VISIBLE_TOKENS_PER_CELL = 4;

/**
 * Intervalo del tiempo para el cronometro del juego...
 */
export const TIME_INTERVAL_CHRONOMETER = 50; // org (50), debug: 10,5,0.5

/**
 * Interval between pawn cell steps (ms).
 * One box at a time — slow enough to clearly see each cell.
 */
export const TOKEN_MOVEMENT_INTERVAL_VALUE = 380;

/** Online: same cell-by-cell cadence as offline. */
export const ONLINE_TOKEN_MOVEMENT_INTERVAL_VALUE = 380;

/** Extra pause on each cell after the hop lands (ms). */
export const TOKEN_STEP_PAUSE_MS = 40;

/**
 * Dice spin duration (seconds) for react-dice-complete.
 * Classic-like roll feel (~0.65s).
 */
export const ROLL_TIME_VALUE = 0.65;

/** Pause before the die handoff to the next player (online). */
export const ONLINE_TURN_PASS_DELAY_MS = 1000;

/**
 * Valor para el delay que se aplica antes de enviar la confirmación al socket
 * que el movimiento de un token ha terminado...
 */
export const DELAY_DONE_TOKEN_MOVEMENT_SOCKET = 500;

/**
 * Tiempo para mostrar el modal del game over del juego...
 */
export const WAIT_SHOW_MODAL_GAME_OVER = 800;

/**
 * Puerto de conexión al socket, sólo aplicable para desarrollo...
 */
export const SOCKET_PORT_DEV = 3000;

/**
 * El estado inicial del estado que maneja el movimiento del token...
 */
export const INITIAL_ACTIONS_MOVE_TOKEN: IActionsMoveToken = {
  isRunning: false,
  tokenIndex: 0,
  totalCellsMove: 0,
  cellsCounter: 0,
};

/**
 * Sufijos para las posiciones del ranking...
 */
export const PREFIX_RANKING = ["st", "nd", "rd", "th"];

/**
 * El valor del rango para la sala.
 */
export const ROOM_RANGE = 5;

/**
 * URL de la API del backend, para la autenticación de los usuarios...
 */
export const API_URL = "/api/me";

/**
 * URL para cerrar sesión...
 */
export const API_LOGOUT = "/api/logout";

/**
 * Tipos de jugabilidad online...
 */
export enum TYPES_ONLINE_GAMEPLAY {
  NONE = "NONE",
  JOIN_EXISTING_ROOM = "JOIN_EXISTING_ROOM",
  JOIN_ROOM = "JOIN_ROOM",
  CREATE_ROOM = "CREATE_ROOM",
}

/**
 * Para los tipos de errores de la sala...
 */
export enum SocketErrors {
  INVALID_ROOM = "INVALID_ROOM",
  INVALID_COLOR = "INVALID_COLOR",
  INVALID_USER = "INVALID_USER",
  UNAUTHENTICATED = "UNAUTHENTICATED",
  AUTHENTICATED = "AUTHENTICATED",
}

/**
 * Mensajes relacionados a lo tipos de errores en los sockets...
 */
export const SOCKET_ERROR_MESSAGES: ISocketError = {
  INVALID_ROOM: "The room is not valid",
  INVALID_COLOR: "Invalid token color",
  INVALID_USER: "Invalid user",
  UNAUTHENTICATED: "User is not authenticated",
  AUTHENTICATED: "User already authenticated",
};

/**
 * Los tipos de mensajes en el chat...
 */
export enum TYPES_CHAT_MESSAGES {
  EMOJI = "EMOJI",
  TEXT = "TEXT",
}

/**
 * Valor inicial del estado para los eventos del socket...
 */
export const INITIAL_SOCKET_LISTEN_ACTIONS: ISocketListenActions = {
  change: false,
  type: EActionsBoardGame.ROLL_DICE,
  data: {
    [EActionsBoardGame.ROLL_DICE]: 1,
    [EActionsBoardGame.SELECT_TOKEN]: {
      diceIndex: -1,
      tokenIndex: -1,
    },
    [EActionsBoardGame.OPPONENT_LEAVE]: "",
    [EActionsBoardGame.CHAT_MESSAGE]: {
      userID: "",
      type: TYPES_CHAT_MESSAGES.EMOJI,
      messageIndex: 0,
    },
    [EActionsBoardGame.DONE_DICE]: false,
    [EActionsBoardGame.DONE_TOKEN_MOVEMENT]: ENextStepGame.NEXT_TURN,
  },
};

/**
 * Los mensajes predefinidos dle chat...
 */
export const PREDEFINED_CHAT_MESSAGES: IPredefinedChatMessages = {
  [TYPES_CHAT_MESSAGES.TEXT]: [
    "Hi",
    "Nice move!",
    "Oh no!",
    "Good game!",
    "Best of luck!",
    "Oops...",
    "Thanks!",
    "Bye bye",
    "Play fast",
    "Sorry!",
    "Catch me if you can!",
    "Please do not kill me",
    "Unlucky",
    "Not again!",
    "You're lucky!",
    "I will eat you",
  ].map((value, index) => ({ index, value })),
  [TYPES_CHAT_MESSAGES.EMOJI]: ["😅", "🤬", "😭", "🤯", "🥺", "😩"].map(
    (value, index) => ({ index, value })
  ),
};

/**
 * Para las opciones del juego...
 */
export enum EOptionsGame {
  SOUND = "SOUND",
  MUSIC = "MUSIC",
  CHAT = "CHAT",
}

export enum ESounds {
  ROLL_DICE = "ROLL_DICE",
  TOKEN_MOVE = "TOKEN_MOVE",
  GET_SIX = "GET_SIX",
  SAFE_ZONE = "SAFE_ZONE",
  TOKEN_JAIL = "TOKEN_JAIL",
  CHAT = "CHAT",
  USER_OFFLINE = "USER_OFFLINE",
  USER_ONLINE = "USER_ONLINE",
  GAMER_OVER = "GAMER_OVER",
  CLICK = "CLICK",
}

export const INITIAL_OPTIONS_GAME: IOptionsGame = {
  [EOptionsGame.SOUND]: true,
  [EOptionsGame.MUSIC]: true,
  [EOptionsGame.CHAT]: true,
};

/**
 * Crear variables de css que se utilizaran para la creación del board...
 */
document.documentElement.style.setProperty("--base-height", `${BASE_HEIGHT}px`);
document.documentElement.style.setProperty("--base-width", `${BASE_WIDTH}px`);
document.documentElement.style.setProperty("--size-board", `${SIZE_BOARD}px`);
document.documentElement.style.setProperty("--size-tile", `${SIZE_TILE}px`);
