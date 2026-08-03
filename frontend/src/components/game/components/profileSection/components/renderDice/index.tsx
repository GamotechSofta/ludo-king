import "./styles.css";
import {
  DICE_ROLL_ANIM_MS,
  DICE_ROLL_SETTLE_MS,
  ROLL_TIME_VALUE,
} from "../../../../../../utils/constants";
import React, { useCallback, useEffect, useRef, useState } from "react";
import ReactDice, { ReactDiceRef } from "react-dice-complete";
import type { TDicevalues } from "../../../../../../interfaces";

interface RenderDiceProps {
  hasTurn?: boolean;
  disabledDice: boolean;
  showDice: boolean;
  showArrow?: boolean;
  value: 0 | TDicevalues;
  diceRollNumber: number;
  /** Spin before server face is known (online click). */
  optimisticRolling?: boolean;
  handleDoneDice: () => void;
  handleSelectDice: () => void;
}

/** tumble duration + settle buffer (ms) — keep in sync with OnlineGame waits */
const ROLL_DONE_SAFETY_MS = DICE_ROLL_ANIM_MS + DICE_ROLL_SETTLE_MS;

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
  optimisticRolling = false,
  handleDoneDice,
  handleSelectDice,
}: RenderDiceProps) => {
  const refDice = useRef<ReactDiceRef>(null);
  const handleDoneDiceRef = useRef(handleDoneDice);
  const valueRef = useRef(value);
  const lastAnimatedRollRef = useRef("");
  const activeRollRef = useRef("");
  const rollDoneTimerRef = useRef<number | null>(null);
  /** roll# present when this seat gained the turn — ignore until it bumps (no ghost spin). */
  const rollNumberAtTurnStartRef = useRef(0);
  const hadTurnRef = useRef(false);
  const [spinning, setSpinning] = useState(false);

  useEffect(() => {
    handleDoneDiceRef.current = handleDoneDice;
  }, [handleDoneDice]);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    if (!hasTurn) {
      hadTurnRef.current = false;
      setSpinning(false);
      return;
    }
    // Just received the turn.
    if (!hadTurnRef.current) {
      hadTurnRef.current = true;
      lastAnimatedRollRef.current = "";
      setSpinning(false);
      // Idle handoff (human after bot): no face yet — block until roll# bumps.
      // Turn + roll together (bot opening roll): allow this roll# to tumble.
      if (value === 0 || diceRollNumber === 0) {
        rollNumberAtTurnStartRef.current = diceRollNumber;
      } else {
        rollNumberAtTurnStartRef.current = Math.max(0, diceRollNumber - 1);
      }
      return;
    }
    if (diceRollNumber === 0) {
      lastAnimatedRollRef.current = "";
      rollNumberAtTurnStartRef.current = 0;
      setSpinning(false);
    }
  }, [hasTurn, diceRollNumber, value]);

  useEffect(
    () => () => {
      if (rollDoneTimerRef.current != null) {
        window.clearTimeout(rollDoneTimerRef.current);
      }
    },
    []
  );

  const finishRoll = useCallback((doneKey: string) => {
    // Only the active tumble may finish once (rollDone + safety timeout race).
    if (!doneKey || activeRollRef.current !== doneKey) return;
    activeRollRef.current = "";
    setSpinning(false);
    if (rollDoneTimerRef.current != null) {
      window.clearTimeout(rollDoneTimerRef.current);
      rollDoneTimerRef.current = null;
    }
    if (valueRef.current !== 0) {
      handleDoneDiceRef.current();
    }
  }, []);

  const onRollDone = useCallback(
    (_total: number, _values: number[]) => {
      // Ignore stale rollDone from an old/aborted die cycle.
      if (!activeRollRef.current) return;
      const doneKey = activeRollRef.current;
      finishRoll(doneKey);
    },
    [finishRoll]
  );

  // Tumble only on the active seat after a *new* roll (click / server flash).
  useEffect(() => {
    if (!hasTurn) return;
    if (value === 0 || diceRollNumber === 0) return;
    // Same roll# as when the seat gained turn → leftover from previous player.
    if (diceRollNumber === rollNumberAtTurnStartRef.current) return;

    const rollKey = `${diceRollNumber}:${value}`;
    if (lastAnimatedRollRef.current === rollKey) return;

    let cancelled = false;

    const kick = (attempt: number) => {
      if (cancelled) return;
      if (refDice.current) {
        activeRollRef.current = rollKey;
        lastAnimatedRollRef.current = rollKey;
        setSpinning(true);
        // Next frame: start tumble after bob class has been removed from DOM.
        requestAnimationFrame(() => {
          if (cancelled || !refDice.current) return;
          refDice.current.rollAll([value]);
        });
        if (rollDoneTimerRef.current != null) {
          window.clearTimeout(rollDoneTimerRef.current);
        }
        // Safety net: if library doesn't fire rollDone, unlock flow anyway.
        rollDoneTimerRef.current = window.setTimeout(() => {
          if (activeRollRef.current !== rollKey) return;
          finishRoll(rollKey);
        }, ROLL_DONE_SAFETY_MS);
        return;
      }
      if (attempt < 20) {
        requestAnimationFrame(() => kick(attempt + 1));
      } else if (valueRef.current !== 0) {
        setSpinning(false);
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
  }, [hasTurn, value, diceRollNumber, finishRoll]);

  if (!showDice) return null;

  const showOptimistic = Boolean(
    optimisticRolling && hasTurn && !spinning && (value === 0 || diceRollNumber === 0)
  );
  const canClick = hasTurn && !disabledDice && !spinning && !showOptimistic;

  return (
    <div
      className={`game-profile-dice ${
        canClick ? "ready" : "rolled"
      }${showArrow && canClick ? " has-arrow" : ""}${
        spinning ? " is-rolling" : ""
      }${showOptimistic ? " is-optimistic" : ""} has-die`}
    >
      {showArrow && canClick && (
        <span className="game-profile-dice-arrow" aria-hidden />
      )}
      <button
        className="game-profile-dice-button"
        type="button"
        disabled={!canClick}
        onClick={handleSelectDice}
        aria-label={hasTurn ? "Roll dice" : "Waiting for turn"}
      />
      <div className="game-profile-dice-face">
        <div className="game-profile-dice-bob">
          <StableReactDice ref={refDice} onRollDone={onRollDone} />
        </div>
      </div>
    </div>
  );
};

export default React.memo(RenderDice);
