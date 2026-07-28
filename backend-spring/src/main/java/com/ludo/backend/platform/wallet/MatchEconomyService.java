package com.ludo.backend.platform.wallet;

import com.ludo.backend.admin.AdminSettingsService;
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
 * Match ledger for admin P&amp;L + optional Aakda wallet.
 * Always writes local {@code match_economy} rows for human seats (incl. FREE / wallet-off)
 * so the admin dashboard is populated. Wallet debit/credit only when {@link #isLive()}.
 *
 * <p>Settlement mirrors craft/luzo wallet math:
 * display pot = entryFee × all seats (bots count as synthetic paid seats),
 * rake = min(pot, platformFeePerPlayer × paidSeats),
 * winner payout = pot − rake (0 if bot/house wins).
 */
@Service
public class MatchEconomyService {

  private static final Logger log = LoggerFactory.getLogger(MatchEconomyService.class);

  private final AakdaWalletClient wallet;
  private final WalletProperties props;
  private final MatchEconomyRepository repository;
  private final AdminSettingsService adminSettings;

  public MatchEconomyService(
      AakdaWalletClient wallet,
      WalletProperties props,
      MatchEconomyRepository repository,
      AdminSettingsService adminSettings
  ) {
    this.wallet = wallet;
    this.props = props;
    this.repository = repository;
    this.adminSettings = adminSettings;
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
    if (userId == null || userId.startsWith("bot-") || matchId == null) {
      return;
    }
    double fee = WalletProperties.money(Math.max(0, amount));
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
      AakdaWalletClient.WalletResult result =
          wallet.debit(userId, fee, txnId, props.gameId(), matchId);
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
    entry.setCreatedAt(entry.getCreatedAt() == null ? Instant.now() : entry.getCreatedAt());
    entry.setUpdatedAt(Instant.now());
    repository.save(entry);
    log.info(
        "reserved entry matchId={} userId={} fee={} walletLive={}",
        matchId,
        userId,
        fee,
        isLive()
    );
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
    String refundTxn = "LUDO_REFUND_" + matchId + "_" + userId;
    if (isLive() && entry.getEntryAmount() > 0) {
      AakdaWalletClient.WalletResult result = wallet.rollback(
          userId, entry.getEntryTxnId(), entry.getEntryAmount(), props.gameId(), matchId);
      if (!result.success()) {
        result = wallet.credit(userId, entry.getEntryAmount(), refundTxn, props.gameId(), matchId);
      }
      if (!result.success()) {
        log.error("refund FAILED matchId={} userId={} status={}", matchId, userId, result.status());
        return;
      }
      entry.setSettleTxnId(result.transactionId() != null ? result.transactionId() : refundTxn);
    }
    entry.setStatus(MatchEconomyEntry.REFUNDED);
    entry.setUpdatedAt(Instant.now());
    repository.save(entry);
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
      }
      return;
    }

    RoomPlayer winner = room.getPlayers().get(winnerSeat);
    MatchEconomyEntry winEntry =
        repository.findByMatchIdAndUserId(room.getId(), winner.getUserId()).orElse(null);
    if (winEntry != null && MatchEconomyEntry.SETTLED.equals(winEntry.getStatus())) {
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
    if (isLive() && !winner.isBot() && payout > 0) {
      AakdaWalletClient.WalletResult result =
          wallet.credit(winner.getUserId(), payout, winTxn, props.gameId(), room.getId());
      if (!result.success()) {
        log.error("settle credit FAILED matchId={} userId={}", room.getId(), winner.getUserId());
        winTxn = "LUDO_WIN_LOCAL_" + room.getId() + "_" + winner.getUserId();
      }
    } else {
      winTxn = "LUDO_WIN_LOCAL_" + room.getId() + "_" + winner.getUserId();
    }

    for (MatchEconomyEntry e : repository.findByMatchId(room.getId())) {
      if (MatchEconomyEntry.REFUNDED.equals(e.getStatus())) {
        continue;
      }
      e.setStatus(MatchEconomyEntry.SETTLED);
      e.setUpdatedAt(Instant.now());
      if (!winner.isBot() && e.getUserId().equals(winner.getUserId())) {
        e.setSettleTxnId(winTxn);
        e.setSettleAmount(payout);
      }
      repository.save(e);
    }
    log.info(
        "settled matchId={} winner={} seats={} displayPot={} rake={} realIncome={} payout={} walletLive={}",
        room.getId(),
        winner.getUserId(),
        math.paidSeats(),
        math.displayPot(),
        math.rake(),
        math.realIncome(),
        payout,
        isLive()
    );
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
      entry.setCreatedAt(Instant.now());
      entry.setUpdatedAt(Instant.now());
      repository.save(entry);
    }
  }
}
