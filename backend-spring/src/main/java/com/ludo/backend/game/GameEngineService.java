package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.MAX_STACK;
import static com.ludo.backend.game.BoardConstants.SAFE_AREAS;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.exitIndex;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.isSafe;
import static com.ludo.backend.game.BoardConstants.toExit;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

/**
 * Server-authoritative Ludo engine.
 *
 * <p>Defaults locked from product spec:
 * <ul>
 *   <li>Extra turn on 6 only if a move was executed with that 6</li>
 *   <li>No legal moves after any roll (including 6) → pass immediately</li>
 *   <li>Third consecutive six voided (no move) → pass</li>
 *   <li>Capture grants bonus roll; does not count toward three-six limit</li>
 *   <li>Home finish does NOT grant bonus roll</li>
 *   <li>Own stack max 2 (block); third token cannot join</li>
 *   <li>Opponent blocks: can pass through; cannot land on / capture</li>
 *   <li>AFK timeout 20s: turn is passed to the next player</li>
 *   <li>3 turn timeouts (lifetime) → AFK eliminated (skipped, tokens removed)</li>
 *   <li>First player with all 4 tokens home wins (rank 1); all others LOST (rank 0)</li>
 *   <li>Team mode: not implemented</li>
 * </ul>
 */
@Service
public class GameEngineService {

  /** Spec name: AWAITING_ROLL */
  public static final String PHASE_ROLL = "AWAITING_ROLL";
  /** Spec name: AWAITING_MOVE */
  public static final String PHASE_MOVE = "AWAITING_MOVE";
  public static final String PHASE_FINISHED = "FINISHED";

  public static final int TURN_TIMEOUT_SECONDS = 20;
  public static final int MAX_CONSECUTIVE_TIMEOUTS = 3;
  /** Only rank assigned besides LOST (0). */
  public static final int RANK_WIN = 1;
  public static final int RANK_LOST = 0;
  private static final int MAX_CONSECUTIVE_SIXES = 3;

  private final ConcurrentHashMap<String, MatchRuntime> matches = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();
  private final HumanJailDiceAssist humanJailDiceAssist;
  private final HumanJailExitAssist humanJailExitAssist;

  public GameEngineService(
      HumanJailDiceAssist humanJailDiceAssist,
      HumanJailExitAssist humanJailExitAssist
  ) {
    this.humanJailDiceAssist = humanJailDiceAssist;
    this.humanJailExitAssist = humanJailExitAssist;
  }

  public static class SeatInfo {
    public final String userId;
    public final String username;
    public final LudoColor color;
    public final boolean bot;

    public SeatInfo(String userId, String username, LudoColor color, boolean bot) {
      this.userId = userId;
      this.username = username;
      this.color = color;
      this.bot = bot;
    }
  }

  static final class MatchRuntime {
    final String roomId;
    final int maxPlayers;
    final LudoColor[] colors;
    final String[] userIds;
    final String[] usernames;
    final boolean[] isBot;
    final int[][] tokens;
    final boolean[] finished;
    final boolean[] eliminated;
    final int[] ranking;
    final int[] consecutiveTimeouts;
    /** Human jail assist: consecutive non-6 rolls while all four tokens were jailed. */
    final int[] jailAssistFailedRolls;
    final List<Integer> diceList = new ArrayList<>();
    final ReentrantLock lock = new ReentrantLock();
    int currentSeat;
    int lastDice;
    int consecutiveSixes;
    boolean lastRollWasSix;
    String phase = PHASE_ROLL;
    Instant turnStartedAt = Instant.now();
    int nextRank = 1;
    String lastActionType;
    Integer lastActionSeat;
    Integer lastActionTokenIndex;
    Integer lastActionDice;
    Integer lastActionFrom;
    Integer lastActionTo;
    long actionSeq;

    MatchRuntime(String roomId, List<SeatInfo> seats) {
      this.roomId = roomId;
      this.maxPlayers = seats.size();
      this.colors = new LudoColor[maxPlayers];
      this.userIds = new String[maxPlayers];
      this.usernames = new String[maxPlayers];
      this.isBot = new boolean[maxPlayers];
      this.tokens = new int[maxPlayers][4];
      this.finished = new boolean[maxPlayers];
      this.eliminated = new boolean[maxPlayers];
      this.ranking = new int[maxPlayers];
      this.consecutiveTimeouts = new int[maxPlayers];
      this.jailAssistFailedRolls = new int[maxPlayers];
      this.lastActionType = null;
      this.lastActionSeat = null;
      this.lastActionTokenIndex = null;
      this.lastActionDice = null;
      this.lastActionFrom = null;
      this.lastActionTo = null;
      this.actionSeq = 0;
      for (int i = 0; i < maxPlayers; i++) {
        SeatInfo s = seats.get(i);
        colors[i] = s.color;
        userIds[i] = s.userId;
        usernames[i] = s.username;
        isBot[i] = s.bot;
        Arrays.fill(tokens[i], JAIL);
      }
    }

    /** Rebuild runtime from a persisted snapshot (reconnect / cross-instance). */
    static MatchRuntime fromSnapshot(GameSnapshot snap) {
      List<String> colorKeys =
          snap.getSeatColors() != null && !snap.getSeatColors().isEmpty()
              ? new ArrayList<>(snap.getSeatColors())
              : new ArrayList<>(snap.getTokenPositions().keySet());
      int n = colorKeys.size();
      if (snap.getUserIds() != null && snap.getUserIds().size() > 0) {
        n = snap.getUserIds().size();
      }
      List<SeatInfo> seats = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
        String colorName = i < colorKeys.size()
            ? colorKeys.get(i)
            : (snap.getCurrentColor() != null ? snap.getCurrentColor() : "RED");
        String uid = snap.getUserIds() != null && i < snap.getUserIds().size()
            ? snap.getUserIds().get(i) : ("seat-" + i);
        String uname = snap.getUsernames() != null && i < snap.getUsernames().size()
            ? snap.getUsernames().get(i) : ("Player " + (i + 1));
        boolean bot = snap.getIsBot() != null && i < snap.getIsBot().length && snap.getIsBot()[i];
        seats.add(new SeatInfo(uid, uname, LudoColor.valueOf(colorName), bot));
      }
      MatchRuntime rt = new MatchRuntime(snap.getRoomId(), seats);
      applySnapshotFields(rt, snap);
      return rt;
    }
  }

  static void applySnapshotFields(MatchRuntime rt, GameSnapshot snap) {
    if (snap.getPhase() != null) {
      rt.phase = snap.getPhase();
    }
    rt.currentSeat = snap.getCurrentSeatIndex();
    rt.lastDice = snap.getDiceValue();
    rt.diceList.clear();
    if (snap.getDiceList() != null) {
      rt.diceList.addAll(snap.getDiceList());
    }
    rt.consecutiveSixes = snap.getConsecutiveSixes();
    rt.lastRollWasSix = snap.isBonusRoll() || (rt.lastDice == 6 && PHASE_MOVE.equals(rt.phase));
    if (snap.getTurnStartedAt() != null) {
      rt.turnStartedAt = snap.getTurnStartedAt();
    }
    rt.lastActionType = snap.getLastActionType();
    rt.lastActionSeat = snap.getLastActionSeat();
    rt.lastActionTokenIndex = snap.getLastActionTokenIndex();
    rt.lastActionDice = snap.getLastActionDice();
    rt.lastActionFrom = snap.getLastActionFrom();
    rt.lastActionTo = snap.getLastActionTo();
    rt.actionSeq = snap.getActionSeq();

    if (snap.getFinished() != null) {
      System.arraycopy(
          snap.getFinished(), 0, rt.finished, 0,
          Math.min(snap.getFinished().length, rt.finished.length));
    }
    if (snap.getEliminated() != null) {
      System.arraycopy(
          snap.getEliminated(), 0, rt.eliminated, 0,
          Math.min(snap.getEliminated().length, rt.eliminated.length));
    }
    if (snap.getStandings() != null) {
      int maxRank = 0;
      for (int i = 0; i < rt.maxPlayers && i < snap.getStandings().size(); i++) {
        rt.ranking[i] = snap.getStandings().get(i);
        maxRank = Math.max(maxRank, rt.ranking[i]);
      }
      rt.nextRank = maxRank + 1;
    }
    if (snap.getConsecutiveTimeouts() != null) {
      for (int i = 0; i < rt.maxPlayers && i < snap.getConsecutiveTimeouts().size(); i++) {
        rt.consecutiveTimeouts[i] = snap.getConsecutiveTimeouts().get(i);
      }
    }

    for (int s = 0; s < rt.maxPlayers; s++) {
      List<Integer> pos = snap.getTokenPositions().get(rt.colors[s].name());
      if (pos == null) {
        continue;
      }
      for (int t = 0; t < 4 && t < pos.size(); t++) {
        Integer v = pos.get(t);
        rt.tokens[s][t] = v != null ? v : JAIL;
      }
    }
  }

  /**
   * Create a brand-new match. Refuses to overwrite an existing live session.
   * Mid-game recovery must use {@link #restoreFromSnapshot(GameSnapshot)}.
   */
  public GameSnapshot createMatch(String roomId, List<SeatInfo> seats) {
    MatchRuntime existing = matches.get(roomId);
    if (existing != null) {
      existing.lock.lock();
      try {
        if (!PHASE_FINISHED.equals(existing.phase)) {
          // One live session per room — never recreate mid-game
          return snapshot(existing);
        }
      } finally {
        existing.lock.unlock();
      }
      matches.remove(roomId, existing);
    }
    MatchRuntime rt = new MatchRuntime(roomId, seats);
    MatchRuntime raced = matches.putIfAbsent(roomId, rt);
    if (raced != null) {
      return getSnapshot(roomId);
    }
    return snapshot(rt);
  }

  /**
   * Restore or upgrade the single authoritative MatchRuntime for a room from a
   * persisted/cached snapshot. Never resets tokens to jail when a newer or equal
   * local session already exists.
   */
  public GameSnapshot restoreFromSnapshot(GameSnapshot snap) {
    if (snap == null || snap.getRoomId() == null || snap.getRoomId().isBlank()) {
      throw new IllegalArgumentException("Invalid snapshot");
    }
    if (snap.getTokenPositions() == null || snap.getTokenPositions().isEmpty()) {
      throw new IllegalArgumentException("Snapshot missing token positions");
    }

    String roomId = snap.getRoomId();
    MatchRuntime existing = matches.get(roomId);
    if (existing != null) {
      existing.lock.lock();
      try {
        if (snap.getActionSeq() < existing.actionSeq) {
          return snapshot(existing);
        }
        applySnapshotFields(existing, snap);
        return snapshot(existing);
      } finally {
        existing.lock.unlock();
      }
    }

    MatchRuntime rt = MatchRuntime.fromSnapshot(snap);
    MatchRuntime raced = matches.putIfAbsent(roomId, rt);
    if (raced != null) {
      return restoreFromSnapshot(snap);
    }
    return snapshot(rt);
  }

  /** @return true if this JVM holds the live session for the room */
  public boolean hasMatch(String roomId) {
    return matches.containsKey(roomId);
  }

  public GameSnapshot getSnapshot(String roomId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      return snapshot(rt);
    } finally {
      rt.lock.unlock();
    }
  }

  /** Client sends roll intent only — server generates the die value. */
  public GameSnapshot rollDice(String roomId, String userId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      int seat = seatOfUser(rt, userId);
      if (seat < 0) {
        throw new IllegalStateException("Not a player in this room");
      }
      return rollInternal(rt, seat, null);
    } finally {
      rt.lock.unlock();
    }
  }

  public GameSnapshot rollDiceAsSeat(String roomId, int seat) {
    return rollDiceAsSeat(roomId, seat, null);
  }

  /**
   * Bot-only assist path may pass a forced value 1–6; {@code null} uses secure random.
   * Human rolls always call {@link #rollDice} which ignores forced values.
   */
  public GameSnapshot rollDiceAsSeat(String roomId, int seat, Integer forcedValue) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      return rollInternal(rt, seat, forcedValue);
    } finally {
      rt.lock.unlock();
    }
  }

  /**
   * Pre-roll legality probe for bot kill dice assist (AWAITING_ROLL only).
   */
  public boolean canBotUseDiceForAssist(String roomId, int seat, int tokenIndex, int dice) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      if (seat != rt.currentSeat || !PHASE_ROLL.equals(rt.phase) || rt.finished[seat]) {
        return false;
      }
      return canUseDice(rt, seat, tokenIndex, dice);
    } finally {
      rt.lock.unlock();
    }
  }

  public GameSnapshot moveToken(String roomId, String userId, int tokenIndex, int diceIndex) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      int seat = seatOfUser(rt, userId);
      if (seat < 0) {
        throw new IllegalStateException("Not a player in this room");
      }
      return moveInternal(rt, seat, tokenIndex, diceIndex);
    } finally {
      rt.lock.unlock();
    }
  }

  public GameSnapshot moveTokenAsSeat(String roomId, int seat, int tokenIndex, int diceIndex) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      return moveInternal(rt, seat, tokenIndex, diceIndex);
    } finally {
      rt.lock.unlock();
    }
  }

  public List<int[]> legalMoves(String roomId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      return computeLegalMoves(rt, rt.currentSeat);
    } finally {
      rt.lock.unlock();
    }
  }

  public GameSnapshot skipTurn(String roomId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      assertActive(rt);
      int seat = rt.currentSeat;
      clearDice(rt);
      recordAction(rt, "PASS", seat, null, null);
      nextTurn(rt);
      return snapshot(rt);
    } finally {
      rt.lock.unlock();
    }
  }

  /**
   * AFK / disconnect timeout (server authority): discard the current turn
   * and pass play to the next seat.
   */
  public GameSnapshot resolveTimeout(String roomId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      if (PHASE_FINISHED.equals(rt.phase)) {
        return snapshot(rt);
      }
      Instant deadline = rt.turnStartedAt.plusSeconds(TURN_TIMEOUT_SECONDS);
      if (Instant.now().isBefore(deadline)) {
        return snapshot(rt);
      }

      int seat = rt.currentSeat;
      if (rt.finished[seat]) {
        recordAction(rt, "PASS", seat, null, null);
        nextTurn(rt);
        return snapshot(rt);
      }
      clearDice(rt);
      rt.consecutiveTimeouts[seat] += 1;
      boolean eliminated = rt.consecutiveTimeouts[seat] >= MAX_CONSECUTIVE_TIMEOUTS;
      if (eliminated) {
        eliminateAfk(rt, seat);
      }
      if (eliminated) {
        recordAction(rt, "ELIMINATED", seat, null, null);
      } else {
        recordAction(rt, "TIMEOUT", seat, null, null);
      }
      nextTurn(rt);
      return snapshot(rt);
    } finally {
      rt.lock.unlock();
    }
  }

  /** @deprecated use {@link #resolveTimeout(String)} */
  public GameSnapshot skipTurnIfTimedOut(String roomId) {
    return resolveTimeout(roomId);
  }

  public java.util.Set<String> activeRoomIds() {
    return matches.keySet();
  }

  /** True when every seated player is human (no bots). */
  public static boolean isAllHumanMatch(GameSnapshot snap) {
    if (snap == null || snap.getIsBot() == null || snap.getIsBot().length == 0) {
      return false;
    }
    for (boolean bot : snap.getIsBot()) {
      if (bot) {
        return false;
      }
    }
    return true;
  }

  /** 2-player queue with two real humans — human-vs-human online rules apply. */
  public static boolean isTwoPlayerHumanMatch(GameSnapshot snap) {
    if (snap == null) {
      return false;
    }
    int seats = snap.getSeatColors() != null ? snap.getSeatColors().size() : 0;
    if (seats == 0 && snap.getUserIds() != null) {
      seats = snap.getUserIds().size();
    }
    return seats == 2 && isAllHumanMatch(snap);
  }

  static boolean isAllHumanRuntime(MatchRuntime rt) {
    for (boolean bot : rt.isBot) {
      if (bot) {
        return false;
      }
    }
    return true;
  }

  static boolean isTwoPlayerHumanRuntime(MatchRuntime rt) {
    return rt.maxPlayers == 2 && isAllHumanRuntime(rt);
  }

  /**
   * Intentional exit during a live human-only match.
   * 2P: leaver LOST, opponent WIN, match ends.
   * 4P: leaver LOST + skipped from rotation; match continues until first winner.
   */
  public GameSnapshot forfeitOnExit(String roomId, String userId) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      if (PHASE_FINISHED.equals(rt.phase)) {
        return snapshot(rt);
      }
      if (!isAllHumanRuntime(rt)) {
        throw new IllegalStateException("Forfeit only applies to human-only matches");
      }
      int seat = seatOfUser(rt, userId);
      if (seat < 0) {
        throw new IllegalStateException("Not a player in this room");
      }
      if (rt.eliminated[seat] && rt.finished[seat]) {
        return snapshot(rt);
      }
      boolean wasCurrent = rt.currentSeat == seat;
      markPlayerExited(rt, seat);
      if (wasCurrent) {
        clearDice(rt);
      }
      recordAction(rt, "FORFEIT", seat, null, null);
      if (rt.maxPlayers == 2) {
        finishMatchAfterElimination(rt, seat);
      } else if (wasCurrent) {
        nextTurn(rt);
      }
      return snapshot(rt);
    } finally {
      rt.lock.unlock();
    }
  }

  private static boolean allTokensInJail(MatchRuntime rt, int seat) {
    return HumanJailDiceAssist.allTokensInJail(rt.tokens[seat]);
  }

  private GameSnapshot rollInternal(MatchRuntime rt, int seat, Integer forcedValue) {
    assertActive(rt);
    if (rt.eliminated[seat] || rt.finished[seat]) {
      return snapshot(rt);
    }
    if (seat != rt.currentSeat) {
      throw new IllegalStateException("Not your turn");
    }
    if (!PHASE_ROLL.equals(rt.phase)) {
      throw new IllegalStateException("Cannot roll now — state is not AWAITING_ROLL");
    }
    if (!rt.diceList.isEmpty()) {
      throw new IllegalStateException("Dice already rolled this turn");
    }

    int value;
    boolean allJailedBeforeRoll = allTokensInJail(rt, seat);
    boolean humanSeat = !rt.isBot[seat];
    boolean opponentNearStart =
        humanSeat
            && allJailedBeforeRoll
            && humanJailExitAssist.isEnabled()
            && humanJailExitAssist.isOpponentNearStartingPath(
                rt.colors[seat].startTile(),
                rt.maxPlayers,
                rt.tokens,
                rt.eliminated,
                rt.finished,
                seat);
    if (forcedValue != null && forcedValue >= 1 && forcedValue <= 6) {
      value = forcedValue;
    } else if (opponentNearStart) {
      value = humanJailExitAssist.rollDice(secureRandom);
    } else if (humanSeat && allJailedBeforeRoll && humanJailDiceAssist.isEnabled()) {
      value = humanJailDiceAssist.rollDice(secureRandom, rt.jailAssistFailedRolls[seat]);
    } else {
      value = secureRandom.nextInt(6) + 1;
    }
    if (humanSeat && allJailedBeforeRoll && humanJailDiceAssist.isEnabled() && !opponentNearStart) {
      if (value == 6) {
        rt.jailAssistFailedRolls[seat] = 0;
      } else {
        rt.jailAssistFailedRolls[seat] += 1;
      }
    }
    rt.lastDice = value;
    rt.diceList.clear();
    rt.diceList.add(value);

    if (value == 6) {
      rt.consecutiveSixes += 1;
    } else {
      rt.consecutiveSixes = 0;
    }

    // Third consecutive six: voided — no move, turn passes, streak reset
    if (rt.consecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
      rt.consecutiveSixes = 0;
      clearDice(rt);
      nextTurn(rt);
      recordAction(rt, "PASS", seat, null, value);
      return snapshot(rt);
    }

    rt.lastRollWasSix = value == 6;
    List<int[]> moves = computeLegalMoves(rt, seat);

    // No legal moves (including on a 6) → pass immediately; 6 does NOT grant extra roll
    if (moves.isEmpty()) {
      clearDice(rt);
      nextTurn(rt);
      recordAction(rt, "PASS", seat, null, value);
      return snapshot(rt);
    }

    rt.phase = PHASE_MOVE;
    rt.turnStartedAt = Instant.now();
    recordAction(rt, "ROLL", seat, null, value);
    return snapshot(rt);
  }

  private GameSnapshot moveInternal(MatchRuntime rt, int seat, int tokenIndex, int diceIndex) {
    assertActive(rt);
    if (rt.eliminated[seat] || rt.finished[seat]) {
      return snapshot(rt);
    }
    if (seat != rt.currentSeat) {
      throw new IllegalStateException("Not your turn");
    }
    if (!PHASE_MOVE.equals(rt.phase)) {
      throw new IllegalStateException("Cannot move now — state is not AWAITING_MOVE");
    }
    if (diceIndex < 0 || diceIndex >= rt.diceList.size()) {
      throw new IllegalArgumentException("Invalid dice index");
    }
    if (tokenIndex < 0 || tokenIndex > 3) {
      throw new IllegalArgumentException("Invalid token");
    }

    int dice = rt.diceList.get(diceIndex);
    if (!canUseDice(rt, seat, tokenIndex, dice)) {
      throw new IllegalStateException("Illegal move");
    }

    boolean usedSix = dice == 6;
    int from = rt.tokens[seat][tokenIndex];
    int to = applySteps(rt.colors[seat], from, dice);
    rt.tokens[seat][tokenIndex] = to;

    if (isJail(from) || !allTokensInJail(rt, seat)) {
      rt.jailAssistFailedRolls[seat] = 0;
    }

    boolean captured = resolveCapture(rt, seat, tokenIndex, to);
    boolean reachedHome = isHome(to);
    if (reachedHome) {
      checkFinished(rt, seat);
    }

    clearDice(rt);
    rt.lastRollWasSix = false;
    recordAction(rt, "MOVE", seat, tokenIndex, dice, from, to);

    if (PHASE_FINISHED.equals(rt.phase)) {
      return snapshot(rt);
    }

    // Seat finished (all 4 home) → pass; no bonus
    if (rt.finished[seat]) {
      nextTurn(rt);
      return snapshot(rt);
    }

    // Extra turn: (1) six used for a move, or (2) capture bonus
    // Home finish alone does NOT grant a bonus (product rules)
    boolean extraFromSix = usedSix && rt.consecutiveSixes < MAX_CONSECUTIVE_SIXES;
    boolean extraFromCapture = captured;
    if (extraFromSix || extraFromCapture) {
      if (!usedSix) {
        // Capture after non-six: six-streak does not continue
        rt.consecutiveSixes = 0;
      }
      rt.phase = PHASE_ROLL;
      rt.turnStartedAt = Instant.now();
      return snapshot(rt);
    }

    rt.consecutiveSixes = 0;
    nextTurn(rt);
    return snapshot(rt);
  }

  private void recordAction(
      MatchRuntime rt,
      String type,
      int seat,
      Integer tokenIndex,
      Integer dice
  ) {
    recordAction(rt, type, seat, tokenIndex, dice, null, null);
  }

  private void recordAction(
      MatchRuntime rt,
      String type,
      int seat,
      Integer tokenIndex,
      Integer dice,
      Integer from,
      Integer to
  ) {
    rt.actionSeq += 1;
    rt.lastActionType = type;
    rt.lastActionSeat = seat;
    rt.lastActionTokenIndex = tokenIndex;
    rt.lastActionDice = dice;
    rt.lastActionFrom = from;
    rt.lastActionTo = to;
  }

  private void clearDice(MatchRuntime rt) {
    rt.diceList.clear();
    rt.lastDice = 0;
    rt.lastRollWasSix = false;
  }

  /** Exit / AFK removal: LOST, tokens cleared, skipped in {@link #nextTurn}. */
  private void markPlayerExited(MatchRuntime rt, int seat) {
    if (rt.finished[seat]) {
      return;
    }
    rt.finished[seat] = true;
    rt.eliminated[seat] = true;
    rt.ranking[seat] = RANK_LOST;
    Arrays.fill(rt.tokens[seat], JAIL);
  }

  /** 3 timeouts used → remove player from the match. */
  private void eliminateAfk(MatchRuntime rt, int seat) {
    if (rt.finished[seat]) {
      return;
    }
    markPlayerExited(rt, seat);

    // 2-player: one AFK elimination ends the match (opponent wins).
    if (rt.maxPlayers == 2) {
      finishMatchAfterElimination(rt, seat);
    }
  }

  /** 2P: last active seat wins; eliminated seat is LOST. */
  private void finishMatchAfterElimination(MatchRuntime rt, int eliminatedSeat) {
    rt.ranking[eliminatedSeat] = RANK_LOST;
    for (int i = 0; i < rt.maxPlayers; i++) {
      if (i == eliminatedSeat) {
        continue;
      }
      rt.finished[i] = true;
      rt.ranking[i] = RANK_WIN;
    }
    rt.phase = PHASE_FINISHED;
  }

  private void checkFinished(MatchRuntime rt, int seat) {
    for (int t = 0; t < 4; t++) {
      if (!isHome(rt.tokens[seat][t])) {
        return;
      }
    }
    finishMatchOnFirstWinner(rt, seat);
  }

  /** First seat with all tokens home wins; everyone else is LOST (no rank 2/3/4). */
  private void finishMatchOnFirstWinner(MatchRuntime rt, int winnerSeat) {
    rt.finished[winnerSeat] = true;
    rt.ranking[winnerSeat] = RANK_WIN;
    for (int i = 0; i < rt.maxPlayers; i++) {
      if (i == winnerSeat) {
        continue;
      }
      rt.finished[i] = true;
      if (rt.ranking[i] != RANK_WIN) {
        rt.ranking[i] = RANK_LOST;
      }
    }
    rt.phase = PHASE_FINISHED;
  }

  private boolean resolveCapture(MatchRuntime rt, int moverSeat, int moverToken, int landPos) {
    if (!isMain(landPos) || SAFE_AREAS.contains(landPos)) {
      return false;
    }
    boolean captured = false;
    Map<Integer, List<int[]>> bySeat = new LinkedHashMap<>();
    for (int s = 0; s < rt.maxPlayers; s++) {
      if (s == moverSeat || rt.finished[s]) {
        continue;
      }
      for (int t = 0; t < 4; t++) {
        if (rt.tokens[s][t] == landPos) {
          bySeat.computeIfAbsent(s, k -> new ArrayList<>()).add(new int[] {s, t});
        }
      }
    }
    for (List<int[]> group : bySeat.values()) {
      // Block (2+) is immune; only a single opponent token can be cut
      if (group.size() == 1) {
        int[] hit = group.get(0);
        rt.tokens[hit[0]][hit[1]] = JAIL;
        captured = true;
      }
    }
    return captured;
  }

  private void nextTurn(MatchRuntime rt) {
    if (PHASE_FINISHED.equals(rt.phase)) {
      return;
    }
    LudoColor current = rt.colors[rt.currentSeat];
    List<LudoColor> boardOrder = LudoColor.forPlayerCount(rt.maxPlayers);
    int startIdx = boardOrder.indexOf(current);
    if (startIdx < 0) {
      startIdx = 0;
    }
    // Clockwise on the board: RED→GREEN→YELLOW→BLUE (2p/3p use subset)
    for (int step = 1; step <= boardOrder.size(); step++) {
      LudoColor nextColor = boardOrder.get((startIdx + step) % boardOrder.size());
      for (int s = 0; s < rt.maxPlayers; s++) {
        if (rt.colors[s] == nextColor && !rt.finished[s]) {
          rt.currentSeat = s;
          rt.phase = PHASE_ROLL;
          clearDice(rt);
          rt.consecutiveSixes = 0;
          rt.turnStartedAt = Instant.now();
          return;
        }
      }
    }
    rt.phase = PHASE_FINISHED;
  }

  private List<int[]> computeLegalMoves(MatchRuntime rt, int seat) {
    List<int[]> moves = new ArrayList<>();
    for (int t = 0; t < 4; t++) {
      for (int d = 0; d < rt.diceList.size(); d++) {
        if (canUseDice(rt, seat, t, rt.diceList.get(d))) {
          moves.add(new int[] {t, d});
        }
      }
    }
    return moves;
  }

  private boolean canUseDice(MatchRuntime rt, int seat, int tokenIndex, int dice) {
    int pos = rt.tokens[seat][tokenIndex];
    if (isHome(pos)) {
      return false;
    }
    if (isJail(pos)) {
      if (dice != 6) {
        return false;
      }
      int start = rt.colors[seat].startTile();
      // Start is always safe: mixed/own stacking unrestricted on safe cells
      if (isSafe(start)) {
        return true;
      }
      return countOwnOnCell(rt, seat, start, -1) < MAX_STACK;
    }

    int remaining = remainingDistance(rt.colors[seat], pos);
    if (dice > remaining) {
      return false;
    }

    int dest = applySteps(rt.colors[seat], pos, dice);

    if (isHome(dest) && dice != remaining) {
      return false;
    }

    if (isMain(dest)) {
      // Safe cells: mixed occupancy OK; exempt from block / max-stack limits
      if (isSafe(dest)) {
        return true;
      }
      // Non-safe: cannot land on opponent block; own stack max MAX_STACK
      if (hasOpponentBlock(rt, seat, dest)) {
        return false;
      }
      if (countOwnOnCell(rt, seat, dest, tokenIndex) >= MAX_STACK) {
        return false;
      }
    }

    return true;
  }

  private int countOwnOnCell(MatchRuntime rt, int seat, int cell, int excludeToken) {
    if (!isMain(cell)) {
      return 0;
    }
    int count = 0;
    for (int t = 0; t < 4; t++) {
      if (t == excludeToken) {
        continue;
      }
      if (rt.tokens[seat][t] == cell) {
        count++;
      }
    }
    return count;
  }

  private boolean hasOpponentBlock(MatchRuntime rt, int moverSeat, int landPos) {
    Map<Integer, Integer> counts = new LinkedHashMap<>();
    for (int s = 0; s < rt.maxPlayers; s++) {
      if (s == moverSeat) {
        continue;
      }
      for (int t = 0; t < 4; t++) {
        if (rt.tokens[s][t] == landPos) {
          counts.merge(s, 1, Integer::sum);
        }
      }
    }
    return counts.values().stream().anyMatch(c -> c >= MAX_STACK);
  }

  private int remainingDistance(LudoColor color, int pos) {
    if (isJail(pos) || isHome(pos)) {
      return Integer.MAX_VALUE;
    }
    if (isExit(pos)) {
      return HOME_STEPS - 1 - exitIndex(pos);
    }
    int toExit = (color.exitTile() - pos + TOTAL_TILES) % TOTAL_TILES;
    return toExit + HOME_STEPS;
  }

  private int applySteps(LudoColor color, int from, int steps) {
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

  private int seatOfUser(MatchRuntime rt, String userId) {
    if (userId == null) {
      return -1;
    }
    for (int i = 0; i < rt.maxPlayers; i++) {
      if (userId.equals(rt.userIds[i])) {
        return i;
      }
    }
    return -1;
  }

  private void assertActive(MatchRuntime rt) {
    if (PHASE_FINISHED.equals(rt.phase)) {
      throw new IllegalStateException("Match finished");
    }
  }

  private MatchRuntime require(String roomId) {
    MatchRuntime rt = matches.get(roomId);
    if (rt == null) {
      throw new IllegalArgumentException("No match for room " + roomId);
    }
    return rt;
  }

  private GameSnapshot snapshot(MatchRuntime rt) {
    GameSnapshot snap = new GameSnapshot();
    snap.setRoomId(rt.roomId);
    snap.setPhase(rt.phase);
    snap.setCurrentSeatIndex(rt.currentSeat);
    snap.setCurrentColor(rt.colors[rt.currentSeat].name());
    snap.setDiceValue(rt.lastDice);
    snap.setDiceList(new ArrayList<>(rt.diceList));
    snap.setTurnStartedAt(rt.turnStartedAt);
    snap.setTurnTimeoutSeconds(TURN_TIMEOUT_SECONDS);
    long elapsed = Math.max(0, Instant.now().getEpochSecond() - rt.turnStartedAt.getEpochSecond());
    snap.setTurnSecondsRemaining(
        Math.max(0, (int) (TURN_TIMEOUT_SECONDS - elapsed))
    );
    snap.setConsecutiveSixes(rt.consecutiveSixes);
    List<Integer> timeoutStreak = new ArrayList<>(rt.maxPlayers);
    for (int s = 0; s < rt.maxPlayers; s++) {
      timeoutStreak.add(rt.consecutiveTimeouts[s]);
    }
    snap.setConsecutiveTimeouts(timeoutStreak);
    // Same seat still rolling after a MOVE = bonus (six / capture / home)
    boolean bonusAfterMove =
        PHASE_ROLL.equals(rt.phase)
            && "MOVE".equals(rt.lastActionType)
            && rt.lastActionSeat != null
            && rt.lastActionSeat == rt.currentSeat;
    snap.setBonusRoll(bonusAfterMove);
    snap.setFinished(Arrays.copyOf(rt.finished, rt.finished.length));
    snap.setEliminated(Arrays.copyOf(rt.eliminated, rt.eliminated.length));
    snap.setIsBot(Arrays.copyOf(rt.isBot, rt.isBot.length));
    snap.setUserIds(Arrays.asList(rt.userIds.clone()));
    snap.setUsernames(Arrays.asList(rt.usernames.clone()));

    Map<String, List<Integer>> positions = new LinkedHashMap<>();
    List<String> seatColors = new ArrayList<>(rt.maxPlayers);
    for (int s = 0; s < rt.maxPlayers; s++) {
      List<Integer> list = new ArrayList<>(4);
      for (int t = 0; t < 4; t++) {
        list.add(rt.tokens[s][t]);
      }
      String colorName = rt.colors[s].name();
      positions.put(colorName, list);
      seatColors.add(colorName);
    }
    snap.setTokenPositions(positions);
    snap.setSeatColors(seatColors);

    List<Integer> legalTokens = new ArrayList<>();
    List<Map<String, Integer>> legalMoves = new ArrayList<>();
    if (PHASE_MOVE.equals(rt.phase)) {
      for (int[] m : computeLegalMoves(rt, rt.currentSeat)) {
        if (!legalTokens.contains(m[0])) {
          legalTokens.add(m[0]);
        }
        Map<String, Integer> row = new LinkedHashMap<>();
        row.put("tokenIndex", m[0]);
        row.put("diceIndex", m[1]);
        legalMoves.add(row);
      }
    }
    snap.setLegalTokenIndexes(legalTokens);
    snap.setLegalMoves(legalMoves);

    List<Integer> standings = new ArrayList<>();
    Integer winner = null;
    for (int s = 0; s < rt.maxPlayers; s++) {
      if (rt.finished[s] && rt.ranking[s] == RANK_WIN) {
        winner = s;
      }
      standings.add(rt.ranking[s]);
    }
    snap.setWinnerSeat(winner);
    snap.setStandings(standings);
    snap.setLastActionType(rt.lastActionType);
    snap.setLastActionSeat(rt.lastActionSeat);
    snap.setLastActionTokenIndex(rt.lastActionTokenIndex);
    snap.setLastActionDice(rt.lastActionDice);
    snap.setLastActionFrom(rt.lastActionFrom);
    snap.setLastActionTo(rt.lastActionTo);
    snap.setActionSeq(rt.actionSeq);
    return snap;
  }
}
