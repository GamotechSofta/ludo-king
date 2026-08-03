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
  /** Atomic claim before operator debit (transient). */
  public static final String CLAIMING = "CLAIMING";
  /**
   * Operator debit timed out / outcome unknown. Same {@code entryTxnId} must be reused;
   * never issue a second distinct debit id.
   */
  public static final String UNKNOWN = "UNKNOWN";

  /** Cashout not applicable / not attempted. */
  public static final String PUBLISH_NONE = "NONE";
  /** Cashout queued for (re)publish — must not be abandoned. */
  public static final String PUBLISH_PENDING = "PENDING";
  /** Cashout message confirmed by the broker. */
  public static final String PUBLISH_PUBLISHED = "PUBLISHED";
  /**
   * Terminal publish failure only when retry is impossible (e.g. missing txn ref).
   * Transient broker failures stay {@link #PUBLISH_PENDING}.
   */
  public static final String PUBLISH_FAILED = "FAILED";

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

  /** Operator id from launch session (OPERATOR mode). Never stores the player token. */
  private String operatorId;
  /** Bet reference sent with the operator debit. */
  private String betId;
  /** Links credit cashout to the original debit {@code txn_id}. */
  private String txnRefId;
  /**
   * Cashout outbox: {@link #PUBLISH_NONE}, {@link #PUBLISH_PENDING},
   * {@link #PUBLISH_PUBLISHED}, {@link #PUBLISH_FAILED}.
   */
  private String publishStatus;
  /** Number of cashout publish attempts (initial + retries). */
  private int publishAttempts;
  /** Last publish error message (truncated). */
  private String lastPublishError;
  /**
   * Spring Session id from debit request — pointer only, never the player token.
   * Used when Redis hold is missing so any node can resolve the launch session.
   */
  private String httpSessionId;

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

  public String getOperatorId() {
    return operatorId;
  }

  public void setOperatorId(String operatorId) {
    this.operatorId = operatorId;
  }

  public String getBetId() {
    return betId;
  }

  public void setBetId(String betId) {
    this.betId = betId;
  }

  public String getTxnRefId() {
    return txnRefId;
  }

  public void setTxnRefId(String txnRefId) {
    this.txnRefId = txnRefId;
  }

  public String getPublishStatus() {
    return publishStatus;
  }

  public void setPublishStatus(String publishStatus) {
    this.publishStatus = publishStatus;
  }

  public int getPublishAttempts() {
    return publishAttempts;
  }

  public void setPublishAttempts(int publishAttempts) {
    this.publishAttempts = publishAttempts;
  }

  public String getLastPublishError() {
    return lastPublishError;
  }

  public void setLastPublishError(String lastPublishError) {
    this.lastPublishError = lastPublishError;
  }

  public String getHttpSessionId() {
    return httpSessionId;
  }

  public void setHttpSessionId(String httpSessionId) {
    this.httpSessionId = httpSessionId;
  }
}
