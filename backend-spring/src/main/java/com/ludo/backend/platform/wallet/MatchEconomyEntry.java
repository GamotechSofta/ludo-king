package com.ludo.backend.platform.wallet;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "match_economy")
@CompoundIndex(name = "match_user_unique", def = "{'matchId': 1, 'userId': 1}", unique = true)
public class MatchEconomyEntry {

  public static final String RESERVED = "RESERVED";
  public static final String PLAYING = "PLAYING";
  public static final String SETTLED = "SETTLED";
  public static final String REFUNDED = "REFUNDED";

  @Id
  private String id;

  private String matchId;
  private String userId;
  private String entryTxnId;
  private String settleTxnId;
  private String status;
  private double entryAmount;
  private double settleAmount;
  private Instant createdAt;
  private Instant updatedAt;

  public static String idFor(String matchId, String userId) {
    return matchId + "_" + userId;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getMatchId() {
    return matchId;
  }

  public void setMatchId(String matchId) {
    this.matchId = matchId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getEntryTxnId() {
    return entryTxnId;
  }

  public void setEntryTxnId(String entryTxnId) {
    this.entryTxnId = entryTxnId;
  }

  public String getSettleTxnId() {
    return settleTxnId;
  }

  public void setSettleTxnId(String settleTxnId) {
    this.settleTxnId = settleTxnId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public double getEntryAmount() {
    return entryAmount;
  }

  public void setEntryAmount(double entryAmount) {
    this.entryAmount = entryAmount;
  }

  public double getSettleAmount() {
    return settleAmount;
  }

  public void setSettleAmount(double settleAmount) {
    this.settleAmount = settleAmount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
