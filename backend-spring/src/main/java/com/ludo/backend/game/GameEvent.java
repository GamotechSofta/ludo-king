package com.ludo.backend.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact ordered multiplayer event. Clients apply by actionSeq.
 * Full {@link #state} is included for STATE/reconnect and as authority payload.
 */
public class GameEvent {

  public static final String ROLL = "ROLL";
  public static final String MOVE = "MOVE";
  public static final String TURN_CHANGE = "TURN_CHANGE";
  public static final String PASS = "PASS";
  public static final String TIMEOUT = "TIMEOUT";
  public static final String ELIMINATED = "ELIMINATED";
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
  private Map<String, List<Integer>> tokenPositions = new LinkedHashMap<>();
  private List<String> seatColors = new ArrayList<>();
  private List<Integer> legalTokenIndexes = new ArrayList<>();
  private List<Map<String, Integer>> legalMoves = new ArrayList<>();
  private boolean[] finished;
  private Integer winnerSeat;
  private String lastActionType;

  /** Full authority snapshot (always present so reconnect/old clients stay correct). */
  private GameSnapshot state;

  public static GameEvent fromSnapshot(String type, GameSnapshot snap) {
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
    e.tokenPositions = snap.getTokenPositions();
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
    if (e.dice == null || e.dice == 0) {
      e.dice = snap.getLastActionDice();
    }
    e.state = snap;
    return e;
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
      case "FORFEIT" -> FINISHED;
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

  public GameSnapshot getState() {
    return state;
  }

  public void setState(GameSnapshot state) {
    this.state = state;
  }
}
