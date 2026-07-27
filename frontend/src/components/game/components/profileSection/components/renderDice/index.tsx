import "./styles.css";
import { ROLL_TIME_VALUE } from "../../../../../../utils/constants";
import React, { useEffect, useRef } from "react";
import ReactDice, { ReactDiceRef } from "react-dice-complete";
import type { TDicevalues } from "../../../../../../interfaces";

interface RenderDiceProps {
  hasTurn?: boolean;
  disabledDice: boolean;
  showDice: boolean;
  showArrow?: boolean;
  value: 0 | TDicevalues;
  diceRollNumber: number;
  handleDoneDice: () => void;
  handleSelectDice: () => void;
}

const RenderDice = ({
  hasTurn = false,
  disabledDice = false,
  showDice = true,
  showArrow = false,
  value = 0,
  diceRollNumber = 0,
  handleDoneDice,
  handleSelectDice,
}: RenderDiceProps) => {
  const refDice = useRef<ReactDiceRef>(null);
  const handleDoneDiceRef = useRef(handleDoneDice);
  const valueRef = useRef(value);
  const lastAnimatedRollRef = useRef("");

  useEffect(() => {
    handleDoneDiceRef.current = handleDoneDice;
  }, [handleDoneDice]);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  const rollTime = value !== 0 ? ROLL_TIME_VALUE : 0;

  useEffect(() => {
    if (!hasTurn) return;
    if (value === 0 || diceRollNumber === 0) return;
    const rollKey = `${diceRollNumber}:${value}`;
    if (lastAnimatedRollRef.current === rollKey) return;
    lastAnimatedRollRef.current = rollKey;
    refDice.current?.rollAll([value]);
  }, [hasTurn, value, diceRollNumber]);

  if (!showDice) return null;

  return (
    <div
      className={`game-profile-dice ${
        hasTurn && !disabledDice ? "ready" : "rolled"
      }${showArrow ? " has-arrow" : ""}${hasTurn ? " has-die" : " empty"}`}
    >
      {showArrow && <span className="game-profile-dice-arrow" aria-hidden />}
      <button
        className="game-profile-dice-button"
        type="button"
        disabled={disabledDice || !hasTurn}
        onClick={handleSelectDice}
        aria-label={hasTurn ? "Roll dice" : "Waiting for turn"}
      />
      <div
        className={`game-profile-dice-face${
          hasTurn ? "" : " game-profile-dice-face-hidden"
        }`}
      >
        <ReactDice
          ref={refDice}
          disableIndividual
          defaultRoll={1}
          dieSize={36}
          dotColor="#111111"
          faceColor="#ffffff"
          numDice={1}
          outline
          rollTime={rollTime}
          rollDone={() => {
            if (valueRef.current !== 0) {
              handleDoneDiceRef.current();
            }
          }}
          outlineColor="#c8c8c8"
        />
      </div>
    </div>
  );
};

export default React.memo(RenderDice);
