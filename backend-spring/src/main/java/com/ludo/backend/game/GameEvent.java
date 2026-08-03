package com.ludo.backend.game;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Compact ordered multiplayer event. Clients apply by actionSeq.
 * Full {@link #state} is included for STATE/reconnect/finished; routine
 * ROLL/MOVE events carry top-level fields only (no nested snapshot).
 */
public class GameEvent {

  public static final String ROLL = "ROLL";
  public static final String MOVE = "MOVE";
  public static final String TURN_CHANGE = "TURN_CHANGE";
  public static final String PASS = "PASS";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String ELIMINATED = "ELIMINATED";
  public static final String FORFEIT = "FORFEIT";
  public static final String FINISHED = "FINISHED";
  public static final String STATE = "STATE";
  public static final String PLAYER_JOIN = "PLAYER_JOIN";
  public static final String PLAYER_LEFT = "PLAYER_LEFT";

  private String type;
  private long actionSeq;
  private String roomId;
  private long ts = System.currentTimeMillis();

  private Integer seat;
  private Integer tokenIndex;
  private Integer dice;
  private Integer from;
  private Integer to;
  private String phase;
  private Integer currentSeatIndex;
  private List<Integer> diceList = new ArrayList<>();
  private Instant turnStartedAt;
  private Integer turnSecondsRemaining;
  private Integer consecutiveSixes;
  /** Miss chances used this match (match-total — never reset on successful play). */
  private List<Integer> consecutiveTimeouts;
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private Map<String, List<Integer>> tokenPositions;
  private List<String> seatColors = new ArrayList<>();
  private List<Integer> legalTokenIndexes = new ArrayList<>();
  private List<Map<String, Integer>> legalMoves = new ArrayList<>();
  private boolean[] finished;
  private Integer winnerSeat;
  private String lastActionType;
  private boolean[] isBot;
  private boolean[] eliminated;
  private List<String> userIds;
  private List<String> usernames;

  /** Full authority snapshot — only on STATE / finish / forced resync. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private GameSnapshot state;

  public static GameEvent fromSnapshot(String type, GameSnapshot snap) {
    return fromSnapshot(type, snap, shouldEmbedFullState(type));
  }

  public static GameEvent fromSnapshot(String type, GameSnapshot snap, boolean includeFullState) {
    GameEvent e = new GameEvent();
    e.type = type;
    e.roomId = snap.getRoomId();
    e.actionSeq = snap.getActionSeq();
    e.phase = snap.getPhase();
    e.currentSeatIndex = snap.getCurrentSeatIndex();
    e.diceList = snap.getDiceList() != null ? new ArrayList<>(snap.getDiceList()) : new ArrayList<>();
    e.dice = snap.getDiceValue();
    e.turnStartedAt = snap.getTurnStartedAt();
    e.turnSecondsRemaining = snap.getTurnSecondsRemaining();
    e.consecutiveSixes = snap.getConsecutiveSixes();
    e.consecutiveTimeouts =
        snap.getConsecutiveTimeouts() != null
            ? new ArrayList<>(snap.getConsecutiveTimeouts())
            : new ArrayList<>();
    e.seatColors = snap.getSeatColors();
    e.legalTokenIndexes = snap.getLegalTokenIndexes();
    e.legalMoves = snap.getLegalMoves();
    e.finished = snap.getFinished();
    e.winnerSeat = snap.getWinnerSeat();
    e.lastActionType = snap.getLastActionType();
    e.seat = snap.getLastActionSeat();
    e.tokenIndex = snap.getLastActionTokenIndex();
    e.from = snap.getLastActionFrom();
    e.to = snap.getLastActionTo();
    e.isBot = snap.getIsBot();
    e.eliminated = snap.getEliminated();
    e.userIds = snap.getUserIds();
    e.usernames = snap.getUsernames();
    if (e.dice == null || e.dice == 0) {
      e.dice = snap.getLastActionDice();
    }
    // ROLL/PASS board does not change — omit positions; client merges prior board.
    if (!omitTokenPositions(type)) {
      e.tokenPositions = snap.getTokenPositions();
    }
    if (includeFullState) {
      e.state = snap;
    }
    return e;
  }

  /** Embed full snapshot for join/resync/terminal events only. */
  public static boolean shouldEmbedFullState(String type) {
    if (type == null) {
      return true;
    }
    return switch (type) {
      case STATE, FINISHED, FORFEIT, ELIMINATED, PLAYER_JOIN, PLAYER_LEFT -> true;
      default -> false;
    };
  }

  private static boolean omitTokenPositions(String type) {
    return ROLL.equals(type) || PASS.equals(type) || TURN_CHANGE.equals(type);
  }

  public static String typeFor(GameSnapshot snap) {
    if (GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return FINISHED;
    }
    String t = snap.getLastActionType();
    if (t == null) {
      return STATE;
    }
    return switch (t) {
      case "ROLL" -> ROLL;
      case "MOVE" -> MOVE;
      case "PASS" -> PASS;
      case "TIMEOUT" -> TIMEOUT;
      case "ELIMINATED" -> ELIMINATED;
      case "FORFEIT" -> GameEngineService.PHASE_FINISHED.equals(snap.getPhase())
          ? FINISHED
          : FORFEIT;
      default -> STATE;
    };
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public long getActionSeq() {
    return actionSeq;
  }

  public void setActionSeq(long actionSeq) {
    this.actionSeq = actionSeq;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public long getTs() {
    return ts;
  }

  public void setTs(long ts) {
    this.ts = ts;
  }

  public Integer getSeat() {
    return seat;
  }

  public void setSeat(Integer seat) {
    this.seat = seat;
  }

  public Integer getTokenIndex() {
    return tokenIndex;
  }

  public void setTokenIndex(Integer tokenIndex) {
    this.tokenIndex = tokenIndex;
  }

  public Integer getDice() {
    return dice;
  }

  public void setDice(Integer dice) {
    this.dice = dice;
  }

  public Integer getFrom() {
    return from;
  }

  public void setFrom(Integer from) {
    this.from = from;
  }

  public Integer getTo() {
    return to;
  }

  public void setTo(Integer to) {
    this.to = to;
  }

  public String getPhase() {
    return phase;
  }

  public void setPhase(String phase) {
    this.phase = phase;
  }

  public Integer getCurrentSeatIndex() {
    return currentSeatIndex;
  }

  public void setCurrentSeatIndex(Integer currentSeatIndex) {
    this.currentSeatIndex = currentSeatIndex;
  }

  public List<Integer> getDiceList() {
    return diceList;
  }

  public void setDiceList(List<Integer> diceList) {
    this.diceList = diceList;
  }

  public Instant getTurnStartedAt() {
    return turnStartedAt;
  }

  public void setTurnStartedAt(Instant turnStartedAt) {
    this.turnStartedAt = turnStartedAt;
  }

  public Integer getTurnSecondsRemaining() {
    return turnSecondsRemaining;
  }

  public void setTurnSecondsRemaining(Integer turnSecondsRemaining) {
    this.turnSecondsRemaining = turnSecondsRemaining;
  }

  public Integer getConsecutiveSixes() {
    return consecutiveSixes;
  }

  public void setConsecutiveSixes(Integer consecutiveSixes) {
    this.consecutiveSixes = consecutiveSixes;
  }

  public List<Integer> getConsecutiveTimeouts() {
    return consecutiveTimeouts;
  }

  public void setConsecutiveTimeouts(List<Integer> consecutiveTimeouts) {
    this.consecutiveTimeouts = consecutiveTimeouts;
  }

  public Map<String, List<Integer>> getTokenPositions() {
    return tokenPositions;
  }

  public void setTokenPositions(Map<String, List<Integer>> tokenPositions) {
    this.tokenPositions = tokenPositions;
  }

  public List<String> getSeatColors() {
    return seatColors;
  }

  public void setSeatColors(List<String> seatColors) {
    this.seatColors = seatColors;
  }

  public List<Integer> getLegalTokenIndexes() {
    return legalTokenIndexes;
  }

  public void setLegalTokenIndexes(List<Integer> legalTokenIndexes) {
    this.legalTokenIndexes = legalTokenIndexes;
  }

  public List<Map<String, Integer>> getLegalMoves() {
    return legalMoves;
  }

  public void setLegalMoves(List<Map<String, Integer>> legalMoves) {
    this.legalMoves = legalMoves;
  }

  public boolean[] getFinished() {
    return finished;
  }

  public void setFinished(boolean[] finished) {
    this.finished = finished;
  }

  public Integer getWinnerSeat() {
    return winnerSeat;
  }

  public void setWinnerSeat(Integer winnerSeat) {
    this.winnerSeat = winnerSeat;
  }

  public String getLastActionType() {
    return lastActionType;
  }

  public void setLastActionType(String lastActionType) {
    this.lastActionType = lastActionType;
  }

  public boolean[] getIsBot() {
    return isBot;
  }

  public void setIsBot(boolean[] isBot) {
    this.isBot = isBot;
  }

  public boolean[] getEliminated() {
    return eliminated;
  }

  public void setEliminated(boolean[] eliminated) {
    this.eliminated = eliminated;
  }

  public List<String> getUserIds() {
    return userIds;
  }

  public void setUserIds(List<String> userIds) {
    this.userIds = userIds;
  }

  public List<String> getUsernames() {
    return usernames;
  }

  public void setUsernames(List<String> usernames) {
    this.usernames = usernames;
  }

  public GameSnapshot getState() {
    return state;
  }

  public void setState(GameSnapshot state) {
    this.state = state;
  }
}
