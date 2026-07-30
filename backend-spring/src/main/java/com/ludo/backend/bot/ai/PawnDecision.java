package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isSafe;

import org.springframework.stereotype.Component;

/**
 * Strategic helpers: sacrifice, safer-prefer, escape urgency for move scoring.
 */
@Component
public class PawnDecision {

  private final PawnValueConfig config;

  public PawnDecision(PawnValueConfig config) {
    this.config = config;
  }

  /**
   * Extra score delta when moving a pawn given its priority and destination danger.
   */
  public void applyToMoveScore(
      MoveScore score,
      MoveCandidate move,
      PawnPriority priority,
      int destinationDanger
  ) {
    if (priority == null || score == null || move == null) {
      return;
    }
    int value = priority.value();

    if (priority.escapeNeeded()
        && move.underThreatAtFrom()
        && (destinationDanger <= 20 || isSafe(move.to()) || isHome(move.to()))) {
      int bonus = Math.min(120, 40 + value / 3);
      score.add("Protect Valuable Escape", bonus);
    }

    if (destinationDanger > 0 && !isSafe(move.to()) && !isHome(move.to())) {
      int penalty = Math.min(160, (value * destinationDanger) / 200);
      if (priority.neverSacrifice()) {
        penalty = (int) (penalty * 1.5);
      }
      if (penalty > 0) {
        score.add("Valuable Pawn Danger", -penalty);
      }
    }

    if (priority.importance() == PawnImportance.HIGHEST && isHome(move.to())) {
      score.add("Highest Priority Finish", 50);
    }

    if (priority.state() != null
        && priority.state().finishedSiblings() >= 3
        && !isHome(move.to())
        && move.capture()) {
      score.add("Endgame Skip Capture", -30);
    }
  }

  /**
   * When two candidates are close in total score, prefer protecting higher value /
   * safer destination using pawn priorities.
   */
  public int tieBreakDelta(
      MoveCandidate a,
      PawnPriority pa,
      int dangerA,
      MoveCandidate b,
      PawnPriority pb,
      int dangerB
  ) {
    if (!config.enabled() || pa == null || pb == null) {
      return 0;
    }
    int va = pa.value();
    int vb = pb.value();
    if (Math.abs(va - vb) <= config.similarValueMargin()) {
      // Prefer safer pawn destination
      return Integer.compare(dangerB, dangerA);
    }
    // Prefer moving/protecting higher value when one is escaping danger
    if (pa.escapeNeeded() && !pb.escapeNeeded()) {
      return 1;
    }
    if (pb.escapeNeeded() && !pa.escapeNeeded()) {
      return -1;
    }
    return Integer.compare(va, vb);
  }

  /**
   * True if exposing {@code exposed} is an acceptable sacrifice vs saving {@code saved}.
   */
  public boolean maySacrifice(PawnPriority exposed, PawnPriority saved) {
    if (!config.sacrificeEnabled() || exposed == null || saved == null) {
      return false;
    }
    if (exposed.neverSacrifice()) {
      return false;
    }
    if (exposed.pawnIndex() == saved.pawnIndex()) {
      return false;
    }
    // Always allow sacrificing clearly lower-value pawns to save a protected one.
    if (saved.neverSacrifice() || saved.importance().ordinal() < exposed.importance().ordinal()) {
      return exposed.value() < saved.value();
    }
    return exposed.value() + config.similarValueMargin() < saved.value();
  }

  public int lossPenalty(int pawnValue) {
    // Losing a pawn in future sim — scale hard for high value
    return Math.min(220, 40 + pawnValue);
  }
}
