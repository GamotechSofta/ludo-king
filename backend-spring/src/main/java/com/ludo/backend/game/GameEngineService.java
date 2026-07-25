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
 *   <li>Multi-winner rankings continue until ≤1 unfinished</li>
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
  private static final int MAX_CONSECUTIVE_SIXES = 3;

  private final ConcurrentHashMap<String, MatchRuntime> matches = new ConcurrentHashMap<>();
  private final SecureRandom secureRandom = new SecureRandom();

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
    final int[] ranking;
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
      this.ranking = new int[maxPlayers];
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
      List<String> colorKeys = new ArrayList<>(snap.getTokenPositions().keySet());
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
    if (snap.getStandings() != null) {
      int maxRank = 0;
      for (int i = 0; i < rt.maxPlayers && i < snap.getStandings().size(); i++) {
        rt.ranking[i] = snap.getStandings().get(i);
        maxRank = Math.max(maxRank, rt.ranking[i]);
      }
      rt.nextRank = maxRank + 1;
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
      return rollInternal(rt, seat);
    } finally {
      rt.lock.unlock();
    }
  }

  public GameSnapshot rollDiceAsSeat(String roomId, int seat) {
    MatchRuntime rt = require(roomId);
    rt.lock.lock();
    try {
      return rollInternal(rt, seat);
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
      clearDice(rt);
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
      clearDice(rt);
      nextTurn(rt);
      recordAction(rt, "TIMEOUT", seat, null, null);
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

  private GameSnapshot rollInternal(MatchRuntime rt, int seat) {
    assertActive(rt);
    if (rt.finished[seat]) {
      // All tokens home already — no-op / pass
      nextTurn(rt);
      return snapshot(rt);
    }
    if (seat != rt.currentSeat) {
      throw new IllegalStateException("Not your turn");
    }
    if (!PHASE_ROLL.equals(rt.phase)) {
      throw new IllegalStateException("Cannot roll now — state is not AWAITING_ROLL");
    }

    // Cryptographically sound RNG; client never supplies this value
    int value = secureRandom.nextInt(6) + 1;
    rt.lastDice = value;
    rt.diceList.clear();
    rt.diceList.add(value);

    if (value == 6) {
      rt.consecutiveSixes += 1;
    } else {
      rt.consecutiveSixes = 0;
    }

    // Third consecutive six: voided — no move, turn passes
    if (rt.consecutiveSixes >= MAX_CONSECUTIVE_SIXES) {
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

    // Extra turn: (1) six was used for a move, or (2) capture bonus (separate from six-streak)
    boolean extraFromSix = usedSix && rt.consecutiveSixes < MAX_CONSECUTIVE_SIXES;
    boolean extraFromCapture = captured;
    if (extraFromSix || extraFromCapture) {
      if (!usedSix) {
        // Capture after non-six: streak already 0; keep it
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

  private void checkFinished(MatchRuntime rt, int seat) {
    for (int t = 0; t < 4; t++) {
      if (!isHome(rt.tokens[seat][t])) {
        return;
      }
    }
    if (!rt.finished[seat]) {
      rt.finished[seat] = true;
      rt.ranking[seat] = rt.nextRank++;
    }
    int unfinished = 0;
    int last = -1;
    for (int i = 0; i < rt.maxPlayers; i++) {
      if (!rt.finished[i]) {
        unfinished++;
        last = i;
      }
    }
    // Continue for rankings until ≤1 unfinished, then seal last place
    if (unfinished <= 1) {
      if (last >= 0 && !rt.finished[last]) {
        rt.finished[last] = true;
        rt.ranking[last] = rt.nextRank++;
      }
      rt.phase = PHASE_FINISHED;
    }
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
    for (int i = 1; i <= rt.maxPlayers; i++) {
      int seat = (rt.currentSeat + i) % rt.maxPlayers;
      if (!rt.finished[seat]) {
        rt.currentSeat = seat;
        rt.phase = PHASE_ROLL;
        clearDice(rt);
        rt.consecutiveSixes = 0;
        rt.turnStartedAt = Instant.now();
        return;
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
    snap.setBonusRoll(PHASE_ROLL.equals(rt.phase) && rt.consecutiveSixes > 0);
    snap.setFinished(Arrays.copyOf(rt.finished, rt.finished.length));
    snap.setIsBot(Arrays.copyOf(rt.isBot, rt.isBot.length));
    snap.setUserIds(Arrays.asList(rt.userIds.clone()));
    snap.setUsernames(Arrays.asList(rt.usernames.clone()));

    Map<String, List<Integer>> positions = new LinkedHashMap<>();
    for (int s = 0; s < rt.maxPlayers; s++) {
      List<Integer> list = new ArrayList<>(4);
      for (int t = 0; t < 4; t++) {
        list.add(rt.tokens[s][t]);
      }
      positions.put(rt.colors[s].name(), list);
    }
    snap.setTokenPositions(positions);

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
      if (rt.finished[s] && rt.ranking[s] == 1) {
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
