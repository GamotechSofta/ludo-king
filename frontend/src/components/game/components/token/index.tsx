import "./styles.css";
import { EColors, EtypeTile, SIZE_TILE } from "../../../../utils/constants";
import { Piece, Tooltip } from "./components";
import { useOnClickOutside } from "../../../../hooks";
import {
  ICalculateTokenStyles,
  getDiceIndexSelected,
  getTokenSyle,
  getZindexTokenWrapper,
} from "./helpers";
import React, { useEffect, useRef, useState } from "react";
import type {
  IDiceList,
  ISelectTokenValues,
  IToken,
} from "../../../../interfaces";

/**
 * totalTokens determina la cantidad de tokens que hay en la celda del token actual,
 * por defecto es 1 si no se pasa..
 * position: determina la posición en la que se muestra el token,
 * sólo se aplica si el valor de totalTokens es mayor que uno.
 * el token,
 */
interface TokenProps extends IToken {
  diceList: IDiceList[];
  isDisabledUI?: boolean;
  debug?: boolean;
  handleSelectedToken: (selectTokenValues: ISelectTokenValues) => void;
}

const Token = ({
  color = EColors.RED,
  coordinate = { x: 0, y: 0 },
  typeTile = EtypeTile.NORMAL,
  index = 0,
  diceAvailable = [],
  totalTokens = 1,
  position = 1,
  enableTooltip = false,
  isMoving = false,
  animated = false,
  snapPlace = false,
  isReturning = false,
  canSelectToken = true,
  diceList = [],
  isDisabledUI = false,
  debug = false,
  handleSelectedToken,
}: TokenProps) => {
  const [showTooltip, setShowTooltip] = useState(false);
  const tooltipRef = useRef<HTMLDivElement>(null);

  /**
   * Se ejecuta cuando el usuario hace click por fuera del tooltip...
   */
  useOnClickOutside(tooltipRef, () => setShowTooltip(false));

  /**
   * Mostrar el tooltip si desde el padre se invoca...
   */
  useEffect(() => {
    setShowTooltip(enableTooltip);
  }, [enableTooltip]);

  /**
   * Efecto que valida que si existe un tooltip abierto
   * y no hay dados disponibles, por defecto cierre el tooltip...
   */
  useEffect(() => {
    if (showTooltip && diceAvailable.length === 0) {
      setShowTooltip(false);
    }
  }, [showTooltip, diceAvailable]);

  /**
   * Se ejecuta cuando se ha hecho click en el botón del token...
   */
  const handleClickDice = () => {
    if (diceAvailable.length === 1) {
      /**
       * Se obtiene el índice del dado en el listado de dados globales,
       * de acuerdo al dado que tiene disponible el token...
       */
      const diceIndex = getDiceIndexSelected(diceList, diceAvailable[0].key);
      /**
       * Se envía el indice del único dado que hay disponible...
       */
      handleSelectedToken({ diceIndex, tokenIndex: index });
    } else {
      // Hay varios dados, así que se muestra el tooltip...
      setShowTooltip(true);
    }
  };

  /**
   * Se ejecuta cuando se ha seleccionado un valor del dado en el tooltip...
   * @param dice
   */
  const handleTooltipDice = (dice: IDiceList) => {
    /**
     * Se oculta el tooltip de dados...
     */
    setShowTooltip(false);

    /**
     * Con el dado seleccionado en el tooltip, se busca el índice del mismo en el array
     * de dados globales...
     */
    const diceIndex = getDiceIndexSelected(diceList, dice.key);

    /**
     * Se sube el valor seleccionado al componente padre...
     */
    handleSelectedToken({ diceIndex, tokenIndex: index });
  };

  /**
   * El total de dados disponibles para el token...
   */
  const totalDiceAvailable = diceAvailable.length;

  const calculateTokenStyles: ICalculateTokenStyles = {
    totalTokens,
    position,
    totalDiceAvailable,
    isMoving,
    canSelectToken: canSelectToken && !isDisabledUI,
  };

  // Se calcula el zindex del elemento padre...
  const zIndex = getZindexTokenWrapper(calculateTokenStyles);

  const styleWrapper: React.CSSProperties = {
    left: coordinate.x,
    top: coordinate.y,
    width: SIZE_TILE,
    height: SIZE_TILE,
    zIndex,
  };

  const isMovingClass = isMoving ? "moving" : "";
  const isAnimatedClass = animated ? "animated" : "";
  const isSnapClass = snapPlace ? "snap" : "";
  const isReturningClass = isReturning ? "returning" : "";
  const className = `game-token ${isAnimatedClass} ${isMovingClass} ${isSnapClass} ${isReturningClass}`;
  const stylePiece = getTokenSyle({ ...calculateTokenStyles, typeTile });

  const showButton =
    canSelectToken &&
    totalDiceAvailable !== 0 &&
    !showTooltip &&
    !isMoving &&
    !isDisabledUI;

  return (
    <React.Fragment>
      <div className={className} style={styleWrapper}>
        <Piece color={color} style={stylePiece} debug={debug} index={index} />
        {showButton && (
          <button className="game-token-button" onClick={handleClickDice} />
        )}
      </div>
      {canSelectToken && showTooltip && (
        <Tooltip
          ref={tooltipRef}
          color={color}
          coordinate={coordinate}
          diceAvailable={diceAvailable}
          handleTooltipDice={handleTooltipDice}
        />
      )}
    </React.Fragment>
  );
};

export default React.memo(Token, (prev, next) => {
  return (
    prev.coordinate?.x === next.coordinate?.x &&
    prev.coordinate?.y === next.coordinate?.y &&
    prev.isMoving === next.isMoving &&
    prev.animated === next.animated &&
    prev.snapPlace === next.snapPlace &&
    prev.isReturning === next.isReturning &&
    prev.canSelectToken === next.canSelectToken &&
    prev.totalTokens === next.totalTokens &&
    prev.position === next.position &&
    prev.enableTooltip === next.enableTooltip &&
    prev.color === next.color &&
    prev.index === next.index &&
    prev.isDisabledUI === next.isDisabledUI &&
    prev.debug === next.debug &&
    prev.handleSelectedToken === next.handleSelectedToken &&
    prev.diceList === next.diceList &&
    prev.diceAvailable === next.diceAvailable &&
    prev.typeTile === next.typeTile
  );
});
