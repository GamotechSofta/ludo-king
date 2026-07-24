package com.ludo.backend.game;

import static com.ludo.backend.game.BoardConstants.EXIT_LEN;
import static com.ludo.backend.game.BoardConstants.HOME;
import static com.ludo.backend.game.BoardConstants.HOME_STEPS;
import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.SAFE_AREAS;
import static com.ludo.backend.game.BoardConstants.TOTAL_TILES;
import static com.ludo.backend.game.BoardConstants.exitIndex;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isMain;
import static com.ludo.backend.game.BoardConstants.toExit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class GameEngineService {

  public static final String PHASE_ROLL = "WAITING_ROLL";
  public static final String PHASE_MOVE = "WAITING_MOVE";
  public static final String PHASE_FINISHED = "FINISHED";
  private static final int MAX_DICE = 3;

  private final ConcurrentHashMap<String, MatchRuntime> matches = new ConcurrentHashMap<>();

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
    final int[][] tokens; // [seat][token] encoded positions
    final boolean[] finished;
    final int[] ranking;
    final List<Integer> diceList = new ArrayList<>();
    final ReentrantLock lock = new ReentrantLock();
    int currentSeat;
    int lastDice;
    String phase = PHASE_ROLL;
    Instant turnStartedAt = Instant.now();
    int nextRank = 1;

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
      for (int i = 0; i < maxPlayers; i++) {
        SeatInfo s = seats.get(i);
        colors[i] = s.color;
        userIds[i] = s.userId;
        usernames[i] = s.username;
        isBot[i] = s.bot;
        Arrays.fill(tokens[i], JAIL);
      }
    }
  }

  public GameSnapshot createMatch(String roomId, List<SeatInfo> seats) {
    MatchRuntime rt = new MatchRuntime(roomId, seats);
    matches.put(roomId, rt);
    return snapshot(rt);
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

  public boolean hasMatch(String roomId) {
    return matches.containsKey(roomId);
  }

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

  private GameSnapshot rollInternal(MatchRuntime rt, int seat) {
    assertActive(rt);
    if (rt.finished[seat]) {
      throw new IllegalStateException("Player already finished");
    }
    if (seat != rt.currentSeat) {
      throw new IllegalStateException("Not your turn");
    }
    if (!PHASE_ROLL.equals(rt.phase)) {
      throw new IllegalStateException("Cannot roll now");
    }

    int value = ThreadLocalRandom.current().nextInt(1, 7);
    rt.lastDice = value;
    rt.diceList.add(value);

    if (rt.diceList.size() == MAX_DICE && allSame(rt.diceList)) {
      rt.diceList.clear();
      rt.lastDice = 0;
      nextTurn(rt);
      return snapshot(rt);
    }

    boolean six = value == 6;
    if (six && rt.diceList.size() < MAX_DICE) {
      rt.phase = PHASE_ROLL;
      rt.turnStartedAt = Instant.now();
      return snapshot(rt);
    }

    List<int[]> moves = computeLegalMoves(rt, seat);
    if (moves.isEmpty()) {
      rt.diceList.clear();
      rt.lastDice = 0;
      nextTurn(rt);
    } else {
      rt.phase = PHASE_MOVE;
      rt.turnStartedAt = Instant.now();
    }
    return snapshot(rt);
  }

  private GameSnapshot moveInternal(MatchRuntime rt, int seat, int tokenIndex, int diceIndex) {
    assertActive(rt);
    if (seat != rt.currentSeat) {
      throw new IllegalStateException("Not your turn");
    }
    if (!PHASE_MOVE.equals(rt.phase) && !(PHASE_ROLL.equals(rt.phase) && !rt.diceList.isEmpty())) {
      // allow move only in MOVE phase (sixes finish rolling first)
    }
    if (!PHASE_MOVE.equals(rt.phase)) {
      throw new IllegalStateException("Cannot move now");
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

    int from = rt.tokens[seat][tokenIndex];
    int to = applySteps(rt.colors[seat], from, dice);
    rt.tokens[seat][tokenIndex] = to;

    boolean captured = resolveCapture(rt, seat, tokenIndex, to);
    boolean reachedHome = isHome(to);
    if (reachedHome) {
      checkFinished(rt, seat);
    }

    rt.diceList.remove(diceIndex);
    rt.lastDice = 0;

    boolean bonus = captured || reachedHome;
    if (rt.phase.equals(PHASE_FINISHED)) {
      return snapshot(rt);
    }

    if (bonus) {
      rt.phase = PHASE_ROLL;
      rt.turnStartedAt = Instant.now();
      return snapshot(rt);
    }

    if (!rt.diceList.isEmpty()) {
      List<int[]> moves = computeLegalMoves(rt, seat);
      if (moves.isEmpty()) {
        rt.diceList.clear();
        nextTurn(rt);
      } else {
        rt.phase = PHASE_MOVE;
        rt.turnStartedAt = Instant.now();
      }
      return snapshot(rt);
    }

    nextTurn(rt);
    return snapshot(rt);
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
    // blockade check already prevents landing on 2+; capture singles
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
        rt.diceList.clear();
        rt.lastDice = 0;
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
      return dice == 6;
    }
    int remaining = remainingDistance(rt.colors[seat], pos);
    if (dice > remaining) {
      return false;
    }
    int dest = applySteps(rt.colors[seat], pos, dice);
    if (isMain(dest) && isBlocked(rt, seat, dest)) {
      return false;
    }
    return true;
  }

  private boolean isBlocked(MatchRuntime rt, int moverSeat, int landPos) {
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
    return counts.values().stream().anyMatch(c -> c >= 2);
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

  private boolean allSame(List<Integer> dice) {
    int first = dice.get(0);
    return dice.stream().allMatch(d -> d == first);
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
    return snap;
  }
}
