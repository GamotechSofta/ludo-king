package com.ludo.backend.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GameSnapshot {
  private String roomId;
  private String phase;
  private int currentSeatIndex;
  private String currentColor;
  private int diceValue;
  private List<Integer> diceList = new ArrayList<>();
  private Map<String, List<Integer>> tokenPositions = new LinkedHashMap<>();
  /** Color name per seat index (authoritative after shuffle). */
  private List<String> seatColors = new ArrayList<>();
  private List<Integer> legalTokenIndexes = new ArrayList<>();
  private boolean bonusRoll;
  private Integer winnerSeat;
  private List<Integer> standings = new ArrayList<>();
  private Instant turnStartedAt;
  private int turnTimeoutSeconds = 20;
  private int turnSecondsRemaining = 20;
  private int consecutiveSixes;
  private List<Integer> consecutiveTimeouts = new ArrayList<>();
  private boolean[] finished;
  /** True when removed for AFK (2 consecutive timeouts). */
  private boolean[] eliminated;
  private boolean[] isBot;
  private List<String> userIds = new ArrayList<>();
  private List<String> usernames = new ArrayList<>();
  private List<Map<String, Integer>> legalMoves = new ArrayList<>();
  /** ROLL | MOVE | PASS | TIMEOUT — helps clients animate smoothly */
  private String lastActionType;
  private Integer lastActionSeat;
  private Integer lastActionTokenIndex;
  private Integer lastActionDice;
  private Integer lastActionFrom;
  private Integer lastActionTo;
  private long actionSeq;

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }

  public String getPhase() {
    return phase;
  }

  public void setPhase(String phase) {
    this.phase = phase;
  }

  public int getCurrentSeatIndex() {
    return currentSeatIndex;
  }

  public void setCurrentSeatIndex(int currentSeatIndex) {
    this.currentSeatIndex = currentSeatIndex;
  }

  public String getCurrentColor() {
    return currentColor;
  }

  public void setCurrentColor(String currentColor) {
    this.currentColor = currentColor;
  }

  public int getDiceValue() {
    return diceValue;
  }

  public void setDiceValue(int diceValue) {
    this.diceValue = diceValue;
  }

  public List<Integer> getDiceList() {
    return diceList;
  }

  public void setDiceList(List<Integer> diceList) {
    this.diceList = diceList;
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

  public boolean isBonusRoll() {
    return bonusRoll;
  }

  public void setBonusRoll(boolean bonusRoll) {
    this.bonusRoll = bonusRoll;
  }

  public Integer getWinnerSeat() {
    return winnerSeat;
  }

  public void setWinnerSeat(Integer winnerSeat) {
    this.winnerSeat = winnerSeat;
  }

  public List<Integer> getStandings() {
    return standings;
  }

  public void setStandings(List<Integer> standings) {
    this.standings = standings;
  }

  public Instant getTurnStartedAt() {
    return turnStartedAt;
  }

  public void setTurnStartedAt(Instant turnStartedAt) {
    this.turnStartedAt = turnStartedAt;
  }

  public int getTurnTimeoutSeconds() {
    return turnTimeoutSeconds;
  }

  public void setTurnTimeoutSeconds(int turnTimeoutSeconds) {
    this.turnTimeoutSeconds = turnTimeoutSeconds;
  }

  public int getTurnSecondsRemaining() {
    return turnSecondsRemaining;
  }

  public void setTurnSecondsRemaining(int turnSecondsRemaining) {
    this.turnSecondsRemaining = turnSecondsRemaining;
  }

  public int getConsecutiveSixes() {
    return consecutiveSixes;
  }

  public void setConsecutiveSixes(int consecutiveSixes) {
    this.consecutiveSixes = consecutiveSixes;
  }

  public List<Integer> getConsecutiveTimeouts() {
    return consecutiveTimeouts;
  }

  public void setConsecutiveTimeouts(List<Integer> consecutiveTimeouts) {
    this.consecutiveTimeouts = consecutiveTimeouts;
  }

  public boolean[] getFinished() {
    return finished;
  }

  public void setFinished(boolean[] finished) {
    this.finished = finished;
  }

  public boolean[] getEliminated() {
    return eliminated;
  }

  public void setEliminated(boolean[] eliminated) {
    this.eliminated = eliminated;
  }

  public boolean[] getIsBot() {
    return isBot;
  }

  public void setIsBot(boolean[] isBot) {
    this.isBot = isBot;
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

  public List<Map<String, Integer>> getLegalMoves() {
    return legalMoves;
  }

  public void setLegalMoves(List<Map<String, Integer>> legalMoves) {
    this.legalMoves = legalMoves;
  }

  public String getLastActionType() {
    return lastActionType;
  }

  public void setLastActionType(String lastActionType) {
    this.lastActionType = lastActionType;
  }

  public Integer getLastActionSeat() {
    return lastActionSeat;
  }

  public void setLastActionSeat(Integer lastActionSeat) {
    this.lastActionSeat = lastActionSeat;
  }

  public Integer getLastActionTokenIndex() {
    return lastActionTokenIndex;
  }

  public void setLastActionTokenIndex(Integer lastActionTokenIndex) {
    this.lastActionTokenIndex = lastActionTokenIndex;
  }

  public Integer getLastActionDice() {
    return lastActionDice;
  }

  public void setLastActionDice(Integer lastActionDice) {
    this.lastActionDice = lastActionDice;
  }

  public Integer getLastActionFrom() {
    return lastActionFrom;
  }

  public void setLastActionFrom(Integer lastActionFrom) {
    this.lastActionFrom = lastActionFrom;
  }

  public Integer getLastActionTo() {
    return lastActionTo;
  }

  public void setLastActionTo(Integer lastActionTo) {
    this.lastActionTo = lastActionTo;
  }

  public long getActionSeq() {
    return actionSeq;
  }

  public void setActionSeq(long actionSeq) {
    this.actionSeq = actionSeq;
  }
}
