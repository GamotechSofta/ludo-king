package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.LudoColor;
import com.ludo.backend.room.BotDifficulty;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Production Human Behavior Learning Engine (HARD only).
 *
 * <p>Observes only publicly visible actions in the current match. Never predicts
 * dice, never persists across matches, never exceeds {@code maxInfluence}.
 */
@Component
public class HumanBehaviorEngine {

  private static final Logger log = LoggerFactory.getLogger(HumanBehaviorEngine.class);

  private final BehaviorConfig config;
  private final BehaviorAnalyzer analyzer;
  private final PatternDetector patternDetector;
  private final BehaviorPredictor predictor;
  private final ConcurrentHashMap<String, DecisionMemory> memories = new ConcurrentHashMap<>();

  public HumanBehaviorEngine(
      BehaviorConfig config,
      BehaviorAnalyzer analyzer,
      PatternDetector patternDetector,
      BehaviorPredictor predictor
  ) {
    this.config = config;
    this.analyzer = analyzer;
    this.patternDetector = patternDetector;
    this.predictor = predictor;
  }

  public boolean enabled() {
    return config.enabled();
  }

  /** Observe a human dice roll (public face only). */
  public void observeRoll(String roomId, int seat, boolean isBot, int dice) {
    if (!config.enabled() || isBot || dice < 1 || dice > 6) {
      return;
    }
    memory(roomId, seat).add(BehaviorEvent.roll(dice));
  }

  /**
   * Observe a human pawn move using only board-visible outcomes.
   *
   * @param captured true if a capture occurred (public board change)
   */
  public void observeMove(
      String roomId,
      int seat,
      boolean isBot,
      LudoColor color,
      int pawnIndex,
      int dice,
      int from,
      int to,
      boolean captured,
      int[][] allTokens,
      LudoColor[] seatColors
  ) {
    if (!config.enabled() || isBot) {
      return;
    }
    boolean opening = isJail(from) && dice == 6;
    boolean safeLand = isSafe(to) || isExit(to) || isHome(to);
    boolean homePriority =
        isHome(to)
            || isExit(to)
            || (color != null && BotBoardMath.isNearHome(color, from));
    boolean fromThreat = isThreatened(seat, from, allTokens, seatColors);
    boolean toThreat = isThreatened(seat, to, allTokens, seatColors);
    boolean escape = fromThreat && !toThreat;
    boolean risky = isMain(to) && !isSafe(to) && !isHome(to) && toThreat;

    memory(roomId, seat)
        .add(
            new BehaviorEvent(
                BehaviorEvent.Kind.MOVE,
                dice,
                pawnIndex,
                from,
                to,
                captured,
                safeLand,
                homePriority,
                opening,
                escape,
                risky));
  }

  /**
   * Build adaptive response for HARD bot vs primary human threat.
   * Returns disabled profile if confidence is low or engine off.
   */
  public BehaviorProfile evaluate(
      String roomId,
      BotDifficulty difficulty,
      BotMatchAnalysis analysis,
      OpponentAnalysisReport opponents
  ) {
    long t0 = System.nanoTime();
    if (!config.enabled()
        || difficulty != BotDifficulty.HARD
        || analysis == null
        || analysis.mode == com.ludo.backend.bot.BotAiMode.OTHER
        || analysis.isBot == null) {
      return BehaviorProfile.disabled();
    }

    int humanSeat = primaryHumanSeat(analysis, opponents);
    if (humanSeat < 0) {
      return BehaviorProfile.disabled();
    }

    DecisionMemory mem = memories.get(key(roomId, humanSeat));
    if (mem == null || mem.size() == 0) {
      return BehaviorProfile.disabled();
    }

    List<BehaviorEvent> events = mem.snapshot();
    PlayerStatistics stats = PlayerStatistics.from(events);
    PatternDetector.Patterns patterns = patternDetector.detect(stats, events);
    BehaviorAnalyzer.Classification cls = analyzer.classify(stats, patterns);
    BehaviorPredictor.Prediction pred =
        predictor.predict(cls.style(), stats, patterns, cls.confidence());

    boolean influential =
        cls.confidence() >= config.confidenceThreshold()
            && pred.confidence() >= config.confidenceThreshold() * 0.85;

    ResponseWeights rw = responseFor(cls.style());
    // Scale toward neutral when not influential / low confidence
    double scale =
        influential
            ? config.maxInfluence() * (0.6 + 0.4 * cls.confidence())
            : 0.0;
    BehaviorProfile profile =
        new BehaviorProfile(
            humanSeat,
            cls.style(),
            cls.confidence(),
            cls.reason(),
            pred,
            patterns,
            stats,
            blend(1.0, rw.escape, scale),
            blend(1.0, rw.protect, scale),
            blend(1.0, rw.future, scale),
            blend(1.0, rw.home, scale),
            blend(1.0, rw.leader, scale),
            blend(1.0, rw.board, scale),
            blend(1.0, rw.safe, scale),
            rw.response,
            true,
            influential);

    if (log.isDebugEnabled() && influential) {
      log.debug(profile.debugLine());
    }

    long us = (System.nanoTime() - t0) / 1_000L;
    if (us > 1_000L && log.isDebugEnabled()) {
      log.debug("HumanBehaviorEngine {}µs (budget 1000µs)", us);
    }
    return profile;
  }

  /** Apply capped behavior deltas to move score (≤ maxInfluence of base bonuses). */
  public void apply(MoveScore score, MoveCandidate move, BehaviorProfile profile, AIScoreConfig cfg) {
    if (score == null
        || move == null
        || profile == null
        || !profile.enabled()
        || !profile.influential()
        || cfg == null) {
      return;
    }
    int budget = (int) Math.round(Math.abs(cfg.escapeBonus() + cfg.safeBonus()) * config.maxInfluence());
    int used = 0;

    used += addCapped(score, "Behavior Escape", move.underThreatAtFrom() && move.threatCountAtTo() == 0,
        (int) Math.round(cfg.escapeBonus() * (profile.escapeWeight() - 1.0)), budget - used);
    used += addCapped(score, "Behavior Safe", isSafe(move.to()) || isExit(move.to()),
        (int) Math.round(cfg.safeBonus() * (profile.safeWeight() - 1.0) / 2), budget - used);
    used += addCapped(score, "Behavior Home", isHome(move.to()) || isExit(move.to()),
        (int) Math.round(cfg.homeBonus() * (profile.homeWeight() - 1.0) / 2), budget - used);
    used += addCapped(score, "Behavior Protect", move.underThreatAtFrom(),
        (int) Math.round(cfg.protectAdvancedBonus() * (profile.protectWeight() - 1.0) / 3), budget - used);
    if (move.capture() && move.victimIsLeader()) {
      used += addCapped(score, "Behavior Leader Track", true,
          (int) Math.round(cfg.captureLeaderBonus() * (profile.leaderWeight() - 1.0) / 3), budget - used);
    }
    if (move.createsBlock() || (isMain(move.to()) && !isSafe(move.to()))) {
      addCapped(score, "Behavior Board Control", true,
          (int) Math.round(cfg.boardControlBonus() * (profile.boardControlWeight() - 1.0) / 2), budget - used);
    }
  }

  public double futureMultiplier(BehaviorProfile profile) {
    if (profile == null || !profile.enabled() || !profile.influential()) {
      return 1.0;
    }
    // Cap future boost within maxInfluence band
    double w = profile.futureWeight();
    return 1.0 + Math.max(-config.maxInfluence(), Math.min(config.maxInfluence(), w - 1.0));
  }

  public void clear(String roomId) {
    if (roomId == null) {
      return;
    }
    String prefix = roomId + "#";
    memories.keySet().removeIf(k -> k.startsWith(prefix));
  }

  public void clearSeat(String roomId, int seat) {
    memories.remove(key(roomId, seat));
  }

  private DecisionMemory memory(String roomId, int seat) {
    return memories.computeIfAbsent(key(roomId, seat), k -> new DecisionMemory(config.history()));
  }

  private static String key(String roomId, int seat) {
    return (roomId == null ? "_" : roomId) + "#" + seat;
  }

  private static int primaryHumanSeat(BotMatchAnalysis analysis, OpponentAnalysisReport opponents) {
    // Prefer opponent primary target if human; else first human seat
    if (opponents != null && opponents.enabled()) {
      int t = opponents.primaryTargetSeat();
      if (t >= 0 && analysis.isBot != null && t < analysis.isBot.length && !analysis.isBot[t]) {
        return t;
      }
      int leader = opponents.currentLeaderSeat();
      if (leader >= 0
          && analysis.isBot != null
          && leader < analysis.isBot.length
          && !analysis.isBot[leader]) {
        return leader;
      }
    }
    if (analysis.isBot != null) {
      for (int i = 0; i < analysis.isBot.length; i++) {
        if (!analysis.isBot[i]) {
          return i;
        }
      }
    }
    return -1;
  }

  private static ResponseWeights responseFor(HumanPlayStyle style) {
    return switch (style) {
      case AGGRESSIVE ->
          new ResponseWeights(1.35, 1.25, 1.20, 1.05, 1.05, 1.00, 1.15, "Increase escape priority");
      case SPEED_RUNNER ->
          new ResponseWeights(1.10, 1.15, 1.15, 1.30, 1.35, 1.00, 1.10, "Increase leader tracking");
      case DEFENSIVE ->
          new ResponseWeights(1.00, 1.05, 1.05, 1.05, 1.00, 1.30, 1.05, "Increase board control");
      case RISK_TAKER ->
          new ResponseWeights(1.20, 1.20, 1.15, 1.10, 1.05, 1.05, 1.30, "Create safer board positions");
      case SAFE_PLAYER ->
          new ResponseWeights(1.05, 1.05, 1.05, 1.15, 1.10, 1.20, 1.05, "Expand pawn presence");
      default ->
          new ResponseWeights(1.05, 1.05, 1.05, 1.05, 1.05, 1.05, 1.05, "Maintain balanced pressure");
    };
  }

  private static double blend(double neutral, double target, double scale) {
    if (scale <= 0) {
      return neutral;
    }
    // scale is small (≤0.10); map target offset into that band
    double delta = (target - neutral) * (scale / 0.10);
    return neutral + delta;
  }

  private static int addCapped(MoveScore score, String label, boolean cond, int delta, int remaining) {
    if (!cond || delta == 0 || remaining <= 0) {
      return 0;
    }
    int d = Math.max(-remaining, Math.min(remaining, delta));
    if (d != 0) {
      score.add(label, d);
    }
    return Math.abs(d);
  }

  /** Public-board threat: any other seat can land on cell with die 1–6. */
  static boolean isThreatened(int defender, int pos, int[][] tokens, LudoColor[] colors) {
    if (tokens == null || !isMain(pos) || isSafe(pos) || isHome(pos) || isExit(pos)) {
      return false;
    }
    for (int s = 0; s < tokens.length; s++) {
      if (s == defender || tokens[s] == null) {
        continue;
      }
      LudoColor c = colors != null && s < colors.length ? colors[s] : null;
      if (c == null) {
        continue;
      }
      for (int p = 0; p < tokens[s].length; p++) {
        int from = tokens[s][p];
        if (!isMain(from)) {
          continue;
        }
        for (int d = 1; d <= 6; d++) {
          if (BotBoardMath.applySteps(c, from, d) == pos) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private record ResponseWeights(
      double escape,
      double protect,
      double future,
      double home,
      double leader,
      double board,
      double safe,
      String response
  ) {}
}
