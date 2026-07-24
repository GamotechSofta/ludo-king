import {
  BASE_ZINDEX_TOKEN,
  EtypeTile,
  MAXIMUM_VISIBLE_TOKENS_PER_CELL,
  ZINDEX_TOKEN_SELECT,
} from "../../../../utils/constants";
import type { IDiceList, TtypeTile } from "../../../../interfaces";
import React from "react";

/**
 * Dado el listado de dados que están totalmente disponibles,
 * devolver el indice real del dado seleccioando de acuerdo al key del mismo,
 * este key es un valor único (random)...
 * @param diceList
 * @param diceKey
 * @returns
 */
export const getDiceIndexSelected = (diceList: IDiceList[], diceKey: number) =>
  diceList.findIndex((v) => v.key === diceKey);

export interface ICalculateTokenStyles {
  totalTokens: number;
  position: number;
  totalDiceAvailable: number;
  isMoving: boolean;
  canSelectToken: boolean;
}

export const getZindexTokenWrapper = ({
  totalTokens,
  position,
  totalDiceAvailable,
  isMoving,
  canSelectToken,
}: ICalculateTokenStyles) => {
  /**
   * Se establece el zindex por defecto que tendrá el contenedor del token...
   */
  let zIndex = BASE_ZINDEX_TOKEN;

  /**
   * Sí tiene dados disponibles, se establece el zIndez de selección,
   * de esta forma el token quedará encima de los demás que estén en la misma celda
   * (si es que hay más...)
   * También se pondrá si la ficha se está moviendo, en este caso
   * tomará el zindex mayor para que quede arriba...
   * Se establece el valor canSelectToken con totalDiceAvailable,
   * ya que sólo debería tener en cuenta el valor de totalDiceAvailable,
   * si el usuario puede seleccionar el token, también entra cuando
   * se está moviendo el token...
   */
  if ((canSelectToken && totalDiceAvailable !== 0) || isMoving) {
    zIndex = ZINDEX_TOKEN_SELECT;
  } else {
    /**
     * Por el contrario si existen más tokens en la misma celda,
     * se establece su zindex dependiendo de la posición, de está forma
     * quedará uno sobre otr0.
     * Sólo se tienen en cuenta los primeros 4, si hay más no se les establece el zindex
     * y quedará con el valor por defecto...
     */
    if (totalTokens > 1 && position <= MAXIMUM_VISIBLE_TOKENS_PER_CELL) {
      zIndex = position;
    }
  }

  return zIndex;
};

export interface IGetTokenSyle extends ICalculateTokenStyles {
  typeTile: TtypeTile;
}

export const getTokenSyle = ({
  totalTokens,
  position,
  totalDiceAvailable,
  typeTile = EtypeTile.JAIL,
  isMoving,
  canSelectToken,
}: IGetTokenSyle): React.CSSProperties => {
  const scales = ["1", "0.8", "0.68", "0.58"];

  const positions = [[0], [-6, 6], [-10, 0, 10], [-13, -4, 4, 13]];

  let indexPosition = totalTokens <= positions.length ? totalTokens - 1 : 3;

  let scale =
    position <= MAXIMUM_VISIBLE_TOKENS_PER_CELL ? scales[indexPosition] : 0;

  let translateX =
    position <= MAXIMUM_VISIBLE_TOKENS_PER_CELL
      ? positions[indexPosition][position - 1]
      : 0;

  if ((canSelectToken && totalDiceAvailable !== 0) || isMoving) {
    scale = scales[0];
    translateX = positions[0][0];
  }

  if (typeTile === EtypeTile.END) {
    scale = "0.55";
  }

  // Scale around the colored base disc so stacked tokens stay centered
  return {
    transform: `translateX(${translateX}px) scale(${scale})`,
    transformOrigin: "50% 86.8%",
  };
};
