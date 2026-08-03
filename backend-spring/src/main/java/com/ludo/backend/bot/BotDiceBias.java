package com.ludo.backend.bot;

import static com.ludo.backend.bot.superior.ProgressCodec.FINISHED_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.MAIN_PATH_LAST_PROGRESS;
import static com.ludo.backend.bot.superior.ProgressCodec.absoluteMainTile;
import static com.ludo.backend.bot.superior.ProgressCodec.canMoveToken;
import static com.ludo.backend.bot.superior.ProgressCodec.isSafeMainProgress;
import static com.ludo.backend.bot.superior.ProgressCodec.toProgress;
import static com.ludo.backend.game.BoardConstants.JAIL;

import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dice bias for online matches (port of luzo BotDiceBias). Tuned in code only — settings mirror
 * offline board defaults.
 */
public final class BotDiceBias {

  public static final double TARGET_PLAYER_TO_BOT_SIX_RATIO = 7.0 / 11.0;
  public static final int FIRST_SIX_MIN_ROLL_NUMBER = 2;
  public static final int FIRST_SIX_MAX_ROLL_NUMBER = 6;
  public static final int MIN_TOTAL_SIXES_BEFORE_BALANCE = 3;
  public static final double MAX_SIX_PROBABILITY = 0.35;
  /** Bot→bot kill favor (~2–3 of 10 chances). */
  public static final int BOT_VS_BOT_KILL_FAVOR_PERCENT = 25;
  /** Favor for the exact face that walks a bot token onto its home square. */
  public static final int BOT_HOME_FINISH_FAVOR_PERCENT = 60;

  private BotDiceBias() {}

  /**
   * Dice bias settings. Tuned in code only, so online matches always behave like the offline board.
   */
  public static final class BotDiceSettings {
    public static final int DEFAULT_BOT_KILL_FAVOR_PERCENT = 55;
    public static final int DEFAULT_USER_KILL_FAVOR_PERCENT = 30;
    public static final int DEFAULT_SIX_BOOST_PERCENT = 15;

    public static final BotDiceSettings DEFAULT = new BotDiceSettings();

    public final int botKillFavor2Player;
    public final int userKillFavor2Player;
    public final int botKillFavorMultiPlayer;
    public final int userKillFavorMultiPlayer;
    public final int botSixBoostPercent;

    public BotDiceSettings() {
      this(
          DEFAULT_BOT_KILL_FAVOR_PERCENT,
          DEFAULT_USER_KILL_FAVOR_PERCENT,
          DEFAULT_BOT_KILL_FAVOR_PERCENT,
          DEFAULT_USER_KILL_FAVOR_PERCENT,
          DEFAULT_SIX_BOOST_PERCENT);
    }

    public BotDiceSettings(
        int botKillFavor2Player,
        int userKillFavor2Player,
        int botKillFavorMultiPlayer,
        int userKillFavorMultiPlayer,
        int botSixBoostPercent) {
      this.botKillFavor2Player = botKillFavor2Player;
      this.userKillFavor2Player = userKillFavor2Player;
      this.botKillFavorMultiPlayer = botKillFavorMultiPlayer;
      this.userKillFavorMultiPlayer = userKillFavorMultiPlayer;
      this.botSixBoostPercent = botSixBoostPercent;
    }

    public int botKillFavorFor(int seatCount) {
      return seatCount == 2 ? botKillFavor2Player : botKillFavorMultiPlayer;
    }

    public int userKillFavorFor(int seatCount) {
      return seatCount == 2 ? userKillFavor2Player : userKillFavorMultiPlayer;
    }
  }

  /** Per-match dice context for six balancing and first-six window rules. */
  public static final class DiceRollContext {
    public static final DiceRollContext DEFAULT = new DiceRollContext();

    public final int rollerMatchDiceRollCount;
    public final int rollerMatchSixCount;
    public final int botMatchSixRolls;
    public final int playerMatchSixRolls;

    public DiceRollContext() {
      this(0, 0, 0, 0);
    }

    public DiceRollContext(
        int rollerMatchDiceRollCount,
        int rollerMatchSixCount,
        int botMatchSixRolls,
        int playerMatchSixRolls) {
      this.rollerMatchDiceRollCount = rollerMatchDiceRollCount;
      this.rollerMatchSixCount = rollerMatchSixCount;
      this.botMatchSixRolls = botMatchSixRolls;
      this.playerMatchSixRolls = playerMatchSixRolls;
    }
  }

  record HomeFinishOption(int tokenIndex, int dice) {}

  /** Build per-seat progress views from a snapshot plus per-seat roll counters. */
  public static List<BotPlayerView> buildPlayers(
      GameSnapshot snap, int[] matchDiceRollCounts, int[] matchSixCounts) {
    if (snap == null) {
      return List.of();
    }
    List<String> seatColors = snap.getSeatColors();
    if (seatColors == null || seatColors.isEmpty()) {
      return List.of();
    }
    boolean[] isBot = snap.getIsBot();
    boolean[] eliminated = snap.getEliminated();
    boolean[] finished = snap.getFinished();
    var positions = snap.getTokenPositions();

    List<BotPlayerView> players = new ArrayList<>(seatColors.size());
    for (int seat = 0; seat < seatColors.size(); seat++) {
      String colorName = seatColors.get(seat);
      LudoColor color = BotBoardMath.parseColor(colorName);
      if (color == null) {
        try {
          color = LudoColor.valueOf(colorName);
        } catch (RuntimeException ex) {
          color = LudoColor.RED;
        }
      }
      int[] tokens = toProgressTokens(color, positions != null ? positions.get(colorName) : null);
      boolean bot = isBot != null && seat < isBot.length && isBot[seat];
      boolean abandoned =
          (eliminated != null && seat < eliminated.length && eliminated[seat])
              || (finished != null && seat < finished.length && finished[seat]);
      int rollCount =
          matchDiceRollCounts != null && seat < matchDiceRollCounts.length
              ? matchDiceRollCounts[seat]
              : 0;
      int sixCount =
          matchSixCounts != null && seat < matchSixCounts.length ? matchSixCounts[seat] : 0;
      players.add(new BotPlayerView(color, tokens, bot, abandoned, rollCount, sixCount));
    }
    return players;
  }

  static int[] toProgressTokens(LudoColor color, List<Integer> boardPositions) {
    int n = boardPositions != null ? boardPositions.size() : 4;
    if (n <= 0) {
      n = 4;
    }
    int[] tokens = new int[n];
    for (int i = 0; i < n; i++) {
      int boardPos = JAIL;
      if (boardPositions != null && i < boardPositions.size() && boardPositions.get(i) != null) {
        boardPos = boardPositions.get(i);
      }
      tokens[i] = toProgress(color, boardPos);
    }
    return tokens;
  }

  public static int aggregateBotMatchSixRolls(List<BotPlayerView> players) {
    if (players == null) {
      return 0;
    }
    int sum = 0;
    for (BotPlayerView player : players) {
      if (player.isBot() && !player.isEffectivelyAbandoned()) {
        sum += player.matchSixCount();
      }
    }
    return sum;
  }

  public static int aggregatePlayerMatchSixRolls(List<BotPlayerView> players) {
    if (players == null) {
      return 0;
    }
    int sum = 0;
    for (BotPlayerView player : players) {
      if (!player.isBot() && !player.isEffectivelyAbandoned()) {
        sum += player.matchSixCount();
      }
    }
    return sum;
  }

  public static DiceRollContext buildDiceRollContext(List<BotPlayerView> players, int playerIndex) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return DiceRollContext.DEFAULT;
    }
    BotPlayerView roller = players.get(playerIndex);
    return new DiceRollContext(
        roller.matchDiceRollCount(),
        roller.matchSixCount(),
        aggregateBotMatchSixRolls(players),
        aggregatePlayerMatchSixRolls(players));
  }

  /** First match six only on rolls 2–6 (roll 1 blocked; roll 7+ allowed as fallback). */
  public static boolean allowsFirstSixOnThisRoll(int rollerMatchSixCount, int nextRollNumber) {
    if (rollerMatchSixCount > 0) {
      return true;
    }
    return nextRollNumber >= FIRST_SIX_MIN_ROLL_NUMBER;
  }

  public static double computeSixProbabilityNudge(
      boolean isBot, int botMatchSixRolls, int playerMatchSixRolls, Random random) {
    int totalSixes = botMatchSixRolls + playerMatchSixRolls;
    if (totalSixes < MIN_TOTAL_SIXES_BEFORE_BALANCE) {
      return 0.0;
    }

    double expectedPlayerSixes = botMatchSixRolls * TARGET_PLAYER_TO_BOT_SIX_RATIO;
    double playerGap = expectedPlayerSixes - playerMatchSixRolls;
    double expectedBotSixes =
        TARGET_PLAYER_TO_BOT_SIX_RATIO <= 0.0
            ? botMatchSixRolls
            : playerMatchSixRolls / TARGET_PLAYER_TO_BOT_SIX_RATIO;
    double botGap = expectedBotSixes - botMatchSixRolls;
    double jitter = (random.nextDouble() - 0.5) * 0.02;

    if (isBot) {
      if (botGap > 0.75) {
        return Math.min(0.10, Math.max(0.0, botGap * 0.03)) + jitter;
      }
      if (botGap < -1.0) {
        return Math.max(-0.05, Math.min(0.0, botGap * 0.015)) + jitter;
      }
      return jitter * 0.4;
    }
    if (playerGap > 0.75) {
      return Math.min(0.08, Math.max(0.0, playerGap * 0.022)) + jitter;
    }
    if (playerGap < -1.0) {
      return Math.max(-0.05, Math.min(0.0, playerGap * 0.015)) + jitter;
    }
    return jitter * 0.4;
  }

  /** Gentle boost while still hunting for the first six inside rolls 2–6. */
  public static double firstSixWindowNudge(
      int rollerMatchSixCount, int nextRollNumber, Random random) {
    if (rollerMatchSixCount > 0) {
      return 0.0;
    }
    if (nextRollNumber < FIRST_SIX_MIN_ROLL_NUMBER
        || nextRollNumber > FIRST_SIX_MAX_ROLL_NUMBER) {
      return 0.0;
    }
    return 0.03 + random.nextDouble() * 0.02;
  }

  /** Dice values (1–6) that would capture a human opponent token for this player. */
  public static List<Integer> findKillDiceValues(List<BotPlayerView> players, int playerIndex) {
    return findCaptureDiceValues(players, playerIndex, true, false);
  }

  /** Dice faces that would kill another bot and would not also kill a real player. */
  public static List<Integer> findBotOnlyKillDiceValues(
      List<BotPlayerView> players, int playerIndex) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return List.of();
    }
    if (!players.get(playerIndex).isBot()) {
      return List.of();
    }
    Set<Integer> humanKillFaces = new LinkedHashSet<>(findKillDiceValues(players, playerIndex));
    Set<Integer> anyBotKillFaces =
        new LinkedHashSet<>(findCaptureDiceValues(players, playerIndex, false, true));
    anyBotKillFaces.removeAll(humanKillFaces);
    return new ArrayList<>(anyBotKillFaces);
  }

  private static List<Integer> findCaptureDiceValues(
      List<BotPlayerView> players, int playerIndex, boolean humansOnly, boolean botsOnly) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return List.of();
    }
    BotPlayerView roller = players.get(playerIndex);
    Set<Integer> killFaces = new LinkedHashSet<>();

    for (int dice = 1; dice <= 6; dice++) {
      for (int tokenIndex = 0; tokenIndex < roller .tokens().length; tokenIndex++) {
        int progress = roller .tokens()[tokenIndex];
        if (!canMoveToken(progress, dice)) {
          continue;
        }
        int nextProgress = progress == -1 ? 0 : progress + dice;
        if (nextProgress < 0 || nextProgress > MAIN_PATH_LAST_PROGRESS) {
          continue;
        }
        if (isSafeMainProgress(roller .color(), nextProgress)) {
          continue;
        }
        int landingTile = absoluteMainTile(roller .color(), nextProgress);

        boolean wouldCapture = false;
        for (int opponentIndex = 0; opponentIndex < players.size(); opponentIndex++) {
          if (opponentIndex == playerIndex) {
            continue;
          }
          BotPlayerView opponent = players.get(opponentIndex);
          if (opponent.isEffectivelyAbandoned()) {
            continue;
          }
          if (humansOnly && opponent.isBot()) {
            continue;
          }
          if (botsOnly && !opponent.isBot()) {
            continue;
          }
          for (int opponentTokenIndex = 0;
              opponentTokenIndex < opponent .tokens().length;
              opponentTokenIndex++) {
            int opponentProgress = opponent .tokens()[opponentTokenIndex];
            if (opponentProgress >= 0
                && opponentProgress <= MAIN_PATH_LAST_PROGRESS
                && absoluteMainTile(opponent .color(), opponentProgress) == landingTile) {
              wouldCapture = true;
              break;
            }
          }
          if (wouldCapture) {
            break;
          }
        }
        if (wouldCapture) {
          killFaces.add(dice);
        }
      }
    }
    return new ArrayList<>(killFaces);
  }

  static List<HomeFinishOption> findHomeFinishOptions(
      List<BotPlayerView> players, int playerIndex) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return List.of();
    }
    BotPlayerView roller = players.get(playerIndex);
    List<HomeFinishOption> options = new ArrayList<>();
    for (int tokenIndex = 0; tokenIndex < roller .tokens().length; tokenIndex++) {
      int progress = roller .tokens()[tokenIndex];
      if (progress < 0 || progress >= FINISHED_PROGRESS) {
        continue;
      }
      int dice = FINISHED_PROGRESS - progress;
      if (dice < 1 || dice > 6 || !canMoveToken(progress, dice)) {
        continue;
      }
      options.add(new HomeFinishOption(tokenIndex, dice));
    }
    return options;
  }

  private static BotDiceDecision rollWithHomeFinishFavor(
      boolean allowSix,
      List<BotPlayerView> players,
      int playerIndex,
      int favorPercent,
      Random random) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return null;
    }
    if (!players.get(playerIndex).isBot()) {
      return null;
    }
    List<HomeFinishOption> options = new ArrayList<>();
    for (HomeFinishOption option : findHomeFinishOptions(players, playerIndex)) {
      if (allowSix || option.dice != 6) {
        options.add(option);
      }
    }
    if (options.isEmpty()) {
      return null;
    }
    if (random.nextInt(100) >= favorPercent) {
      return null;
    }
    HomeFinishOption chosen = options.get(random.nextInt(options.size()));
    return new BotDiceDecision(chosen.dice, chosen.tokenIndex);
  }

  /** Soft bot→bot kill favor: about 25% of the time force a bot-only kill face. */
  public static Integer rollWithBotVsBotKillFavor(
      boolean allowSix, List<BotPlayerView> players, int playerIndex, Random random) {
    if (players == null || playerIndex < 0 || playerIndex >= players.size()) {
      return null;
    }
    if (!players.get(playerIndex).isBot()) {
      return null;
    }
    List<Integer> botOnlyKills = new ArrayList<>();
    for (int face : findBotOnlyKillDiceValues(players, playerIndex)) {
      if (allowSix || face != 6) {
        botOnlyKills.add(face);
      }
    }
    if (botOnlyKills.isEmpty()) {
      return null;
    }
    if (random.nextInt(100) >= BOT_VS_BOT_KILL_FAVOR_PERCENT) {
      return null;
    }
    return botOnlyKills.get(random.nextInt(botOnlyKills.size()));
  }

  private static boolean resolveAllowSix(
      int consecutiveSixCount, boolean isBot, DiceRollContext context) {
    // First-six window (no 6 on roll 1) is bot-only. Humans need a fair chance to
    // leave jail on their opening rolls — otherwise every early turn is an empty PASS.
    if (isBot) {
      int nextRollNumber = context.rollerMatchDiceRollCount + 1;
      if (!allowsFirstSixOnThisRoll(context.rollerMatchSixCount, nextRollNumber)) {
        return false;
      }
      // Bots: never back-to-back sixes.
      return consecutiveSixCount <= 0;
    }
    // Humans: standard triple-six rule only (block after two consecutive sixes).
    return consecutiveSixCount < 2;
  }

  private static Integer rollWithKillFavor(
      boolean allowSix,
      List<BotPlayerView> players,
      int playerIndex,
      int killFavorPercent,
      Random random) {
    List<Integer> killDice = new ArrayList<>();
    for (int face : findKillDiceValues(players, playerIndex)) {
      if (allowSix || face != 6) {
        killDice.add(face);
      }
    }
    if (!killDice.isEmpty() && random.nextInt(100) < killFavorPercent) {
      return killDice.get(random.nextInt(killDice.size()));
    }
    return null;
  }

  private static int rollWeightedSixOrLow(
      boolean allowSix,
      double baseSixProbability,
      DiceRollContext context,
      boolean isBot,
      Random random) {
    if (!allowSix) {
      return random.nextInt(5) + 1;
    }

    int nextRollNumber = context.rollerMatchDiceRollCount + 1;
    double balanceNudge =
        computeSixProbabilityNudge(
            isBot, context.botMatchSixRolls, context.playerMatchSixRolls, random);
    double windowNudge =
        firstSixWindowNudge(context.rollerMatchSixCount, nextRollNumber, random);
    double sixProbability =
        Math.min(
            MAX_SIX_PROBABILITY,
            Math.max(0.0, baseSixProbability + balanceNudge + windowNudge));

    if (random.nextDouble() < sixProbability) {
      return 6;
    }
    return random.nextInt(5) + 1;
  }

  /**
   * Roll dice for a real player with lower kill favor than bots. Uses standard triple-six rule.
   */
  public static int rollUserDice(
      int consecutiveSixCount,
      List<BotPlayerView> players,
      int playerIndex,
      int killFavorPercent,
      DiceRollContext context,
      Random random) {
    Random rng = random != null ? random : ThreadLocalRandom.current();
    DiceRollContext ctx = context != null ? context : DiceRollContext.DEFAULT;
    boolean allowSix = resolveAllowSix(consecutiveSixCount, false, ctx);
    Integer favored =
        rollWithKillFavor(allowSix, players, playerIndex, killFavorPercent, rng);
    if (favored != null) {
      return favored;
    }

    double baseSixProbability = consecutiveSixCount >= 2 ? 0.0 : 1.0 / 6.0;
    return rollWeightedSixOrLow(allowSix, baseSixProbability, ctx, false, rng);
  }

  /**
   * Roll dice for a bot with kill favoritism and boosted sixes. Never returns 6 when
   * {@code consecutiveSixCount} >= 1 (no back-to-back sixes).
   */
  public static BotDiceDecision rollBotDice(
      int consecutiveSixCount,
      List<BotPlayerView> players,
      int playerIndex,
      BotDiceSettings settings,
      DiceRollContext context,
      KillStalkPlan stalkPlan,
      Random random) {
    Random rng = random != null ? random : ThreadLocalRandom.current();
    BotDiceSettings activeSettings =
        settings != null ? settings : BotDiceSettings.DEFAULT;
    DiceRollContext ctx = context != null ? context : DiceRollContext.DEFAULT;
    boolean allowSix = resolveAllowSix(consecutiveSixCount, true, ctx);

    if (stalkPlan == null) {
      BotDiceDecision finishing =
          rollWithHomeFinishFavor(
              allowSix, players, playerIndex, BOT_HOME_FINISH_FAVOR_PERCENT, rng);
      if (finishing != null) {
        return finishing;
      }
    }

    BotDiceDecision stalked =
        BotKillStalk.resolveStalkDice(
            allowSix,
            players,
            playerIndex,
            activeSettings.botKillFavorFor(players.size()),
            stalkPlan,
            rng);
    if (stalked != null) {
      return stalked;
    }

    Integer botFavored = rollWithBotVsBotKillFavor(allowSix, players, playerIndex, rng);
    if (botFavored != null) {
      return new BotDiceDecision(botFavored);
    }

    double baseSixProbability =
        (1.0 / 6.0) * (1.0 + activeSettings.botSixBoostPercent / 100.0);
    return new BotDiceDecision(
        rollWeightedSixOrLow(allowSix, baseSixProbability, ctx, true, rng));
  }

  /** Convenience overload using {@link GameSnapshot} and per-seat roll counters. */
  public static BotDiceDecision rollBotDice(
      GameSnapshot snap,
      int seat,
      int consecutiveSixCount,
      int[] matchDiceRollCounts,
      int[] matchSixCounts,
      KillStalkPlan stalkPlan) {
    List<BotPlayerView> players = buildPlayers(snap, matchDiceRollCounts, matchSixCounts);
    DiceRollContext context = buildDiceRollContext(players, seat);
    return rollBotDice(
        consecutiveSixCount,
        players,
        seat,
        BotDiceSettings.DEFAULT,
        context,
        stalkPlan,
        ThreadLocalRandom.current());
  }
}
