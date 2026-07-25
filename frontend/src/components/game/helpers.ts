import { randomNumber } from "../../utils/helpers";
// import { TOKENS_JAIN_AND_OUTSITE } from "./components/debug/states";
import {
  EActionsBoardGame,
  EPositionGame,
  ESufixColors,
  EtypeTile,
} from "../../utils/constants";
import {
  POSITION_ELEMENTS_BOARD,
  POSITION_TILES,
} from "../../utils/positions-board";
import cloneDeep from "lodash.clonedeep";
import type {
  IActionsTurn,
  IListTokens,
  IPlayer,
  IToken,
  IUser,
  TBoardColors,
  TColors,
  TDicevalues,
  TPositionGame,
  TSufixColors,
  TTotalPlayers,
  TtypeTile,
} from "../../interfaces";

/**
 * Dependiendo del total de jugadores se duvuelen los colores que corresponde a las
 * posiciones donde quedará cada jugador...
 * @param boardColor
 * @param totalPlayers
 * @returns
 */
const getPlayersColors = (
  boardColor: TBoardColors,
  totalPlayers: TTotalPlayers
) => {
  /**
   * Se divide el nombre de cada una de las letras correspondientes a los colores...
   */
  const splitColor = boardColor.split("");
  /**
   * Se obtiene el nombre de los colores en relación al sufijo...
   */
  const colors = splitColor.map((v) => ESufixColors[v as TSufixColors]);

  /**
   * Dependiendo del total de jugadores se duvuelen los colores que corresponde a las
   * posiciones donde quedará cada jugador...
   */
  if (totalPlayers === 2) {
    return [colors[0], colors[2]];
  }

  if (totalPlayers === 3) {
    return [colors[0], colors[1], colors[2]];
  }

  return colors;
};

/**
 * Dependiendo del número de jugadores, devuelve los valores de posiciones
 * para cada token...
 * @param totalPlayers
 */
const getTokensPositionsOnBoard = (totalPlayers: TTotalPlayers) => {
  /**
   * Dependiendo la cantidad de jugadores, se devuelve el valor de posiciones...
   */
  if (totalPlayers === 2) {
    return [EPositionGame.BOTTOM_LEFT, EPositionGame.TOP_RIGHT];
  }

  if (totalPlayers === 3) {
    return [
      EPositionGame.BOTTOM_LEFT,
      EPositionGame.TOP_LEFT,
      EPositionGame.TOP_RIGHT,
    ];
  }

  return [
    EPositionGame.BOTTOM_LEFT,
    EPositionGame.TOP_LEFT,
    EPositionGame.TOP_RIGHT,
    EPositionGame.BOTTOM_RIGHT,
  ];
};

/**
 * Devuelve el listado de coordendas, dependiendo del tipo de celda
 * además de la posición en el board
 * @param tileType
 * @param positionGame
 * @param index
 * @returns
 */
const getCoordinatesByTileType = (
  tileType: TtypeTile,
  positionGame: TPositionGame,
  index: number
) => {
  if (tileType === EtypeTile.JAIL) {
    return POSITION_ELEMENTS_BOARD[positionGame].startPositions[index]
      .coordinate;
  }

  if (tileType === EtypeTile.NORMAL) {
    return POSITION_TILES[index].coordinate;
  }

  if (tileType === EtypeTile.EXIT) {
    return POSITION_ELEMENTS_BOARD[positionGame].exitTiles[index].coordinate;
  }

  // END
  return POSITION_ELEMENTS_BOARD[positionGame].finalPositions[index].coordinate;
};

/**
 * Función que genera la data para ubicar los tokens en la cárcel...
 * @param positionGame
 * @param color
 * @param canSelectToken
 */
const getTokensInJail = (
  positionGame: TPositionGame,
  color: TColors,
  canSelectToken: boolean
) => {
  const tokens: IToken[] = [];

  for (let i = 0; i < 4; i++) {
    /**
     * Se obtiene la coordenada de posición para los tokens,
     * en este caso se requuiere las relacionadas a la cárcel...
     */
    const coordinate = getCoordinatesByTileType(
      EtypeTile.JAIL,
      positionGame,
      i
    );

    tokens.push({
      color,
      coordinate,
      typeTile: EtypeTile.JAIL,
      positionTile: i,
      index: i,
      diceAvailable: [],
      totalTokens: 1,
      position: 1,
      enableTooltip: false,
      isMoving: false,
      animated: false,
      canSelectToken,
    });
  }

  return tokens;
};

/**
 * Valida si el botón del dado estaría bloqueado...
 * @param indexTurn
 * @param players
 * @returns
 */
export const validateDisabledDice = (indexTurn: number, players: IPlayer[]) => {
  /**
   * Se obtiene la información del jugador que tiene el turno...
   */
  const { isOnline, isBot } = players[indexTurn];

  let disabledDice = !!(isBot || isOnline);

  /**
   * Si es un jugador online y es el jugador uno (0) eso quiere decir
   * que se puede habilitar el botón...
   */
  if (isOnline && indexTurn === 0) {
    disabledDice = false;
  }

  return disabledDice;
};

/**
 * Función que generá la data inicial de los jugadores del juego
 * @param users
 * @param boardColor
 * @param totalPlayers
 * @returns
 */
export const getInitialDataPlayers = (
  users: IUser[],
  boardColor: TBoardColors,
  totalPlayers: TTotalPlayers
) => {
  const players: IPlayer[] = [];

  /**
   * Se obtiene la distrubiución de colores, dependiendo del total de jugadores
   * y de la distrubución del colores que está el board...
   */
  const playersColors = getPlayersColors(boardColor, totalPlayers);

  /**
   * Se genera la data de los players...
   */
  for (let i = 0; i < totalPlayers; i++) {
    /**
     * Se le adiciona el índice y además el color a la información de user que le llega al componente...
     */
    players.push({
      index: i,
      color: playersColors[i],
      finished: false,
      isOffline: false,
      isMuted: false,
      chatMessage: "",
      counterMessage: 0,
      ranking: 0,
      ...users[i],
    });
  }

  return players;
};

/**
 * Función que establece la información inicial para la acciones de cada turno...
 * @param indexTurn
 * @param players
 * @returns
 */
export const getInitialActionsTurnValue = (
  indexTurn: number,
  players: IPlayer[]
): IActionsTurn => ({
  // Offline pass-and-play: no turn timer (online uses server 20s timeout).
  timerActivated: false,
  disabledDice: validateDisabledDice(indexTurn, players),
  showDice: true,
  diceValue: 0,
  diceList: [],
  diceRollNumber: 0,
  isDisabledUI: false,
  actionsBoardGame: EActionsBoardGame.ROLL_DICE,
  consecutiveSixes: 0,
  rollId: undefined,
});

/**
 * Función que devuelve el valor aleatorio de un dado...
 * @returns
 */
export const randomValueDice = () => randomNumber(1, 6) as TDicevalues;

/**
 * Obtiene un valor aleatorio del dado,
 * además detiene el cronometro y bloquea el botón del dado...
 * @param actionsTurn
 * @param diceValue
 * @returns
 */
export const getRandomValueDice = (
  actionsTurn: IActionsTurn,
  diceValue?: TDicevalues
) => {
  const copyActionsTurn = cloneDeep(actionsTurn);
  /**
   * Se obtiene el valor del dado de forma aleatorio...
   * si el valor del dado llega se toma ese valor, si no se obtiene aleatorio,
   * se hace esto pata el caso de la jugabilidad online, en la cual el valor del dado,
   * es determinado por un cliente remoto.
   */
  copyActionsTurn.diceValue = diceValue || randomValueDice();

  /**
   * Se indica que le cronometro se debe detener...
   */
  copyActionsTurn.timerActivated = false;

  /**
   * Se bloquea el botón del dado para prevenir nuevos lanzamientos...
   */
  copyActionsTurn.disabledDice = true;

  /**
   * Se incrementa el valor del lanzamiento por si el valor del dado no cambia
   */
  const diceRollNumber = copyActionsTurn.diceRollNumber;

  /**
   * Se valida que sólo incremente hasta 10 para así evitar estar almancenado
   * un número grande que no se refelja en el UI
   */
  const newDiceRollNumber = diceRollNumber + 1 >= 10 ? 1 : diceRollNumber + 1;
  copyActionsTurn.diceRollNumber = newDiceRollNumber;
  copyActionsTurn.rollId = undefined;

  return copyActionsTurn;
};

/**
 * Función que genera la dada inicial de los tokens,
 * inicialmente inicia en la cárcel...
 * @param boardColor
 * @param totalPlayers
 * @param players
 * @returns
 */
export const getInitialPositionTokens = (
  boardColor: TBoardColors,
  totalPlayers: TTotalPlayers,
  players: IPlayer[]
) => {
  // Se obtiene los colores según el color del board...
  const playersColors = getPlayersColors(boardColor, totalPlayers);
  // Se obtiene las posciones para cada token, según la cantidad de jugadores...
  const tokensPositions = getTokensPositionsOnBoard(totalPlayers);
  // Para guardar los tokens...
  const listTokens: IListTokens[] = [];

  for (let i = 0; i < totalPlayers; i++) {
    /**
     * El usuario actual cuando se está jugando online, siempre estará en la
     * posición 0, es el jugador número uno en el tablero...
     */
    const isCurrentOnlineUser = i === 0;
    /**
     * Se extrae el valor para saber si es un bot o si es un jugador online...
     */
    const { isBot = false, isOnline = false } = players[i];
    /**
     * Se indica que puede seleccionar el token, si no es bot y además
     * el jugador online es el actual...
     */
    const canSelectToken = isOnline ? isCurrentOnlineUser : !isBot;

    /**
     * Se extrae el color que tendrá el token...
     */
    const color = playersColors[i];

    /**
     * Se extra la posición en el board...
     */
    const positionGame = tokensPositions[i];

    /**
     * Se obtiene el listado de tokens en la cárcel...
     */
    const tokens: IToken[] = getTokensInJail(
      positionGame,
      color,
      canSelectToken
    );

    listTokens.push({ index: i, positionGame, tokens });
  }

  return listTokens;

  // return TOKENS_JAIN_AND_OUTSITE;
};
