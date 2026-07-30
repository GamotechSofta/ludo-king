package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotBoardMath.VictimInfo;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds {@link MoveCandidate} list from legal engine moves + cached board facts. */
@Component
public class MoveCandidateFactory {

  public List<MoveCandidate> buildAll(
      List<int[]> legalMoves,
      List<Integer> diceList,
      BoardAnalysisCache cache
  ) {
    return buildAll(legalMoves, diceList, cache, null);
  }

  public List<MoveCandidate> buildAll(
      List<int[]> legalMoves,
      List<Integer> diceList,
      BoardAnalysisCache cache,
      PawnValueReport pawnValues
  ) {
    return buildAll(legalMoves, diceList, cache, pawnValues, null);
  }

  public List<MoveCandidate> buildAll(
      List<int[]> legalMoves,
      List<Integer> diceList,
      BoardAnalysisCache cache,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    List<MoveCandidate> out = new ArrayList<>(legalMoves.size());
    if (legalMoves == null || diceList == null || cache == null || cache.own() == null) {
      return out;
    }
    for (int[] m : legalMoves) {
      MoveCandidate c = buildOne(m, diceList, cache, pawnValues, opponents);
      if (c != null) {
        out.add(c);
      }
    }
    return out;
  }

  public MoveCandidate buildOne(int[] raw, List<Integer> diceList, BoardAnalysisCache cache) {
    return buildOne(raw, diceList, cache, null, null);
  }

  public MoveCandidate buildOne(
      int[] raw, List<Integer> diceList, BoardAnalysisCache cache, PawnValueReport pawnValues
  ) {
    return buildOne(raw, diceList, cache, pawnValues, null);
  }

  public MoveCandidate buildOne(
      int[] raw,
      List<Integer> diceList,
      BoardAnalysisCache cache,
      PawnValueReport pawnValues,
      OpponentAnalysisReport opponents
  ) {
    if (raw == null || raw.length < 2 || diceList == null) {
      return null;
    }
    int pawn = raw[0];
    int diceIndex = raw[1];
    if (pawn < 0 || pawn >= cache.own().size()) {
      return null;
    }
    if (diceIndex < 0 || diceIndex >= diceList.size()) {
      return null;
    }
    int dice = diceList.get(diceIndex);
    int from = cache.own().get(pawn) == null ? JAIL : cache.own().get(pawn);
    int to = BotBoardMath.applySteps(cache.color(), from, dice);

    boolean threatFrom = cache.isThreatened(from);
    int threatTo = cache.threatSeatCount(to);

    VictimInfo victim = cache.findVictim(to);
    boolean capture = victim != null;
    int victimSeat = capture ? victim.seat : -1;
    boolean victimLeader =
        capture
            && (victim.seat == cache.leaderSeat()
                || (opponents != null
                    && opponents.enabled()
                    && opponents.isLeader(victim.seat)));
    int victimRem = Integer.MAX_VALUE;
    boolean justOut = false;
    if (capture) {
      victimRem = BotBoardMath.remainingDistance(victim.color, to);
      if (victimRem == Integer.MAX_VALUE) {
        victimRem = BotBoardMath.MAX_PAWN_PROGRESS;
      }
      justOut = cache.isNearStart(victim.color, to);
    }

    int pawnValue = BoardAnalysisCache.pawnValue(cache.color(), from);
    if (pawnValues != null && pawnValues.enabled()) {
      PawnPriority pr = pawnValues.get(pawn);
      if (pr != null) {
        pawnValue = pr.value();
      }
    }
    boolean createsBlock = isMain(to) && countOwnOn(cache.own(), pawn, to) >= 1;
    boolean blockProtects =
        createsBlock && BotBoardMath.pawnProgress(cache.color(), from) >= cache.bestOwnProgress() - 5;

    MoveType type = classify(from, to, capture, threatFrom, threatTo, createsBlock);
    return new MoveCandidate(
        raw,
        pawn,
        dice,
        diceIndex,
        from,
        to,
        type,
        threatFrom,
        threatTo,
        capture,
        victimSeat,
        victimLeader,
        victimRem,
        justOut,
        pawnValue,
        createsBlock,
        blockProtects);
  }

  private static MoveType classify(
      int from,
      int to,
      boolean capture,
      boolean threatFrom,
      int threatTo,
      boolean block
  ) {
    if (isJail(from)) {
      return MoveType.JAIL_EXIT;
    }
    if (isHome(to)) {
      return MoveType.HOME_FINISH;
    }
    if (isExit(from) || isExit(to)) {
      return MoveType.HOME_COLUMN;
    }
    if (capture) {
      return MoveType.CAPTURE;
    }
    if (threatFrom && threatTo == 0) {
      return MoveType.ESCAPE;
    }
    if (isSafe(to)) {
      return MoveType.SAFE_LAND;
    }
    if (block) {
      return MoveType.BLOCK;
    }
    return MoveType.ADVANCE;
  }

  private static int countOwnOn(List<Integer> own, int exclude, int cell) {
    int n = 0;
    for (int i = 0; i < own.size(); i++) {
      if (i == exclude) {
        continue;
      }
      Integer p = own.get(i);
      if (p != null && p == cell) {
        n++;
      }
    }
    return n;
  }
}
