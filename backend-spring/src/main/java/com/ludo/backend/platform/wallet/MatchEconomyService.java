package com.ludo.backend.platform.wallet;

import com.ludo.backend.admin.AdminSettingsService;
import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.platform.PlatformLaunchContext;
import com.ludo.backend.platform.operator.CashoutMessage;
import com.ludo.backend.platform.operator.CashoutPublishException;
import com.ludo.backend.platform.operator.CashoutPublisherService;
import com.ludo.backend.platform.operator.DebitRequest;
import com.ludo.backend.platform.operator.DebitResponse;
import com.ludo.backend.platform.operator.OperatorGatewayClient;
import com.ludo.backend.platform.operator.OperatorGatewayException;
import com.ludo.backend.platform.operator.OperatorGatewayTimeoutException;
import com.ludo.backend.platform.operator.OperatorTokenHold;
import com.ludo.backend.platform.operator.OperatorTokenHoldService;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Match ledger for admin P&amp;L + optional Legacy / Operator wallet.
 * Always writes local {@code match_economy} rows for human seats (incl. FREE / wallet-off)
 * so the admin dashboard is populated. Wallet debit/credit only when {@link #isLive()}.
 *
 * <p>Settlement mirrors craft/luzo wallet math:
 * display pot = entryFee × all seats (bots count as synthetic paid seats),
 * rake = min(pot, platformFeePerPlayer × paidSeats),
 * winner payout = pot − rake (0 if bot/house wins).
 *
 * <p>{@code wallet.mode=LEGACY} keeps the historical legacy wallet client path.
 * {@code wallet.mode=OPERATOR} uses {@link OperatorGatewayClient} debit +
 * {@link CashoutPublisherService} cashout. Player tokens are never written to Mongo.
 */
@Service
public class MatchEconomyService {

  private static final Logger log = LoggerFactory.getLogger(MatchEconomyService.class);

  private final LegacyWalletClient wallet;
  private final WalletProperties props;
  private final MatchEconomyRepository repository;
  private final AdminSettingsService adminSettings;
  private final MongoTemplate mongoTemplate;
  private final ObjectProvider<OperatorGatewayClient> operatorGateway;
  private final ObjectProvider<CashoutPublisherService> cashoutPublisher;
  private final ObjectProvider<OperatorTokenHoldService> tokenHolds;

  /** Stale CLAIMING rows may be reclaimed after this age. */
  private static final long CLAIM_STALE_SECONDS = 60;

  public MatchEconomyService(
      LegacyWalletClient wallet,
      WalletProperties props,
      MatchEconomyRepository repository,
      AdminSettingsService adminSettings,
      MongoTemplate mongoTemplate,
      ObjectProvider<OperatorGatewayClient> operatorGateway,
      ObjectProvider<CashoutPublisherService> cashoutPublisher,
      ObjectProvider<OperatorTokenHoldService> tokenHolds
  ) {
    this.wallet = wallet;
    this.props = props;
    this.repository = repository;
    this.adminSettings = adminSettings;
    this.mongoTemplate = mongoTemplate;
    this.operatorGateway = operatorGateway;
    this.cashoutPublisher = cashoutPublisher;
    this.tokenHolds = tokenHolds;
  }

  public boolean isLive() {
    if (props.isOperatorMode()) {
      return props.enabled();
    }
    return wallet.isLive();
  }

  public double entryFee() {
    return WalletProperties.money(props.entryFee());
  }

  public List<Double> betOptions() {
    List<Double> opts = props.betOptionList();
    if (opts.isEmpty() && props.entryFee() > 0) {
      return List.of(WalletProperties.money(props.entryFee()));
    }
    return opts;
  }

  public String gameId() {
    return props.gameId();
  }

  public double getBalance(String userId) {
    if (!wallet.isLive()) {
      return 0;
    }
    LegacyWalletClient.WalletResult r = wallet.getBalance(userId);
    if (r.configError()) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Wallet misconfigured, contact support");
    }
    if (!r.success()) {
      // A declined call carries the wallet provider's own reason ("Invalid userId", "User not
      // found"); only transport failures are genuinely worth retrying.
      String reason = r.retryable() || r.message() == null || r.message().isBlank()
          ? "Wallet busy, retry"
          : r.message();
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, reason);
    }
    return WalletProperties.money(r.balance());
  }

  public void reserveEntry(String matchId, String userId) {
    reserveEntry(matchId, userId, entryFee());
  }

  public void reserveEntry(String matchId, String userId, double amount) {
    if (userId == null || userId.startsWith("bot-") || matchId == null) {
      return;
    }
    double fee = WalletProperties.money(Math.max(0, amount));
    if (props.isOperatorMode()) {
      reserveEntryOperator(matchId, userId, fee);
      return;
    }
    // ----- Legacy wallet path (unchanged) -----
    var existing = repository.findByMatchIdAndUserId(matchId, userId);
    if (existing.isPresent()) {
      String st = existing.get().getStatus();
      if (MatchEconomyEntry.RESERVED.equals(st)
          || MatchEconomyEntry.PLAYING.equals(st)
          || MatchEconomyEntry.SETTLED.equals(st)) {
        log.info("reserveEntry skip — already {} matchId={} userId={}", st, matchId, userId);
        return;
      }
    }

    String txnId = "LUDO_ENTRY_" + matchId + "_" + userId;
    if (isLive() && fee > 0) {
      LegacyWalletClient.WalletResult result =
          wallet.debit(userId, fee, txnId, props.gameId(), matchId);
      if (result.configError()) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Wallet misconfigured, contact support");
      }
      if (!result.success()) {
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient balance");
      }
    }

    MatchEconomyEntry entry = existing.orElseGet(MatchEconomyEntry::new);
    entry.setId(MatchEconomyEntry.idFor(matchId, userId));
    entry.setMatchId(matchId);
    entry.setUserId(userId);
    entry.setEntryTxnId(txnId);
    entry.setEntryAmount(fee);
    entry.setStatus(MatchEconomyEntry.RESERVED);
    entry.setPublishStatus(MatchEconomyEntry.PUBLISH_NONE);
    entry.setCreatedAt(entry.getCreatedAt() == null ? Instant.now() : entry.getCreatedAt());
    entry.setUpdatedAt(Instant.now());
    repository.save(entry);
    log.info(
        "reserved entry matchId={} userId={} fee={} walletLive={} mode={}",
        matchId,
        userId,
        fee,
        isLive(),
        props.mode()
    );
  }

  /**
   * OPERATOR atomic reserve: claim → debit (stable txn_id) → promote RESERVED.
   * Concurrent joins cannot double-debit the same match/user.
   */
  private void reserveEntryOperator(String matchId, String userId, double fee) {
    String id = MatchEconomyEntry.idFor(matchId, userId);
    String txnId = "LUDO_ENTRY_" + matchId + "_" + userId;

    MatchEconomyEntry existing = repository.findById(id).orElse(null);
    if (existing != null) {
      String st = existing.getStatus();
      if (MatchEconomyEntry.RESERVED.equals(st)
          || MatchEconomyEntry.PLAYING.equals(st)
          || MatchEconomyEntry.SETTLED.equals(st)) {
        log.info("reserveEntry skip — already {} matchId={} userId={}", st, matchId, userId);
        return;
      }
      if (MatchEconomyEntry.UNKNOWN.equals(st)) {
        resumeUnknownOperatorDebit(existing, fee);
        return;
      }
      if (MatchEconomyEntry.CLAIMING.equals(st) && !isClaimStale(existing)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation in progress");
      }
    }

    if (fee <= 0 || !isLive()) {
      MatchEconomyEntry claimed = atomicClaim(id, matchId, userId, fee, txnId);
      if (claimed == null) {
        MatchEconomyEntry again = repository.findById(id).orElse(null);
        if (again != null && isTerminalReserveStatus(again.getStatus())) {
          return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation conflict");
      }
      if (!atomicPromoteToReserved(id, null, null, null)) {
        releaseClaim(id, matchId, userId);
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize reservation");
      }
      log.info("reserved entry (free/offline) matchId={} userId={} fee={}", matchId, userId, fee);
      return;
    }

    MatchEconomyEntry claimed = atomicClaim(id, matchId, userId, fee, txnId);
    if (claimed == null) {
      MatchEconomyEntry again = repository.findById(id).orElse(null);
      if (again == null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation conflict");
      }
      if (isTerminalReserveStatus(again.getStatus())) {
        log.info("reserveEntry skip after race — already {} matchId={} userId={}", again.getStatus(), matchId, userId);
        return;
      }
      if (MatchEconomyEntry.UNKNOWN.equals(again.getStatus())) {
        resumeUnknownOperatorDebit(again, fee);
        return;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation conflict");
    }

    OperatorReserveResult op = null;
    try {
      op = debitOperator(matchId, userId, fee, txnId);
      if (!atomicPromoteToReserved(id, op.betId(), op.operatorId(), op.httpSessionId())) {
        compensateOrphanOperatorDebit(matchId, userId, fee, txnId, op);
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize reservation after debit");
      }
      log.info(
          "reserved entry matchId={} userId={} fee={} walletLive={} mode=OPERATOR txnId={}",
          matchId,
          userId,
          fee,
          isLive(),
          txnId
      );
    } catch (OperatorGatewayTimeoutException e) {
      markUnknown(id, matchId, userId, fee, txnId, op);
      clearOperatorHold(matchId, userId);
      log.error(
          "operator debit UNKNOWN (timeout) matchId={} userId={} txnId={} — reuse txn_id on retry",
          matchId,
          userId,
          txnId
      );
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet busy, retry", e);
    } catch (ResponseStatusException e) {
      releaseClaim(id, matchId, userId);
      clearOperatorHold(matchId, userId);
      throw e;
    } catch (RuntimeException e) {
      releaseClaim(id, matchId, userId);
      clearOperatorHold(matchId, userId);
      throw e;
    }
  }

  /** Reconcile UNKNOWN using the same entryTxnId — never mint a new debit id. */
  private void resumeUnknownOperatorDebit(MatchEconomyEntry existing, double fee) {
    String matchId = existing.getMatchId();
    String userId = existing.getUserId();
    String txnId = existing.getEntryTxnId();
    if (txnId == null || txnId.isBlank()) {
      txnId = "LUDO_ENTRY_" + matchId + "_" + userId;
    }
    log.info(
        "reserveEntry resume UNKNOWN matchId={} userId={} txnId={} (idempotent debit)",
        matchId,
        userId,
        txnId
    );
    if (!atomicReclaimUnknown(existing.getId())) {
      MatchEconomyEntry again = repository.findById(existing.getId()).orElse(null);
      if (again != null && isTerminalReserveStatus(again.getStatus())) {
        return;
      }
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation conflict");
    }
    OperatorReserveResult op = null;
    try {
      op = debitOperator(matchId, userId, fee > 0 ? fee : existing.getEntryAmount(), txnId);
      if (!atomicPromoteToReserved(existing.getId(), op.betId(), op.operatorId(), op.httpSessionId())) {
        compensateOrphanOperatorDebit(matchId, userId, existing.getEntryAmount(), txnId, op);
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR, "Failed to finalize reservation after debit");
      }
    } catch (OperatorGatewayTimeoutException e) {
      markUnknown(existing.getId(), matchId, userId, existing.getEntryAmount(), txnId, op);
      clearOperatorHold(matchId, userId);
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet busy, retry", e);
    } catch (ResponseStatusException e) {
      // Decline after UNKNOWN: keep UNKNOWN for ops reconcile (may still be charged).
      clearOperatorHold(matchId, userId);
      markUnknown(existing.getId(), matchId, userId, existing.getEntryAmount(), txnId, op);
      throw e;
    } catch (RuntimeException e) {
      clearOperatorHold(matchId, userId);
      markUnknown(existing.getId(), matchId, userId, existing.getEntryAmount(), txnId, op);
      throw e;
    }
  }

  private MatchEconomyEntry atomicClaim(
      String id, String matchId, String userId, double fee, String txnId
  ) {
    Instant staleBefore = Instant.now().minus(CLAIM_STALE_SECONDS, ChronoUnit.SECONDS);
    Query query = new Query(new Criteria().andOperator(
        Criteria.where("_id").is(id),
        new Criteria().orOperator(
            Criteria.where("status").exists(false),
            Criteria.where("status").is(null),
            Criteria.where("status").is(MatchEconomyEntry.REFUNDED),
            Criteria.where("status").is(MatchEconomyEntry.CLAIMING)
                .and("updatedAt").lt(staleBefore)
        )
    ));
    Update update = new Update()
        .setOnInsert("_id", id)
        .setOnInsert("matchId", matchId)
        .setOnInsert("userId", userId)
        .setOnInsert("createdAt", Instant.now())
        .set("status", MatchEconomyEntry.CLAIMING)
        .set("entryTxnId", txnId)
        .set("entryAmount", fee)
        .set("publishStatus", MatchEconomyEntry.PUBLISH_NONE)
        .set("updatedAt", Instant.now());
    try {
      return mongoTemplate.findAndModify(
          query,
          update,
          FindAndModifyOptions.options().upsert(true).returnNew(true),
          MatchEconomyEntry.class
      );
    } catch (DuplicateKeyException e) {
      log.info("atomicClaim duplicate key matchId={} userId={}", matchId, userId);
      return null;
    }
  }

  private boolean atomicReclaimUnknown(String id) {
    Query query = Query.query(Criteria.where("_id").is(id).and("status").is(MatchEconomyEntry.UNKNOWN));
    Update update = new Update()
        .set("status", MatchEconomyEntry.CLAIMING)
        .set("updatedAt", Instant.now());
    MatchEconomyEntry modified = mongoTemplate.findAndModify(
        query,
        update,
        FindAndModifyOptions.options().returnNew(true),
        MatchEconomyEntry.class
    );
    return modified != null;
  }

  private boolean atomicPromoteToReserved(
      String id, String betId, String operatorId, String httpSessionId
  ) {
    Query query = Query.query(Criteria.where("_id").is(id).and("status").is(MatchEconomyEntry.CLAIMING));
    Update update = new Update()
        .set("status", MatchEconomyEntry.RESERVED)
        .set("updatedAt", Instant.now())
        .set("publishStatus", MatchEconomyEntry.PUBLISH_NONE);
    if (betId != null) {
      update.set("betId", betId);
    }
    if (operatorId != null) {
      update.set("operatorId", operatorId);
    }
    if (httpSessionId != null) {
      update.set("httpSessionId", httpSessionId);
    }
    MatchEconomyEntry modified = mongoTemplate.findAndModify(
        query,
        update,
        FindAndModifyOptions.options().returnNew(true),
        MatchEconomyEntry.class
    );
    return modified != null;
  }

  private void markUnknown(
      String id,
      String matchId,
      String userId,
      double fee,
      String txnId,
      OperatorReserveResult op
  ) {
    MatchEconomyEntry entry = repository.findById(id).orElseGet(MatchEconomyEntry::new);
    entry.setId(id);
    entry.setMatchId(matchId);
    entry.setUserId(userId);
    entry.setEntryTxnId(txnId);
    entry.setEntryAmount(fee);
    entry.setStatus(MatchEconomyEntry.UNKNOWN);
    entry.setUpdatedAt(Instant.now());
    if (entry.getCreatedAt() == null) {
      entry.setCreatedAt(Instant.now());
    }
    entry.setLastPublishError(truncateError("operator debit outcome UNKNOWN (timeout/unreachable)"));
    if (op != null) {
      if (op.betId() != null) {
        entry.setBetId(op.betId());
      }
      if (op.operatorId() != null) {
        entry.setOperatorId(op.operatorId());
      }
      if (op.httpSessionId() != null) {
        entry.setHttpSessionId(op.httpSessionId());
      }
    }
    repository.save(entry);
  }

  private void releaseClaim(String id, String matchId, String userId) {
    Query query = Query.query(Criteria.where("_id").is(id).and("status").is(MatchEconomyEntry.CLAIMING));
    var removed = mongoTemplate.remove(query, MatchEconomyEntry.class);
    clearOperatorHold(matchId, userId);
    log.info(
        "released CLAIMING reservation id={} matchId={} userId={} deletedCount={}",
        id,
        matchId,
        userId,
        removed.getDeletedCount()
    );
  }

  /**
   * Debit succeeded but promote-to-RESERVED failed. Must not leave a charge without a ledger row.
   */
  private void compensateOrphanOperatorDebit(
      String matchId,
      String userId,
      double fee,
      String txnId,
      OperatorReserveResult op
  ) {
    clearOperatorHold(matchId, userId);
    // TODO(operator-void): when the operator exposes sync rollback/void for debit txn_id,
    // call it here before persisting UNKNOWN. Integration Guide cashout queue has no void API yet.
    log.error(
        "TODO operator-void: orphan debit after ledger promote failure matchId={} userId={} txnId={} fee={} "
            + "— persisting UNKNOWN for reconciliation; manual/operator void required",
        matchId,
        userId,
        txnId,
        fee
    );
    markUnknown(MatchEconomyEntry.idFor(matchId, userId), matchId, userId, fee, txnId, op);
  }

  private static boolean isClaimStale(MatchEconomyEntry entry) {
    if (entry.getUpdatedAt() == null) {
      return true;
    }
    return entry.getUpdatedAt().isBefore(Instant.now().minus(CLAIM_STALE_SECONDS, ChronoUnit.SECONDS));
  }

  private static boolean isTerminalReserveStatus(String status) {
    return MatchEconomyEntry.RESERVED.equals(status)
        || MatchEconomyEntry.PLAYING.equals(status)
        || MatchEconomyEntry.SETTLED.equals(status);
  }

  public void markPlaying(String matchId) {
    for (MatchEconomyEntry e : repository.findByMatchId(matchId)) {
      if (MatchEconomyEntry.RESERVED.equals(e.getStatus())) {
        e.setStatus(MatchEconomyEntry.PLAYING);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
      }
    }
  }

  public void refundEntry(String matchId, String userId) {
    if (userId == null || userId.startsWith("bot-")) {
      return;
    }
    MatchEconomyEntry entry = repository.findByMatchIdAndUserId(matchId, userId).orElse(null);
    if (entry == null) {
      return;
    }
    if (MatchEconomyEntry.REFUNDED.equals(entry.getStatus())
        || MatchEconomyEntry.SETTLED.equals(entry.getStatus())) {
      return;
    }
    // Drop in-flight claim with no confirmed debit.
    if (MatchEconomyEntry.CLAIMING.equals(entry.getStatus())) {
      releaseClaim(entry.getId(), matchId, userId);
      return;
    }
    String refundTxn = "LUDO_REFUND_" + matchId + "_" + userId;
    boolean debited = entry.getEntryTxnId() != null && !entry.getEntryTxnId().isBlank();
    if (isLive() && entry.getEntryAmount() > 0 && debited) {
      if (props.isOperatorMode()) {
        // TODO(operator-void): Integration Guide has no sync rollback/void for OPERATOR debits.
        // When available, call void(entryTxnId) here and only mark REFUNDED on success.
        // Leaving REFUNDED locally keeps the row recoverable for manual reconcile; do not delete txn_id.
        log.error(
            "TODO operator-void: operator refund cannot auto-reverse debit matchId={} userId={} "
                + "entryTxnId={} status={} — marking REFUNDED locally; funds require operator-side void",
            matchId,
            userId,
            entry.getEntryTxnId(),
            entry.getStatus()
        );
        clearOperatorHold(matchId, userId);
        entry.setSettleTxnId(refundTxn);
      } else {
        LegacyWalletClient.WalletResult result = wallet.rollback(
            userId, entry.getEntryTxnId(), entry.getEntryAmount(), props.gameId(), matchId);
        if (!result.success()) {
          // Leave the row un-refunded so a retry can reverse the same debit id.
          // Crediting here instead would double-refund a rollback that in fact
          // landed on the wallet but answered late or unparseably.
          log.error("refund FAILED matchId={} userId={} status={}", matchId, userId, result.status());
          return;
        }
        entry.setSettleTxnId(result.transactionId() != null ? result.transactionId() : refundTxn);
      }
    }
    entry.setStatus(MatchEconomyEntry.REFUNDED);
    entry.setUpdatedAt(Instant.now());
    repository.save(entry);
    clearOperatorHold(matchId, userId);
    log.info("refunded entry matchId={} userId={}", matchId, userId);
  }

  public void refundAllHumans(Room room) {
    if (room == null) {
      return;
    }
    for (RoomPlayer p : room.getPlayers()) {
      if (!p.isBot()) {
        refundEntry(room.getId(), p.getUserId());
      }
    }
  }

  public void settleMatch(Room room, GameSnapshot snap) {
    if (room == null || snap == null) {
      return;
    }
    if (!GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return;
    }

    ensureHumanLedgerRows(room);

    Integer winnerSeat = snap.getWinnerSeat();
    if (winnerSeat == null || winnerSeat < 0 || winnerSeat >= room.getPlayers().size()) {
      log.warn("settleMatch no winnerSeat matchId={}", room.getId());
      for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
        if (MatchEconomyEntry.PLAYING.equals(e.getStatus())
            || MatchEconomyEntry.RESERVED.equals(e.getStatus())) {
          e.setStatus(MatchEconomyEntry.SETTLED);
          e.setUpdatedAt(Instant.now());
          repository.save(e);
        }
        clearOperatorHold(room.getId(), e.getUserId());
      }
      return;
    }

    RoomPlayer winner = room.getPlayers().get(winnerSeat);
    MatchEconomyEntry winEntry =
        repository.findByMatchIdAndUserId(room.getId(), winner.getUserId()).orElse(null);
    if (winEntry != null && MatchEconomyEntry.SETTLED.equals(winEntry.getStatus())) {
      // Outbox: already settled but cashout still pending → retry publish only.
      if (props.isOperatorMode() && isCashoutRetryable(winEntry)) {
        retryCashoutEntry(winEntry);
        return;
      }
      for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
        if (MatchEconomyEntry.PLAYING.equals(e.getStatus())
            || MatchEconomyEntry.RESERVED.equals(e.getStatus())) {
          e.setStatus(MatchEconomyEntry.SETTLED);
          e.setUpdatedAt(Instant.now());
          repository.save(e);
        }
        if (!isCashoutRetryable(e)) {
          clearOperatorHold(room.getId(), e.getUserId());
        }
      }
      return;
    }

    SettlementMath math = computeSettlement(room);
    double payout;
    if (winner.isBot()) {
      // House win: keep real reservations; no winner credit.
      payout = 0;
    } else if (props.winPayout() > 0) {
      payout = WalletProperties.money(props.winPayout());
    } else if (props.winMultiplier() > 0) {
      payout = WalletProperties.money(
          (winEntry != null ? winEntry.getEntryAmount() : entryFee()) * props.winMultiplier());
    } else {
      payout = math.winnerPayout();
    }

    String winTxn = "LUDO_WIN_" + room.getId() + "_" + winner.getUserId();
    String publishStatus = MatchEconomyEntry.PUBLISH_NONE;
    String txnRefId = null;
    String lastPublishError = null;
    int publishAttempts = 0;
    if (isLive() && !winner.isBot() && payout > 0) {
      if (props.isOperatorMode()) {
        OperatorSettleResult op = publishOperatorCashout(room, winner, winEntry, payout, winTxn);
        winTxn = op.winTxn();
        publishStatus = op.publishStatus();
        txnRefId = op.txnRefId();
        lastPublishError = op.lastError();
        publishAttempts = op.attempts();
      } else {
        LegacyWalletClient.WalletResult result =
            wallet.credit(winner.getUserId(), payout, winTxn, props.gameId(), room.getId());
        if (!result.success()) {
          log.error("settle credit FAILED matchId={} userId={}", room.getId(), winner.getUserId());
          winTxn = "LUDO_WIN_LOCAL_" + room.getId() + "_" + winner.getUserId();
        }
      }
    } else {
      winTxn = "LUDO_WIN_LOCAL_" + room.getId() + "_" + winner.getUserId();
    }

    for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
      if (MatchEconomyEntry.REFUNDED.equals(e.getStatus())) {
        clearOperatorHold(room.getId(), e.getUserId());
        continue;
      }
      e.setStatus(MatchEconomyEntry.SETTLED);
      e.setUpdatedAt(Instant.now());
      if (!winner.isBot() && e.getUserId().equals(winner.getUserId())) {
        e.setSettleTxnId(winTxn);
        e.setSettleAmount(payout);
        if (txnRefId != null) {
          e.setTxnRefId(txnRefId);
        }
        if (props.isOperatorMode()) {
          e.setPublishStatus(publishStatus);
          e.setPublishAttempts(publishAttempts);
          e.setLastPublishError(truncateError(lastPublishError));
        }
        if (MatchEconomyEntry.PUBLISH_PUBLISHED.equals(publishStatus)
            || MatchEconomyEntry.PUBLISH_NONE.equals(publishStatus)
            || MatchEconomyEntry.PUBLISH_FAILED.equals(publishStatus)) {
          clearOperatorHold(room.getId(), e.getUserId());
        }
        // PENDING: keep hold for in-JVM retry until Phase 6B shared storage.
      } else {
        clearOperatorHold(room.getId(), e.getUserId());
      }
      repository.save(e);
    }
    log.info(
        "settled matchId={} winner={} seats={} displayPot={} rake={} realIncome={} payout={} walletLive={} mode={} publishStatus={}",
        room.getId(),
        winner.getUserId(),
        math.paidSeats(),
        math.displayPot(),
        math.rake(),
        math.realIncome(),
        payout,
        isLive(),
        props.mode(),
        publishStatus
    );
  }

  /**
   * Outbox drain: republish OPERATOR cashouts stuck in {@link MatchEconomyEntry#PUBLISH_PENDING}.
   * Safe to call periodically; uses the same stable win {@code txn_id} for idempotency.
   */
  public int retryPendingCashouts(int limit) {
    if (!props.isOperatorMode()) {
      return 0;
    }
    int max = Math.max(1, limit);
    List<MatchEconomyEntry> pending = repository.findByStatusAndPublishStatus(
        MatchEconomyEntry.SETTLED, MatchEconomyEntry.PUBLISH_PENDING);
    int done = 0;
    for (MatchEconomyEntry entry : pending) {
      if (done >= max) {
        break;
      }
      if (retryCashoutEntry(entry)) {
        done++;
      }
    }
    return done;
  }

  /** @return true if a publish attempt was made */
  public boolean retryCashoutEntry(MatchEconomyEntry entry) {
    if (entry == null || !props.isOperatorMode() || !isCashoutRetryable(entry)) {
      return false;
    }
    if (entry.getSettleAmount() <= 0
        || entry.getSettleTxnId() == null
        || entry.getSettleTxnId().isBlank()
        || entry.getTxnRefId() == null
        || entry.getTxnRefId().isBlank()) {
      entry.setPublishStatus(MatchEconomyEntry.PUBLISH_FAILED);
      entry.setLastPublishError(truncateError("missing settleTxnId/txnRefId/amount for cashout retry"));
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      clearOperatorHold(entry.getMatchId(), entry.getUserId());
      return false;
    }
    OperatorTokenHold hold = resolveHold(entry.getMatchId(), entry.getUserId(), entry.getHttpSessionId())
        .orElse(null);
    String token = hold != null ? hold.token() : null;
    String operatorId = hold != null && hold.operatorId() != null
        ? hold.operatorId()
        : entry.getOperatorId();
    String ip = hold != null ? hold.ip() : "0.0.0.0";
    if (token == null || token.isBlank() || operatorId == null || operatorId.isBlank()) {
      entry.setPublishAttempts(entry.getPublishAttempts() + 1);
      entry.setLastPublishError(truncateError(
          "token hold missing (backend="
              + tokenHoldBackend()
              + "; set REDIS_URL or keep launch session alive)"));
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      log.error(
          "cashout outbox retry blocked (no token) matchId={} userId={} attempts={} holdBackend={}",
          entry.getMatchId(),
          entry.getUserId(),
          entry.getPublishAttempts(),
          tokenHoldBackend()
      );
      return true;
    }
    CashoutPublisherService publisher = cashoutPublisher.getIfAvailable();
    if (publisher == null) {
      entry.setPublishAttempts(entry.getPublishAttempts() + 1);
      entry.setLastPublishError(truncateError("cashout publisher unavailable"));
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      return true;
    }
    String amount = formatMoney(entry.getSettleAmount());
    CashoutMessage message = new CashoutMessage(
        entry.getSettleTxnId(),
        entry.getTxnRefId(),
        CashoutMessage.TXN_TYPE_CREDIT,
        amount,
        entry.getUserId(),
        props.gameId(),
        amount + " credited for Ludo match " + entry.getMatchId(),
        ip,
        operatorId,
        token
    );
    entry.setPublishAttempts(entry.getPublishAttempts() + 1);
    try {
      publisher.publish(message);
      entry.setPublishStatus(MatchEconomyEntry.PUBLISH_PUBLISHED);
      entry.setLastPublishError(null);
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      clearOperatorHold(entry.getMatchId(), entry.getUserId());
      log.info(
          "cashout outbox retry OK matchId={} userId={} txnId={} attempts={}",
          entry.getMatchId(),
          entry.getUserId(),
          entry.getSettleTxnId(),
          entry.getPublishAttempts()
      );
    } catch (CashoutPublishException e) {
      entry.setPublishStatus(MatchEconomyEntry.PUBLISH_PENDING);
      entry.setLastPublishError(truncateError(e.getMessage()));
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      log.error(
          "cashout outbox retry FAILED matchId={} userId={} txnId={} attempts={} err={}",
          entry.getMatchId(),
          entry.getUserId(),
          entry.getSettleTxnId(),
          entry.getPublishAttempts(),
          e.getMessage()
      );
    }
    return true;
  }

  /**
   * Craft-style pot math: bots count as synthetic paid seats for pot/rake;
   * real income is human cash only.
   * FREE rooms (entryFee=0) use configured {@link #entryFee()} so admin P&amp;L still shows.
   */
  public SettlementMath computeSettlement(Room room) {
    return computeSettlement(room, null);
  }

  /**
   * Same as {@link #computeSettlement(Room)} but reuses preloaded ledger rows (avoids N+1).
   */
  public SettlementMath computeSettlement(Room room, List<MatchEconomyEntry> preloaded) {
    List<MatchEconomyEntry> rows = (preloaded != null ? preloaded : repository.findByMatchId(room.getId()))
        .stream()
        .filter(e -> !MatchEconomyEntry.REFUNDED.equals(e.getStatus()))
        .toList();
    double ledgerIncome = rows.stream().mapToDouble(MatchEconomyEntry::getEntryAmount).sum();

    int seats = Math.max(room.getPlayers().size(), room.getMaxPlayers());
    long humans = room.getPlayers().stream().filter(p -> !p.isBot()).count();
    double seatFee = room.getEntryFee() > 0
        ? WalletProperties.money(room.getEntryFee())
        : 0;
    if (seatFee <= 0 && ledgerIncome > 0 && humans > 0) {
      seatFee = WalletProperties.money(ledgerIncome / humans);
    }
    if (seatFee <= 0) {
      seatFee = entryFee();
    }

    double realIncome = ledgerIncome;
    if (realIncome <= 0 && seatFee > 0 && humans > 0) {
      realIncome = WalletProperties.money(seatFee * humans);
    }

    double displayPot = WalletProperties.money(seatFee * seats);
    int paidSeats = seatFee > 0 ? seats : 0;
    double feePerPlayer = adminSettings.platformFeePerPlayer();
    double rake = WalletProperties.money(Math.min(displayPot, feePerPlayer * paidSeats));
    double winnerPayout = WalletProperties.money(Math.max(0, displayPot - rake));
    return new SettlementMath(displayPot, rake, realIncome, winnerPayout, paidSeats, seatFee);
  }

  public record SettlementMath(
      double displayPot,
      double rake,
      double realIncome,
      double winnerPayout,
      int paidSeats,
      double seatFee
  ) {}

  private OperatorReserveResult debitOperator(
      String matchId, String userId, double fee, String txnId
  ) {
    OperatorGatewayClient client = operatorGateway.getIfAvailable();
    if (client == null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Operator gateway not available");
    }
    PlatformLaunchContext launch = requireOperatorLaunch(userId);
    String token = launch.token();
    String operatorId = launch.operatorId();
    if (token == null || token.isBlank() || operatorId == null || operatorId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
    String ip = clientIp();
    String betId = "BT:" + matchId + ":" + userId + ":" + operatorId;
    String amount = formatMoney(fee);
    DebitRequest request = new DebitRequest(
        txnId,
        DebitRequest.TXN_TYPE_DEBIT,
        amount,
        userId,
        props.gameId(),
        betId,
        amount + " debited for Ludo match " + matchId,
        ip
    );
    try {
      DebitResponse response = client.debit(token, request);
      if (response == null || !response.status()) {
        throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient balance");
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (OperatorGatewayTimeoutException e) {
      log.error(
          "operator debit TIMEOUT matchId={} userId={} txnId={} err={}",
          matchId,
          userId,
          txnId,
          e.getMessage()
      );
      throw e;
    } catch (OperatorGatewayException e) {
      log.error("operator debit failed matchId={} userId={} err={}", matchId, userId, e.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet busy, retry", e);
    }
    String httpSessionId = currentSessionId();
    OperatorTokenHoldService holds = tokenHolds.getIfAvailable();
    if (holds == null) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Operator token hold service not available");
    }
    holds.put(matchId, userId, new OperatorTokenHold(token, operatorId, ip, httpSessionId));
    return new OperatorReserveResult(betId, operatorId, httpSessionId);
  }

  private OperatorSettleResult publishOperatorCashout(
      Room room,
      RoomPlayer winner,
      MatchEconomyEntry winEntry,
      double payout,
      String winTxn
  ) {
    CashoutPublisherService publisher = cashoutPublisher.getIfAvailable();
    String txnRefId = winEntry != null ? winEntry.getEntryTxnId() : null;
    if (txnRefId == null || txnRefId.isBlank()) {
      log.error("operator cashout missing debit txn_ref matchId={} userId={}", room.getId(), winner.getUserId());
      return new OperatorSettleResult(
          winTxn,
          MatchEconomyEntry.PUBLISH_FAILED,
          null,
          "missing debit txn_ref",
          1
      );
    }
    OperatorTokenHold hold = resolveHold(room.getId(), winner.getUserId(),
        winEntry != null ? winEntry.getHttpSessionId() : null).orElse(null);
    String token = hold != null ? hold.token() : null;
    String operatorId = hold != null
        ? hold.operatorId()
        : (winEntry != null ? winEntry.getOperatorId() : null);
    String ip = hold != null ? hold.ip() : "0.0.0.0";
    if (token == null || token.isBlank() || operatorId == null || operatorId.isBlank()) {
      log.error(
          "operator cashout missing token/operatorId matchId={} userId={} holdBackend={} — leaving PENDING",
          room.getId(),
          winner.getUserId(),
          tokenHoldBackend()
      );
      return new OperatorSettleResult(
          winTxn,
          MatchEconomyEntry.PUBLISH_PENDING,
          txnRefId,
          "token hold missing (" + tokenHoldBackend() + ")",
          1
      );
    }
    if (publisher == null) {
      log.error("operator cashout publisher unavailable matchId={} — leaving PENDING", room.getId());
      return new OperatorSettleResult(
          winTxn,
          MatchEconomyEntry.PUBLISH_PENDING,
          txnRefId,
          "publisher unavailable",
          1
      );
    }
    String amount = formatMoney(payout);
    CashoutMessage message = new CashoutMessage(
        winTxn,
        txnRefId,
        CashoutMessage.TXN_TYPE_CREDIT,
        amount,
        winner.getUserId(),
        props.gameId(),
        amount + " credited for Ludo match " + room.getId(),
        ip,
        operatorId,
        token
    );
    try {
      publisher.publish(message);
      return new OperatorSettleResult(
          winTxn, MatchEconomyEntry.PUBLISH_PUBLISHED, txnRefId, null, 1);
    } catch (CashoutPublishException e) {
      log.error(
          "operator cashout publish FAILED (outbox PENDING) matchId={} userId={} txnId={} txnRefId={} err={}",
          room.getId(),
          winner.getUserId(),
          winTxn,
          txnRefId,
          e.getMessage()
      );
      return new OperatorSettleResult(
          winTxn,
          MatchEconomyEntry.PUBLISH_PENDING,
          txnRefId,
          e.getMessage(),
          1
      );
    }
  }

  private static boolean isCashoutRetryable(MatchEconomyEntry entry) {
    return entry != null
        && MatchEconomyEntry.SETTLED.equals(entry.getStatus())
        && MatchEconomyEntry.PUBLISH_PENDING.equals(entry.getPublishStatus());
  }

  private PlatformLaunchContext requireOperatorLaunch(String userId) {
    PlatformLaunchContext ctx = currentLaunch();
    if (ctx == null || ctx.token() == null || ctx.token().isBlank()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
    if (ctx.userId() == null || !ctx.userId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
    }
    return ctx;
  }

  private void clearOperatorHold(String matchId, String userId) {
    OperatorTokenHoldService holds = tokenHolds.getIfAvailable();
    if (holds != null) {
      holds.remove(matchId, userId);
    }
  }

  private Optional<OperatorTokenHold> resolveHold(
      String matchId, String userId, String httpSessionId
  ) {
    OperatorTokenHoldService holds = tokenHolds.getIfAvailable();
    if (holds == null) {
      return Optional.empty();
    }
    return holds.get(matchId, userId, httpSessionId);
  }

  private String tokenHoldBackend() {
    OperatorTokenHoldService holds = tokenHolds.getIfAvailable();
    return holds == null ? "NONE" : holds.backend();
  }

  private static String truncateError(String error) {
    if (error == null || error.isBlank()) {
      return null;
    }
    return error.length() <= 500 ? error : error.substring(0, 500) + "…";
  }

  private static PlatformLaunchContext currentLaunch() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
      return null;
    }
    HttpServletRequest request = servletAttrs.getRequest();
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object attr = session.getAttribute(PlatformLaunchContext.SESSION_KEY);
    return attr instanceof PlatformLaunchContext ctx ? ctx : null;
  }

  private static String currentSessionId() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
      return null;
    }
    HttpSession session = servletAttrs.getRequest().getSession(false);
    return session == null ? null : session.getId();
  }

  private static String clientIp() {
    RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
    if (!(attrs instanceof ServletRequestAttributes servletAttrs)) {
      return "0.0.0.0";
    }
    HttpServletRequest request = servletAttrs.getRequest();
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String ip = request.getRemoteAddr();
    return ip == null || ip.isBlank() ? "0.0.0.0" : ip;
  }

  private static String formatMoney(double amount) {
    long cents = Math.round(amount * 100.0);
    return BigDecimal.valueOf(cents, 2).toPlainString();
  }

  private void ensureHumanLedgerRows(Room room) {
    double fee = room.getEntryFee() > 0
        ? WalletProperties.money(room.getEntryFee())
        : entryFee();
    for (RoomPlayer p : room.getPlayers()) {
      if (p.isBot() || p.getUserId() == null) {
        continue;
      }
      if (repository.findByMatchIdAndUserId(room.getId(), p.getUserId()).isPresent()) {
        continue;
      }
      MatchEconomyEntry entry = new MatchEconomyEntry();
      entry.setId(MatchEconomyEntry.idFor(room.getId(), p.getUserId()));
      entry.setMatchId(room.getId());
      entry.setUserId(p.getUserId());
      entry.setEntryTxnId("LUDO_ENTRY_LOCAL_" + room.getId() + "_" + p.getUserId());
      entry.setEntryAmount(fee);
      entry.setStatus(MatchEconomyEntry.PLAYING);
      entry.setPublishStatus(MatchEconomyEntry.PUBLISH_NONE);
      entry.setCreatedAt(Instant.now());
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
    }
  }

  private record OperatorReserveResult(String betId, String operatorId, String httpSessionId) {}

  private record OperatorSettleResult(
      String winTxn,
      String publishStatus,
      String txnRefId,
      String lastError,
      int attempts
  ) {}
}
