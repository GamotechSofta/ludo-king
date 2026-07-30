package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.game.LudoColor;
import org.springframework.stereotype.Component;

/** Classifies endgame move risk using Danger Map when available. */
@Component
public class RiskAnalyzer {

  public EndGameRisk classify(
      MoveCandidate move, DangerMap dangerMap, LudoColor color, boolean botLeading
  ) {
    if (move == null) {
      return EndGameRisk.BALANCED;
    }
    int to = move.to();
    if (isHome(to) || isExit(to)) {
      return EndGameRisk.VERY_SAFE;
    }
    if (isSafe(to) && move.threatCountAtTo() == 0) {
      return EndGameRisk.VERY_SAFE;
    }

    int danger = dangerMap != null ? dangerMap.dangerAt(to) : move.threatCountAtTo() * 35;
    boolean nearHome =
        color != null
            && (BotBoardMath.isNearHome(color, move.from()) || isExit(move.from()));

    if (danger >= 100 || (nearHome && danger >= 60 && !isSafe(to))) {
      return EndGameRisk.VERY_RISKY;
    }
    if (danger >= 70 || (move.threatCountAtTo() >= 2 && !isSafe(to))) {
      return EndGameRisk.RISKY;
    }
    if (isSafe(to) || danger <= 25) {
      return botLeading ? EndGameRisk.SAFE : EndGameRisk.SAFE;
    }
    if (danger <= 50) {
      return EndGameRisk.BALANCED;
    }
    return EndGameRisk.RISKY;
  }

  /** Score adjustment: prefer safer unless risk substantially improves win chance. */
  public int riskScoreDelta(EndGameRisk risk, boolean botBehind, int winProbGain) {
    int base =
        switch (risk) {
          case VERY_SAFE -> 35;
          case SAFE -> 22;
          case BALANCED -> 0;
          case RISKY -> -40;
          case VERY_RISKY -> -90;
        };
    if (botBehind && (risk == EndGameRisk.RISKY || risk == EndGameRisk.VERY_RISKY)) {
      // Controlled risk when behind: soften penalty if winProbGain is strong
      if (winProbGain >= 12) {
        base = Math.max(base, -15);
      } else if (winProbGain >= 6) {
        base = Math.max(base, -25);
      }
    }
    if (!botBehind && risk.ordinal() >= EndGameRisk.RISKY.ordinal()) {
      base -= 15; // leading → play safer
    }
    return base;
  }
}
