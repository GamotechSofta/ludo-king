import "./styles.css";
import { ROLL_TIME_VALUE } from "../../../../../../utils/constants";
import React, { useCallback, useEffect, useRef } from "react";
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

/**
 * Keep ReactDice props 100% stable so the library does not remount its cube
 * on every parent render (that cancelled the classic tumble).
 */
const StableReactDice = React.memo(
  React.forwardRef<
    ReactDiceRef,
    { onRollDone: (total: number, values: number[]) => void }
  >(function StableReactDice({ onRollDone }, ref) {
    return (
      <ReactDice
        ref={ref}
        disableIndividual
        defaultRoll={1}
        dieSize={36}
        margin={0}
        dotColor="#111111"
        faceColor="#ffffff"
        numDice={1}
        outline
        rollTime={ROLL_TIME_VALUE}
        rollDone={onRollDone}
        outlineColor="#c8c8c8"
      />
    );
  })
);

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
  const activeRollRef = useRef("");
  const rollDoneTimerRef = useRef<number | null>(null);

  useEffect(() => {
    handleDoneDiceRef.current = handleDoneDice;
  }, [handleDoneDice]);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    // New turn baseline: allow same face to animate again (e.g. 4 then 4).
    if (!hasTurn) return;
    if (diceRollNumber === 0) {
      lastAnimatedRollRef.current = "";
    }
  }, [hasTurn, diceRollNumber]);

  useEffect(
    () => () => {
      if (rollDoneTimerRef.current != null) {
        window.clearTimeout(rollDoneTimerRef.current);
      }
    },
    []
  );

  const onRollDone = useCallback((_total: number, _values: number[]) => {
    // Ignore stale rollDone from an old/aborted die cycle.
    if (!activeRollRef.current) return;
    const doneKey = activeRollRef.current;
    activeRollRef.current = "";
    if (rollDoneTimerRef.current != null) {
      window.clearTimeout(rollDoneTimerRef.current);
      rollDoneTimerRef.current = null;
    }
    if (valueRef.current !== 0 && doneKey) handleDoneDiceRef.current();
  }, []);

  // Tumble only on the active seat — other profiles keep their own last face.
  useEffect(() => {
    if (!hasTurn) return;
    if (value === 0 || diceRollNumber === 0) return;

    const rollKey = `${diceRollNumber}:${value}`;
    if (lastAnimatedRollRef.current === rollKey) return;

    let cancelled = false;

    const kick = (attempt: number) => {
      if (cancelled) return;
      if (refDice.current) {
        activeRollRef.current = rollKey;
        refDice.current.rollAll([value]);
        lastAnimatedRollRef.current = rollKey;
        if (rollDoneTimerRef.current != null) {
          window.clearTimeout(rollDoneTimerRef.current);
        }
        // Safety net: if library doesn't fire rollDone, unlock flow anyway.
        rollDoneTimerRef.current = window.setTimeout(() => {
          if (activeRollRef.current !== rollKey) return;
          activeRollRef.current = "";
          if (valueRef.current !== 0) {
            handleDoneDiceRef.current();
          }
        }, Math.round(ROLL_TIME_VALUE * 1000) + 220);
        return;
      }
      if (attempt < 20) {
        requestAnimationFrame(() => kick(attempt + 1));
      } else if (valueRef.current !== 0) {
        handleDoneDiceRef.current();
      }
    };

    requestAnimationFrame(() => {
      requestAnimationFrame(() => kick(0));
    });

    return () => {
      cancelled = true;
      // Keep lastAnimatedRollRef — clearing it re-triggers tumble under React
      // Strict Mode / fast remount (looks like a double bot dice roll).
    };
  }, [hasTurn, value, diceRollNumber]);

  if (!showDice) return null;

  return (
    <div
      className={`game-profile-dice ${
        hasTurn && !disabledDice ? "ready" : "rolled"
      }${showArrow ? " has-arrow" : ""} has-die`}
    >
      {showArrow && <span className="game-profile-dice-arrow" aria-hidden />}
      <button
        className="game-profile-dice-button"
        type="button"
        disabled={disabledDice || !hasTurn}
        onClick={handleSelectDice}
        aria-label={hasTurn ? "Roll dice" : "Waiting for turn"}
      />
      <div className="game-profile-dice-face">
        <StableReactDice ref={refDice} onRollDone={onRollDone} />
      </div>
    </div>
  );
};

export default React.memo(RenderDice);
