import "./styles.css";
import { ROLL_TIME_VALUE } from "../../../../../../utils/constants";
import Icon from "../../../../../icon/indext";
import React, { useEffect, useRef } from "react";
import ReactDice, { ReactDiceRef } from "react-dice-complete";
import type { TDicevalues } from "../../../../../../interfaces";

interface RenderDiceProps {
  disabledDice: boolean;
  showDice: boolean;
  value: 0 | TDicevalues;
  diceRollNumber: number;
  handleDoneDice: () => void;
  handleSelectDice: () => void;
}

const RenderDice = ({
  disabledDice = false,
  showDice = false,
  value = 0,
  diceRollNumber = 0,
  handleDoneDice,
  handleSelectDice,
}: RenderDiceProps) => {
  const refDice = useRef<ReactDiceRef>(null);
  const handleDoneDiceRef = useRef(handleDoneDice);
  const valueRef = useRef(value);

  useEffect(() => {
    handleDoneDiceRef.current = handleDoneDice;
  }, [handleDoneDice]);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  const rollTime = value !== 0 ? ROLL_TIME_VALUE : 0;

  useEffect(() => {
    if (value !== 0 && diceRollNumber !== 0) {
      refDice.current?.rollAll([value]);
    }
  }, [value, diceRollNumber]);

  return (
    <div className={`game-profile-dice ${!showDice ? "hide" : ""}`}>
      {!disabledDice && <Icon type="arrow" />}
      <button
        className="game-profile-dice-button"
        disabled={disabledDice}
        onClick={handleSelectDice}
      >
        <ReactDice
          ref={refDice}
          disableIndividual
          defaultRoll={1}
          dieSize={45}
          dotColor="black"
          faceColor="white"
          numDice={1}
          outline
          rollTime={rollTime}
          // Use refs — react-dice-complete may keep the initial rollDone closure
          rollDone={() => {
            if (valueRef.current !== 0) {
              handleDoneDiceRef.current();
            }
          }}
          outlineColor="white"
        />
      </button>
    </div>
  );
};

export default React.memo(RenderDice);
