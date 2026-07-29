package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.exitIndex;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;

import com.ludo.backend.config.HumanCaptureDiceAssistProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Human-only capture dice assist: when a human pawn can legally capture a bot
 * pawn within 1–6 pips, raise (but do not guarantee) the chance of rolling the
 * exact value needed.
 */
@Component
public class HumanCaptureDiceAssist {

  @FunctionalInterface
  public interface MoveLegality {
    boolean canMove(int tokenIndex, int dice);
  }

  public record CaptureScanContext(
      int humanSeat,
      LudoColor humanColor,
      LudoColor[] colors,
      int[][] tokens,
      boolean[] isBot,
      boolean[] eliminated,
      boolean[] finished,
      int maxPlayers
  ) {}

  static final class CaptureOpportunity {
    final int dice;
    final int victimRemaining;

    CaptureOpportunity(int dice, int victimRemaining) {
      this.dice = dice;
      this.victimRemaining = victimRemaining;
    }
  }

  private final HumanCaptureDiceAssistProperties props;

  @Autowired
  public HumanCaptureDiceAssist(HumanCaptureDiceAssistProperties props) {
    this.props = props;
  }

  /** For unit tests without Spring. */
  HumanCaptureDiceAssist(boolean enabled, int assistChancePct) {
    this.props = new HumanCaptureDiceAssistProperties(enabled, assistChancePct);
  }

  public boolean isEnabled() {
    return props.enabled();
  }

  public int assistChancePct() {
    return props.assistChancePct();
  }

  public boolean hasPawnOnBoard(GameEngineService.MatchRuntime rt, int seat) {
    if (rt == null || seat < 0 || seat >= rt.maxPlayers) {
      return false;
    }
    for (int t = 0; t < 4; t++) {
      int pos = rt.tokens[seat][t];
      if (!isJail(pos) && !isHome(pos)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @return exact capture die 1–6, or {@code null} to use normal random generation
   */
  public Integer maybePickCaptureDice(
      GameEngineService.MatchRuntime rt,
      int humanSeat,
      MoveLegality legality,
      Random rng
  ) {
    if (!props.enabled() || rt == null || legality == null || humanSeat < 0) {
      return null;
    }
    if (humanSeat >= rt.maxPlayers || rt.isBot[humanSeat]) {
      return null;
    }
    CaptureScanContext ctx =
        new CaptureScanContext(
            humanSeat,
            rt.colors[humanSeat],
            rt.colors,
            rt.tokens,
            rt.isBot,
            rt.eliminated,
            rt.finished,
            rt.maxPlayers);
    Integer best = pickBestCaptureDice(ctx, legality, rng);
    if (best == null || rng == null) {
      return null;
    }
    if (rng.nextInt(100) >= props.assistChancePct()) {
      return null;
    }
    return best;
  }

  /** Always picks the best capture die when one exists (tests / diagnostics). */
  public Integer pickBestCaptureDice(
      CaptureScanContext ctx,
      MoveLegality legality,
      Random rng
  ) {
    if (ctx == null || legality == null || ctx.humanSeat() < 0) {
      return null;
    }
    if (ctx.isBot()[ctx.humanSeat()]) {
      return null;
    }

    List<CaptureOpportunity> opportunities = new ArrayList<>();
    int[] own = ctx.tokens()[ctx.humanSeat()];
    for (int t = 0; t < own.length; t++) {
      int from = own[t];
      if (isJail(from) || isHome(from)) {
        continue;
      }
      for (int dice = 1; dice <= 6; dice++) {
        if (!legality.canMove(t, dice)) {
          continue;
        }
        int land = applySteps(ctx.humanColor(), from, dice);
        if (!isCapturableBotAt(ctx, land, ctx.humanSeat())) {
          continue;
        }
        int victimRem = closestBotVictimRemaining(ctx, land, ctx.humanSeat());
        opportunities.add(new CaptureOpportunity(dice, victimRem));
      }
    }

    if (opportunities.isEmpty()) {
      return null;
    }

    opportunities.sort(
        Comparator.comparingInt((CaptureOpportunity o) -> o.victimRemaining));
    CaptureOpportunity best = opportunities.get(0);
    List<CaptureOpportunity> ties = new ArrayList<>();
    for (CaptureOpportunity o : opportunities) {
      if (o.victimRemaining == best.victimRemaining) {
        ties.add(o);
      }
    }
    Random pickRng = rng != null ? rng : new Random();
    CaptureOpportunity chosen = ties.get(pickRng.nextInt(ties.size()));
    return chosen.dice;
  }

  private static boolean isCapturableBotAt(CaptureScanContext ctx, int landPos, int humanSeat) {
    if (!isMain(landPos) || isSafe(landPos)) {
      return false;
    }
    for (int s = 0; s < ctx.maxPlayers(); s++) {
      if (s == humanSeat || !ctx.isBot()[s] || ctx.eliminated()[s] || ctx.finished()[s]) {
        continue;
      }
      int count = 0;
      for (int t = 0; t < 4; t++) {
        if (ctx.tokens()[s][t] == landPos) {
          count++;
        }
      }
      if (count == 1) {
        return true;
      }
    }
    return false;
  }

  private static int closestBotVictimRemaining(
      CaptureScanContext ctx, int landPos, int humanSeat
  ) {
    int best = Integer.MAX_VALUE;
    for (int s = 0; s < ctx.maxPlayers(); s++) {
      if (s == humanSeat || !ctx.isBot()[s] || ctx.eliminated()[s] || ctx.finished()[s]) {
        continue;
      }
      int count = 0;
      for (int t = 0; t < 4; t++) {
        if (ctx.tokens()[s][t] == landPos) {
          count++;
        }
      }
      if (count != 1) {
        continue;
      }
      int rem = remainingDistance(ctx.colors()[s], landPos);
      best = Math.min(best, rem);
    }
    return best == Integer.MAX_VALUE ? TOTAL_TILES + HOME_STEPS : best;
  }

  private static int remainingDistance(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return Integer.MAX_VALUE;
    }
    if (isExit(pos)) {
      return HOME_STEPS - 1 - exitIndex(pos);
    }
    int toExit = (color.exitTile() - pos + TOTAL_TILES) % TOTAL_TILES;
    return toExit + HOME_STEPS;
  }

  private static int applySteps(LudoColor color, int from, int steps) {
    if (isJail(from)) {
      return color.startTile();
    }
    int pos = from;
    for (int i = 0; i < steps; i++) {
      if (isMain(pos)) {
        if (pos == color.exitTile()) {
          pos = toExit(0);
        } else {
          pos = (pos + 1) % TOTAL_TILES;
        }
      } else if (isExit(pos)) {
        int idx = exitIndex(pos);
        if (idx >= EXIT_LEN - 1) {
          pos = HOME;
        } else {
          pos = toExit(idx + 1);
        }
      } else {
        break;
      }
    }
    return pos;
  }
}
