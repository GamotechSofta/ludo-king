package com.ludo.backend.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "rooms")
public class Room {
  @Id
  private String id;

  @Indexed(unique = true)
  private String roomCode;

  private RoomStatus status = RoomStatus.WAITING;
  private int maxPlayers = 4;
  private List<RoomPlayer> players = new ArrayList<>();
  private String stakeTier = "FREE";
  private long entryFee;
  private Instant createdAt = Instant.now();
  private Instant startedAt;
  private Instant endedAt;
  private Instant fillDeadlineAt;
  private String winnerId;
  private List<Integer> finalStandings = new ArrayList<>();
  /** When set, room is in countdown before PLAYING. */
  private Instant countdownEndsAt;
  private Integer countdownValue;
  private Instant reconnectDeadlineAt;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRoomCode() {
    return roomCode;
  }

  public void setRoomCode(String roomCode) {
    this.roomCode = roomCode;
  }

  public RoomStatus getStatus() {
    return status;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
  }

  public int getMaxPlayers() {
    return maxPlayers;
  }

  public void setMaxPlayers(int maxPlayers) {
    this.maxPlayers = maxPlayers;
  }

  public List<RoomPlayer> getPlayers() {
    return players;
  }

  public void setPlayers(List<RoomPlayer> players) {
    this.players = players;
  }

  public String getStakeTier() {
    return stakeTier;
  }

  public void setStakeTier(String stakeTier) {
    this.stakeTier = stakeTier;
  }

  public long getEntryFee() {
    return entryFee;
  }

  public void setEntryFee(long entryFee) {
    this.entryFee = entryFee;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
  }

  public Instant getFillDeadlineAt() {
    return fillDeadlineAt;
  }

  public void setFillDeadlineAt(Instant fillDeadlineAt) {
    this.fillDeadlineAt = fillDeadlineAt;
  }

  public String getWinnerId() {
    return winnerId;
  }

  public void setWinnerId(String winnerId) {
    this.winnerId = winnerId;
  }

  public List<Integer> getFinalStandings() {
    return finalStandings;
  }

  public void setFinalStandings(List<Integer> finalStandings) {
    this.finalStandings = finalStandings;
  }

  public Instant getCountdownEndsAt() {
    return countdownEndsAt;
  }

  public void setCountdownEndsAt(Instant countdownEndsAt) {
    this.countdownEndsAt = countdownEndsAt;
  }

  public Integer getCountdownValue() {
    return countdownValue;
  }

  public void setCountdownValue(Integer countdownValue) {
    this.countdownValue = countdownValue;
  }

  public Instant getReconnectDeadlineAt() {
    return reconnectDeadlineAt;
  }

  public void setReconnectDeadlineAt(Instant reconnectDeadlineAt) {
    this.reconnectDeadlineAt = reconnectDeadlineAt;
  }
}
