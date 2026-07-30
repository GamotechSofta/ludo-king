import "./styles.css";
import { ROLL_TIME_VALUE } from "../../../../../../utils/constants";
import React, { useEffect, useRef, useState } from "react";
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

const DICE_PIPS: Record<number, number[]> = {
  1: [4],
  2: [0, 8],
  3: [0, 4, 8],
  4: [0, 2, 6, 8],
  5: [0, 2, 4, 6, 8],
  6: [0, 2, 3, 5, 6, 8],
};

const DICE_TRANSFORMS: Record<number, string> = {
  1: "rotateX(0deg) rotateY(0deg)",
  2: "rotateX(0deg) rotateY(-90deg)",
  3: "rotateX(-90deg) rotateY(0deg)",
  4: "rotateX(90deg) rotateY(0deg)",
  5: "rotateX(0deg) rotateY(90deg)",
  6: "rotateX(0deg) rotateY(180deg)",
};

const DiceFace = React.memo(({ value }: { value: number }) => {
  const visible = new Set(DICE_PIPS[value]);
  return (
    <div className={`smooth-dice-face smooth-dice-face-${value}`}>
      {Array.from({ length: 9 }, (_, index) => (
        <span
          key={index}
          className={`smooth-dice-pip${visible.has(index) ? "" : " empty"}`}
        />
      ))}
    </div>
  );
});

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
  const handleDoneDiceRef = useRef(handleDoneDice);
  const valueRef = useRef(value);
  const lastAnimatedRollRef = useRef("");
  const activeRollRef = useRef("");
  const rollDoneTimerRef = useRef<number | null>(null);
  const holdVisibleTimerRef = useRef<number | null>(null);
  const [holdVisible, setHoldVisible] = useState(false);
  const [rolling, setRolling] = useState(false);

  useEffect(() => {
    handleDoneDiceRef.current = handleDoneDice;
  }, [handleDoneDice]);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    if (!hasTurn) return;
    if (value === 0 || diceRollNumber === 0) return;
    setHoldVisible(true);
    if (holdVisibleTimerRef.current != null) {
      window.clearTimeout(holdVisibleTimerRef.current);
    }
    holdVisibleTimerRef.current = window.setTimeout(() => {
      setHoldVisible(false);
      holdVisibleTimerRef.current = null;
    }, Math.round(ROLL_TIME_VALUE * 1000) + 240);
  }, [hasTurn, value, diceRollNumber]);

  useEffect(() => {
    // New turn baseline: allow same face to animate again (e.g. 4 then 4).
    if (!hasTurn) return;
    if (diceRollNumber === 0) {
      lastAnimatedRollRef.current = "";
    }
  }, [hasTurn, diceRollNumber]);

  useEffect(
    () => () => {
      if (holdVisibleTimerRef.current != null) {
        window.clearTimeout(holdVisibleTimerRef.current);
      }
      if (rollDoneTimerRef.current != null) {
        window.clearTimeout(rollDoneTimerRef.current);
      }
    },
    []
  );

  // CSS 3D tumble adapted from the reference game. It is controlled by the
  // monotonic roll key, so equal consecutive values still animate and finish.
  useEffect(() => {
    if (!hasTurn) return;
    if (value === 0 || diceRollNumber === 0) return;

    const rollKey = `${diceRollNumber}:${value}`;
    if (lastAnimatedRollRef.current === rollKey) return;

    activeRollRef.current = rollKey;
    lastAnimatedRollRef.current = rollKey;
    setRolling(true);
    if (rollDoneTimerRef.current != null) {
      window.clearTimeout(rollDoneTimerRef.current);
    }
    rollDoneTimerRef.current = window.setTimeout(() => {
      if (activeRollRef.current !== rollKey) return;
      activeRollRef.current = "";
      rollDoneTimerRef.current = null;
      setRolling(false);
      if (valueRef.current !== 0) {
        handleDoneDiceRef.current();
      }
    }, Math.round(ROLL_TIME_VALUE * 1000));
  }, [hasTurn, value, diceRollNumber]);

  if (!showDice) return null;
  const showFace = hasTurn || holdVisible;

  return (
    <div
      className={`game-profile-dice ${
        hasTurn && !disabledDice ? "ready" : "rolled"
      }${showArrow ? " has-arrow" : ""}${showFace ? " has-die" : " empty"}`}
    >
      {showArrow && <span className="game-profile-dice-arrow" aria-hidden />}
      <button
        className="game-profile-dice-button"
        type="button"
        disabled={disabledDice || !hasTurn}
        onClick={handleSelectDice}
        aria-label={hasTurn ? "Roll dice" : "Waiting for turn"}
      />
      {/* Keep die mounted always — original behaviour; hide when not this seat */}
      <div
        className={`game-profile-dice-face${
          showFace ? "" : " game-profile-dice-face-hidden"
        }`}
      >
        <div className="smooth-dice-scene">
          <div
            className={`smooth-dice-cube${rolling ? " rolling" : " settling"}`}
            style={
              rolling
                ? undefined
                : { transform: DICE_TRANSFORMS[value || 1] }
            }
          >
            {[1, 2, 3, 4, 5, 6].map((face) => (
              <DiceFace key={face} value={face} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

export default React.memo(RenderDice);
