/**
 * Animate captured pawns walking back to their yard (JAIL), cell by cell — fast.
 */

import type { IListTokens } from "../../interfaces";
import {
  CAPTURE_RETURN_PAUSE_MS,
  CAPTURE_RETURN_STEP_MS,
} from "../../utils/constants";
import { applyTokenCell, buildReturnToJailPath } from "../game/rules";
import { nextFrame, runCellByCellSteps, type AnimCancel } from "./onlineAnimate";

export type CaptureVictim = { playerIndex: number; tokenIndex: number };

function setVictimReturning(
  list: IListTokens[],
  playerIndex: number,
  tokenIndex: number,
  active: boolean
): IListTokens[] {
  return list.map((group, pIdx) => {
    if (pIdx !== playerIndex) return group;
    return {
      ...group,
      tokens: group.tokens.map((t, tIdx) =>
        tIdx === tokenIndex
          ? {
              ...t,
              isMoving: active,
              animated: active,
              isReturning: active,
              snapPlace: false,
            }
          : t
      ),
    };
  });
}

/**
 * Walk each captive reverse along the board into JAIL at high speed.
 * Mutates visually via `apply`; returns the final token list.
 */
export async function runReturnToJailAnimations(
  listTokens: IListTokens[],
  victims: CaptureVictim[],
  apply: (next: IListTokens[]) => void,
  options?: {
    stepMs?: number;
    pauseMs?: number;
    cancel?: AnimCancel;
    onStepSound?: () => void;
  }
): Promise<IListTokens[]> {
  if (!victims.length) return listTokens;

  const stepMs = options?.stepMs ?? CAPTURE_RETURN_STEP_MS;
  const pauseMs = options?.pauseMs ?? CAPTURE_RETURN_PAUSE_MS;
  let working = listTokens;

  for (const victim of victims) {
    if (options?.cancel?.cancelled) return working;

    const group = working[victim.playerIndex];
    if (!group) continue;
    const token = group.tokens[victim.tokenIndex];
    if (!token) continue;

    const path = buildReturnToJailPath(token, group.positionGame);
    if (!path.length) continue;

    working = setVictimReturning(
      working,
      victim.playerIndex,
      victim.tokenIndex,
      true
    );
    apply(working);
    await nextFrame();
    await nextFrame();

    await runCellByCellSteps(
      path.length,
      stepMs,
      (stepIndex) => {
        const step = path[stepIndex];
        options?.onStepSound?.();
        const g = working[victim.playerIndex];
        const nextToken = applyTokenCell(
          g.tokens[victim.tokenIndex],
          g.positionGame,
          step.typeTile,
          step.positionTile,
          true
        );
        const nextTokens = g.tokens.slice();
        nextTokens[victim.tokenIndex] = {
          ...nextToken,
          isReturning: true,
          snapPlace: false,
        };
        working = working.slice();
        working[victim.playerIndex] = { ...g, tokens: nextTokens };
        apply(working);
      },
      options?.cancel,
      pauseMs
    );

    working = setVictimReturning(
      working,
      victim.playerIndex,
      victim.tokenIndex,
      false
    );
    apply(working);
  }

  return working;
}
