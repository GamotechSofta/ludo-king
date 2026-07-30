package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Builds per-seat {@link OpponentProfile} snapshots from the live board.
 */
@Component
public class OpponentAnalyzer {

  private final OpponentAnalysisConfig config;
  private final OpponentHistory history;

  public OpponentAnalyzer(OpponentAnalysisConfig config, OpponentHistory history) {
    this.config = config;
    this.history = history;
  }

  public List<OpponentProfile> analyzeAll(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      BotMatchAnalysis match,
      DangerMap dangerMap
  ) {
    List<OpponentProfile> out = new ArrayList<>();
    if (snap == null || snap.getSeatColors() == null) {
      return out;
    }
    List<String> colors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    boolean[] isBot = snap.getIsBot();
    int seats = colors.size();

    // First pass: raw stats + leader scores
    int[] rawScores = new int[seats];
    RawSeat[] raw = new RawSeat[seats];
    int maxScore = 0;
    for (int s = 0; s < seats; s++) {
      raw[s] = buildRaw(s, colors.get(s), all, isBot, dangerMap);
      LeaderScore ls = scoreLeader(raw[s], roomId, s);
      rawScores[s] = ls.total();
      maxScore = Math.max(maxScore, ls.total());
      raw[s].leaderScore = ls;
    }

    for (int s = 0; s < seats; s++) {
      out.add(toProfile(roomId, s, raw[s], rawScores, maxScore, botSeat, match));
    }
    return out;
  }

  private RawSeat buildRaw(
      int seat,
      String name,
      Map<String, List<Integer>> all,
      boolean[] isBot,
      DangerMap dangerMap
  ) {
    LudoColor color = BotBoardMath.parseColor(name);
    List<Integer> pos = all != null ? all.get(name) : null;
    int finished = 0;
    int active = 0;
    int jail = 0;
    int safe = 0;
    int totalProg = 0;
    int bestPawn = -1;
    int bestProg = -1;
    int dangerousPawn = -1;
    int dangerousScore = -1;
    int exposedPawn = -1;
    int exposedDanger = -1;
    int weakPawn = -1;
    int weakProg = Integer.MAX_VALUE;
    int len = pos != null ? pos.size() : 4;
    for (int i = 0; i < len; i++) {
      int p = pos == null || pos.get(i) == null ? JAIL : pos.get(i);
      if (isHome(p)) {
        finished++;
        totalProg += BotBoardMath.MAX_PAWN_PROGRESS;
        continue;
      }
      if (isJail(p)) {
        jail++;
        continue;
      }
      active++;
      if (isSafe(p) || isExit(p)) {
        safe++;
      }
      int prog = BotBoardMath.pawnProgress(color, p);
      totalProg += prog;
      if (prog > bestProg) {
        bestProg = prog;
        bestPawn = i;
      }
      int rem = BotBoardMath.remainingDistance(color, p);
      int danger = 0;
      if (dangerMap != null && isMain(p) && !isSafe(p)) {
        danger = dangerMap.dangerAt(p);
      }
      int dScore = prog + (rem != Integer.MAX_VALUE && rem <= HOME_STEPS + 10 ? 40 : 0);
      if (dScore > dangerousScore) {
        dangerousScore = dScore;
        dangerousPawn = i;
      }
      if (danger > exposedDanger) {
        exposedDanger = danger;
        exposedPawn = i;
      }
      if (prog < weakProg) {
        weakProg = prog;
        weakPawn = i;
      }
    }
    int avg = active + finished > 0 ? totalProg / Math.max(1, active + finished) : 0;
    boolean bot = isBot != null && seat < isBot.length && isBot[seat];
    RawSeat r = new RawSeat();
    r.seat = seat;
    r.name = name;
    r.color = color;
    r.bot = bot;
    r.finished = finished;
    r.active = active;
    r.jail = jail;
    r.safe = safe;
    r.totalProg = totalProg;
    r.avgProg = avg;
    r.leaderPawn = bestPawn;
    r.leaderProg = Math.max(0, bestProg);
    r.dangerousPawn = dangerousPawn;
    r.exposedPawn = exposedPawn;
    r.weakPawn = weakPawn;
    r.positions = pos;
    return r;
  }

  LeaderScore scoreLeader(RawSeat r, String roomId, int seat) {
    List<String> reasons = new ArrayList<>(6);
    int score = r.totalProg;
    if (r.finished > 0) {
      score += r.finished * config.finishedPawnPoints();
      reasons.add(r.finished + " Finished Pawns");
    }
    if (r.positions != null && r.color != null) {
      int near = 0;
      int advanced = 0;
      for (Integer p : r.positions) {
        int pos = p == null ? JAIL : p;
        if (isJail(pos) || isHome(pos)) {
          continue;
        }
        if (BotBoardMath.isNearHome(r.color, pos) || isExit(pos)) {
          near++;
        } else if (BotBoardMath.pawnProgress(r.color, pos)
            >= BotBoardMath.MAX_PAWN_PROGRESS * 0.55) {
          advanced++;
        }
      }
      if (near > 0) {
        score += near * config.nearHomePoints();
        reasons.add("Near Home Pawn");
      }
      if (advanced > 0) {
        score += advanced * config.advancedPoints();
        reasons.add("Advanced Pawn");
      }
    }
    if (r.safe > 0) {
      score += r.safe * config.safePawnPoints();
      reasons.add("Safe Pawn");
    }
    if (r.jail > 0) {
      score -= r.jail * config.jailPawnPenalty();
    }
    int captured = history.recentCaptures(roomId, seat);
    if (captured > 0) {
      // Being captured recently lowers standing
      score -= Math.min(45, captured * config.capturedRecentlyPenalty());
    }
    if (r.leaderProg > 0) {
      reasons.add("Highest Progress");
    }
    return new LeaderScore(Math.max(0, score), reasons);
  }

  private OpponentProfile toProfile(
      String roomId,
      int seat,
      RawSeat r,
      int[] scores,
      int maxScore,
      int botSeat,
      BotMatchAnalysis match
  ) {
    double winProb = winningProbability(r, scores[seat], maxScore);
    int threat = threatScore(r, scores[seat], maxScore, winProb, roomId, seat);
    if (config.threatPrediction() && r.finished >= 2 && r.leaderProg >= BotBoardMath.MAX_PAWN_PROGRESS * 0.5) {
      threat = Math.min(100, threat + 12);
    }
    PlayerThreat band = PlayerThreat.fromScore(threat);
    boolean weak = r.active == 0 || (r.jail >= 3 && r.finished == 0 && r.totalProg < 30);
    boolean critical = r.finished >= 3;
    if (critical) {
      threat = Math.max(threat, 90);
      band = PlayerThreat.CRITICAL;
    }
    boolean futureLeader =
        config.threatPrediction()
            && scores[seat] + config.similarScoreMargin() >= maxScore
            && seat != botSeat
            && r.leaderProg >= BotBoardMath.MAX_PAWN_PROGRESS * 0.45;
    PlayStyle style = history.inferStyle(roomId, seat);
    return new OpponentProfile(
        seat,
        r.name,
        r.color,
        r.bot,
        r.finished,
        r.active,
        r.jail,
        r.safe,
        r.totalProg,
        r.avgProg,
        r.leaderPawn,
        r.leaderProg,
        r.dangerousPawn,
        r.exposedPawn,
        r.weakPawn,
        r.leaderScore,
        threat,
        band,
        winProb,
        weak,
        critical,
        futureLeader,
        style,
        false,
        false);
  }

  private int threatScore(
      RawSeat r, int leaderScore, int maxScore, double winProb, String roomId, int seat
  ) {
    int t = (int) Math.round(winProb * 55);
    t += Math.min(25, (leaderScore * 25) / Math.max(1, maxScore));
    if (r.finished >= 3) {
      t += 30;
    } else if (r.finished >= 2) {
      t += 15;
    }
    if (r.leaderProg >= BotBoardMath.MAX_PAWN_PROGRESS - 6) {
      t += 20; // one move from home-ish
    }
    PlayStyle style = history.inferStyle(roomId, seat);
    if (style == PlayStyle.AGGRESSIVE || style == PlayStyle.CAPTURE_FOCUSED) {
      t += 8;
    }
    if (style == PlayStyle.FAST_RUNNER) {
      t += 6;
    }
    return Math.max(0, Math.min(100, t));
  }

  private static double winningProbability(RawSeat r, int score, int maxScore) {
    double progressPart =
        Math.min(1.0, r.totalProg / (double) Math.max(1, BotBoardMath.MAX_PAWN_PROGRESS * 4));
    double relative = maxScore <= 0 ? 0 : score / (double) maxScore;
    double finishBoost = r.finished * 0.12;
    return Math.max(0, Math.min(0.99, 0.35 * progressPart + 0.45 * relative + finishBoost));
  }

  static final class RawSeat {
    int seat;
    String name;
    LudoColor color;
    boolean bot;
    int finished;
    int active;
    int jail;
    int safe;
    int totalProg;
    int avgProg;
    int leaderPawn;
    int leaderProg;
    int dangerousPawn;
    int exposedPawn;
    int weakPawn;
    List<Integer> positions;
    LeaderScore leaderScore;
  }
}
