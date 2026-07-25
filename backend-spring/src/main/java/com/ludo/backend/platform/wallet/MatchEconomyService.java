package com.ludo.backend.platform.wallet;

import com.ludo.backend.game.GameEngineService;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Model A — entry fee / bet per match.
 *
 * Txn ids: LUDO_ENTRY_{matchId}_{userId}, LUDO_WIN_*, LUDO_REFUND_*
 */
@Service
public class MatchEconomyService {

  private static final Logger log = LoggerFactory.getLogger(MatchEconomyService.class);

  private final AakdaWalletClient wallet;
  private final WalletProperties props;
  private final MatchEconomyRepository repository;

  public MatchEconomyService(
      AakdaWalletClient wallet,
      WalletProperties props,
      MatchEconomyRepository repository
  ) {
    this.wallet = wallet;
    this.props = props;
    this.repository = repository;
  }

  public boolean isLive() {
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
    AakdaWalletClient.WalletResult r = wallet.getBalance(userId);
    if (!r.success()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Wallet busy, retry");
    }
    return WalletProperties.money(r.balance());
  }

  public void reserveEntry(String matchId, String userId) {
    reserveEntry(matchId, userId, entryFee());
  }

  public void reserveEntry(String matchId, String userId, double amount) {
    if (!isLive() || userId == null || userId.startsWith("bot-")) {
      return;
    }
    double fee = WalletProperties.money(amount);
    if (fee <= 0) {
      return;
    }
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
    AakdaWalletClient.WalletResult result =
        wallet.debit(userId, fee, txnId, props.gameId(), matchId);
    if (!result.success()) {
      throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Insufficient balance");
    }

    MatchEconomyEntry entry = existing.orElseGet(MatchEconomyEntry::new);
    entry.setId(MatchEconomyEntry.idFor(matchId, userId));
    entry.setMatchId(matchId);
    entry.setUserId(userId);
    entry.setEntryTxnId(txnId);
    entry.setEntryAmount(fee);
    entry.setStatus(MatchEconomyEntry.RESERVED);
    entry.setCreatedAt(entry.getCreatedAt() == null ? Instant.now() : entry.getCreatedAt());
    entry.setUpdatedAt(Instant.now());
    repository.save(entry);
    log.info("reserved entry matchId={} userId={} txn={} fee={}", matchId, userId, txnId, fee);
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
    if (!isLive() || userId == null || userId.startsWith("bot-")) {
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
    String refundTxn = "LUDO_REFUND_" + matchId + "_" + userId;
    AakdaWalletClient.WalletResult result = wallet.rollback(
        userId, entry.getEntryTxnId(), entry.getEntryAmount(), props.gameId(), matchId);
    if (!result.success()) {
      result = wallet.credit(userId, entry.getEntryAmount(), refundTxn, props.gameId(), matchId);
    }
    if (result.success()) {
      entry.setStatus(MatchEconomyEntry.REFUNDED);
      entry.setSettleTxnId(result.transactionId() != null ? result.transactionId() : refundTxn);
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
      log.info("refunded entry matchId={} userId={}", matchId, userId);
    } else {
      log.error("refund FAILED matchId={} userId={} status={}", matchId, userId, result.status());
    }
  }

  public void refundAllHumans(Room room) {
    if (!isLive() || room == null) {
      return;
    }
    for (RoomPlayer p : room.getPlayers()) {
      if (!p.isBot()) {
        refundEntry(room.getId(), p.getUserId());
      }
    }
  }

  public void settleMatch(Room room, GameSnapshot snap) {
    if (!isLive() || room == null || snap == null) {
      return;
    }
    if (!GameEngineService.PHASE_FINISHED.equals(snap.getPhase())) {
      return;
    }
    Integer winnerSeat = snap.getWinnerSeat();
    if (winnerSeat == null || winnerSeat < 0 || winnerSeat >= room.getPlayers().size()) {
      log.warn("settleMatch no winnerSeat matchId={}", room.getId());
      return;
    }
    RoomPlayer winner = room.getPlayers().get(winnerSeat);
    if (winner.isBot()) {
      log.info("settleMatch bot won matchId={} — no human credit", room.getId());
      for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
        if (MatchEconomyEntry.PLAYING.equals(e.getStatus())
            || MatchEconomyEntry.RESERVED.equals(e.getStatus())) {
          e.setStatus(MatchEconomyEntry.SETTLED);
          e.setUpdatedAt(Instant.now());
          repository.save(e);
        }
      }
      return;
    }

    MatchEconomyEntry winEntry =
        repository.findByMatchIdAndUserId(room.getId(), winner.getUserId()).orElse(null);
    if (winEntry != null && MatchEconomyEntry.SETTLED.equals(winEntry.getStatus())) {
      return;
    }

    double pot = repository.findByMatchId(room.getId()).stream()
        .filter(e -> !MatchEconomyEntry.REFUNDED.equals(e.getStatus()))
        .mapToDouble(MatchEconomyEntry::getEntryAmount)
        .sum();
    double payout;
    if (props.winPayout() > 0) {
      payout = WalletProperties.money(props.winPayout());
    } else if (props.winMultiplier() > 0) {
      payout = WalletProperties.money(
          (winEntry != null ? winEntry.getEntryAmount() : entryFee()) * props.winMultiplier());
    } else {
      payout = WalletProperties.money(pot);
    }

    String winTxn = "LUDO_WIN_" + room.getId() + "_" + winner.getUserId();
    AakdaWalletClient.WalletResult result =
        wallet.credit(winner.getUserId(), payout, winTxn, props.gameId(), room.getId());
    if (!result.success()) {
      log.error("settle credit FAILED matchId={} userId={}", room.getId(), winner.getUserId());
      return;
    }

    for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
      if (MatchEconomyEntry.REFUNDED.equals(e.getStatus())) {
        continue;
      }
      e.setStatus(MatchEconomyEntry.SETTLED);
      e.setUpdatedAt(Instant.now());
      if (e.getUserId().equals(winner.getUserId())) {
        e.setSettleTxnId(winTxn);
        e.setSettleAmount(payout);
      }
      repository.save(e);
    }
    log.info("settled matchId={} winner={} payout={}", room.getId(), winner.getUserId(), payout);
  }
}
