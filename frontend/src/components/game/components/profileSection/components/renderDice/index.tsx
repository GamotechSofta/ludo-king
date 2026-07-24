import "./styles.css";
import { ROLL_TIME_VALUE } from "../../../../../../utils/constants";
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
  showDice = true,
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

  if (!showDice) return null;

  return (
    <div className={`game-profile-dice ${disabledDice ? "rolled" : "ready"}`}>
      {!disabledDice && <span className="game-profile-dice-arrow" aria-hidden />}
      <button
        className="game-profile-dice-button"
        type="button"
        disabled={disabledDice}
        onClick={handleSelectDice}
        aria-label="Roll dice"
      />
      <div className="game-profile-dice-face">
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
          outlineColor="#d0d0d0"
        />
      </div>
    </div>
  );
};

export default React.memo(RenderDice);
