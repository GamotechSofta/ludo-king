import type {
  TPositionProfiles,
  IPlayer,
  TTotalPlayers,
  IProfileHandlers,
  TPositionProfile,
  IActionsTurn,
  TColors,
} from "../../../../interfaces";
import { DEFAULT_VALUE_ACTION_TURN } from "../../../../utils/constants";
import { Profile } from "./components";

type TPositionPlayerIndex = Record<TPositionProfile, number>;
type TPositionBoard = Record<TPositionProfiles, Partial<TPositionPlayerIndex>>;
type TPositionTotalPlayers = Record<TTotalPlayers, Partial<TPositionBoard>>;

export interface ProfileSectionProps {
  basePosition: TPositionProfiles;
  currentTurn: number;
  players: IPlayer[];
  totalPlayers: TTotalPlayers;
  profileHandlers: IProfileHandlers;
  actionsTurn: IActionsTurn;
  /**
   * Online: when set, die/turn highlight follows this house color (server seat),
   * not the fragile view-slot index. Fixes die showing on the wrong profile.
   */
  turnColor?: TColors | string | null;
}

/**
 * La distribución de los profiles en relación al número de jugadores, la posición en la board (TOP, BOTTOM)
 * y la posición de izquierda o derecha...
 */
const DISTRIBUTION_PROFILES: TPositionTotalPlayers = {
  2: {
    BOTTOM: {
      LEFT: 1,
    },
    TOP: {
      RIGHT: 2,
    },
  },
  3: {
    BOTTOM: {
      LEFT: 1,
    },
    TOP: {
      LEFT: 2,
      RIGHT: 3,
    },
  },
  4: {
    BOTTOM: {
      LEFT: 1,
      RIGHT: 4,
    },
    TOP: {
      LEFT: 2,
      RIGHT: 3,
    },
  },
};

/**
 * Función que determina si el compontente de profile se renderiza,
 * dependiendo de los props que se reciben...
 * La idea es que sirva como una función que valida los props de los demás
 * componentes...
 * @param props
 * @param position
 * @returns
 */
export const renderProfileComponent = (
  props: ProfileSectionProps,
  position: TPositionProfile
) => {
  /**
   * Se extra los props que llegan al componente...
   */
  const {
    basePosition,
    currentTurn,
    players,
    totalPlayers,
    profileHandlers,
    actionsTurn,
    turnColor,
  } = props;

  /**
   * Se valida si el índice del jugador existe, este índice inicia en 1
   * si no existe por defecto será cero.
   */
  const indexProfile =
    DISTRIBUTION_PROFILES[totalPlayers]?.[basePosition]?.[position] || 0;

  if (indexProfile !== 0 && players[indexProfile - 1]) {
    const player = players[indexProfile - 1];
    /**
     * Online: match house color to active seat color (authoritative).
     * Offline: fall back to view-slot currentTurn index.
     */
    const hasTurn = turnColor
      ? player.color === turnColor
      : currentTurn === indexProfile - 1;

    /**
     * Si tiene el turno, pasa el valor de actionsTurn, si no enviará el valor por defecto,
     * con esto sólo el componente Profile que tenga el turno otendrá el valor de actionsTurn,
     * que se le pasado desde el padre...
     */
    const newActionsTurn = hasTurn ? actionsTurn : DEFAULT_VALUE_ACTION_TURN;

    /**
     * Se generan los props que se le pasarán al componente de Profile...
     */
    const extProps = {
      basePosition,
      position,
      hasTurn,
      actionsTurn: newActionsTurn,
      ...profileHandlers,
    };

    return <Profile {...extProps} player={player} />;
  }
};
