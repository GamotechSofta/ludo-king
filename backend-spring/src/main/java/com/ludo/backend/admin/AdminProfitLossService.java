package com.ludo.backend.admin;

import com.ludo.backend.platform.wallet.MatchEconomyEntry;
import com.ludo.backend.platform.wallet.MatchEconomyRepository;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomRepository;
import com.ludo.backend.room.RoomStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Aggregates match_economy (+ completed rooms fallback) into admin P&amp;L views.
 */
@Service
public class AdminProfitLossService {

  private final MatchEconomyRepository repository;
  private final RoomRepository roomRepository;

  public AdminProfitLossService(
      MatchEconomyRepository repository,
      RoomRepository roomRepository
  ) {
    this.repository = repository;
    this.roomRepository = roomRepository;
  }

  public Map<String, Object> summary(Integer players, String operatorId) {
    List<MatchEconomyEntry> all = repository.findAll();
    Map<String, List<MatchEconomyEntry>> byMatch = groupByMatch(all);

    double income = 0;
    double payouts = 0;
    int settledGames = 0;
    Set<String> economyMatchIds = new HashSet<>();
    for (Map.Entry<String, List<MatchEconomyEntry>> e : byMatch.entrySet()) {
      if (!isSettledMatch(e.getValue())) {
        continue;
      }
      settledGames++;
      economyMatchIds.add(e.getKey());
      for (MatchEconomyEntry row : e.getValue()) {
        income += row.getEntryAmount();
        payouts += row.getSettleAmount();
      }
    }

    List<Room> completed = roomRepository.findByStatus(RoomStatus.COMPLETED);
    // Also pick up finished boards still stuck as IN_PROGRESS (settle missed)
    List<Room> finishedLike = new ArrayList<>(completed);
    for (Room room : roomRepository.findByStatus(RoomStatus.IN_PROGRESS)) {
      if (roomLooksFinished(room)) {
        finishedLike.add(room);
      }
    }
    int roomOnlyGames = 0;
    Set<String> users = new HashSet<>(distinctUsers(all));
    for (Room room : finishedLike) {
      if (economyMatchIds.contains(room.getId())) {
        continue;
      }
      if (players != null && players > 0 && room.getMaxPlayers() != players) {
        continue;
      }
      roomOnlyGames++;
      for (RoomPlayer p : room.getPlayers()) {
        if (p != null && p.getUserId() != null && !p.isBot()) {
          users.add(p.getUserId());
        }
      }
    }
    int totalGames = settledGames + roomOnlyGames;

    Map<String, Object> defaultOp = new LinkedHashMap<>();
    defaultOp.put("operatorId", "default");
    defaultOp.put("name", "Ludo King");
    defaultOp.put("domain", "local");
    defaultOp.put("games", totalGames);
    defaultOp.put("users", users.size());
    defaultOp.put("income", round2(income));
    defaultOp.put("platformProfit", round2(income - payouts));

    if (operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId)) {
      Map<String, Object> empty = new LinkedHashMap<>();
      empty.put("currency", "INR");
      empty.put("totalGames", 0);
      empty.put("totalIncome", 0);
      empty.put("totalPayouts", 0);
      empty.put("platformProfit", 0);
      empty.put("totalUsers", 0);
      empty.put("operators", List.of());
      return empty;
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("currency", "INR");
    out.put("totalGames", totalGames);
    out.put("games", totalGames);
    out.put("totalIncome", round2(income));
    out.put("income", round2(income));
    out.put("totalPayouts", round2(payouts));
    out.put("payouts", round2(payouts));
    out.put("platformProfit", round2(income - payouts));
    out.put("profit", round2(income - payouts));
    out.put("totalUsers", users.size());
    out.put("users", users.size());
    out.put("totalOperators", 1);
    out.put("operatorsCount", 1);
    out.put("operators", List.of(defaultOp));
    out.put("topOperators", List.of(defaultOp));
    return out;
  }

  public Map<String, Object> games(int page, int limit, Integer players, String operatorId) {
    List<MatchEconomyEntry> all = repository.findAll();
    Map<String, List<MatchEconomyEntry>> byMatch = groupByMatch(all);

    List<Map<String, Object>> games = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (Map.Entry<String, List<MatchEconomyEntry>> e : byMatch.entrySet()) {
      if (!isSettledMatch(e.getValue())) {
        continue;
      }
      if (operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId)) {
        continue;
      }
      double entry = e.getValue().stream().mapToDouble(MatchEconomyEntry::getEntryAmount).sum();
      double settle = e.getValue().stream().mapToDouble(MatchEconomyEntry::getSettleAmount).sum();
      int seats = e.getValue().size();
      if (players != null && players > 0 && seats != players) {
        continue;
      }
      seen.add(e.getKey());
      games.add(gameRowFromEconomy(e.getKey(), e.getValue(), entry, settle, seats));
    }

    for (Room room : roomRepository.findByStatus(RoomStatus.COMPLETED)) {
      if (seen.contains(room.getId())) {
        continue;
      }
      if (operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId)) {
        continue;
      }
      if (players != null && players > 0 && room.getMaxPlayers() != players) {
        continue;
      }
      games.add(gameRowFromRoom(room));
    }
    for (Room room : roomRepository.findByStatus(RoomStatus.IN_PROGRESS)) {
      if (!roomLooksFinished(room) || seen.contains(room.getId())) {
        continue;
      }
      if (operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId)) {
        continue;
      }
      if (players != null && players > 0 && room.getMaxPlayers() != players) {
        continue;
      }
      games.add(gameRowFromRoom(room));
    }

    games.sort((a, b) -> {
      Instant ia = (Instant) a.get("finishedAt");
      Instant ib = (Instant) b.get("finishedAt");
      if (ia == null && ib == null) return 0;
      if (ia == null) return 1;
      if (ib == null) return -1;
      return ib.compareTo(ia);
    });

    return paginate(games, page, limit, "games");
  }

  public Map<String, Object> users(int page, int limit, Integer players, String operatorId) {
    if (operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId)) {
      return paginate(List.of(), page, limit, "users");
    }
    List<MatchEconomyEntry> all = repository.findAll().stream()
        .filter(e -> MatchEconomyEntry.SETTLED.equals(e.getStatus())
            || MatchEconomyEntry.REFUNDED.equals(e.getStatus()))
        .toList();

    Map<String, List<MatchEconomyEntry>> byUser = all.stream()
        .collect(Collectors.groupingBy(MatchEconomyEntry::getUserId));

    Map<String, Map<String, Object>> userRows = new LinkedHashMap<>();
    for (Map.Entry<String, List<MatchEconomyEntry>> e : byUser.entrySet()) {
      double wagered = e.getValue().stream().mapToDouble(MatchEconomyEntry::getEntryAmount).sum();
      double payout = e.getValue().stream().mapToDouble(MatchEconomyEntry::getSettleAmount).sum();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("userId", e.getKey());
      row.put("username", e.getKey());
      row.put("operatorId", "default");
      row.put("operatorName", "Ludo King");
      row.put("games", e.getValue().stream().map(MatchEconomyEntry::getMatchId).distinct().count());
      row.put("wagered", round2(wagered));
      row.put("payout", round2(payout));
      row.put("pnl", round2(payout - wagered));
      row.put("currency", "INR");
      userRows.put(e.getKey(), row);
    }

    for (Room room : roomRepository.findByStatus(RoomStatus.COMPLETED)) {
      addRoomUsers(userRows, room);
    }
    for (Room room : roomRepository.findByStatus(RoomStatus.IN_PROGRESS)) {
      if (roomLooksFinished(room)) {
        addRoomUsers(userRows, room);
      }
    }

    List<Map<String, Object>> users = new ArrayList<>(userRows.values());
    users.sort((a, b) -> {
      long ga = ((Number) a.get("games")).longValue();
      long gb = ((Number) b.get("games")).longValue();
      if (ga != gb) {
        return Long.compare(gb, ga);
      }
      return Double.compare(
          ((Number) b.get("wagered")).doubleValue(),
          ((Number) a.get("wagered")).doubleValue()
      );
    });

    return paginate(users, page, limit, "users");
  }

  private static void addRoomUsers(Map<String, Map<String, Object>> userRows, Room room) {
    for (RoomPlayer p : room.getPlayers()) {
      if (p == null || p.isBot() || p.getUserId() == null) {
        continue;
      }
      Map<String, Object> row = userRows.computeIfAbsent(p.getUserId(), id -> {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("userId", id);
        r.put("username", p.getUsername() != null ? p.getUsername() : id);
        r.put("operatorId", "default");
        r.put("operatorName", "Ludo King");
        r.put("games", 0L);
        r.put("wagered", 0.0);
        r.put("payout", 0.0);
        r.put("pnl", 0.0);
        r.put("currency", "INR");
        return r;
      });
      if (p.getUsername() != null && !p.getUsername().isBlank()) {
        row.put("username", p.getUsername());
      }
      row.put("games", ((Number) row.get("games")).longValue() + 1);
    }
  }

  /** Snapshot JSON says FINISHED, or endedAt was set. */
  private static boolean roomLooksFinished(Room room) {
    if (room.getEndedAt() != null) {
      return true;
    }
    String json = room.getLiveSnapshotJson();
    return json != null && json.contains("\"phase\":\"FINISHED\"");
  }

  private static Map<String, Object> gameRowFromEconomy(
      String matchId,
      List<MatchEconomyEntry> rows,
      double entry,
      double settle,
      int seats
  ) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", matchId);
    row.put("roomId", matchId);
    row.put("roundId", matchId);
    row.put("mode", seats + "P");
    row.put("playerCount", seats);
    row.put("entryFee", seats > 0 ? round2(entry / seats) : 0);
    row.put("pot", round2(entry));
    row.put("winnerPayout", round2(settle));
    row.put("platformProfit", round2(entry - settle));
    row.put("currency", "INR");
    row.put("operatorId", "default");
    row.put("operatorName", "Ludo King");
    row.put(
        "finishedAt",
        rows.stream()
            .map(MatchEconomyEntry::getUpdatedAt)
            .filter(t -> t != null)
            .max(Comparator.naturalOrder())
            .orElse(null)
    );
    row.put("realPlayers", seats);
    row.put("botPlayers", 0);
    row.put(
        "players",
        rows.stream().map(p -> {
          Map<String, Object> pr = new LinkedHashMap<>();
          pr.put("userId", p.getUserId());
          pr.put("bet", p.getEntryAmount());
          pr.put("payout", p.getSettleAmount());
          pr.put("pnl", round2(p.getSettleAmount() - p.getEntryAmount()));
          pr.put("isBot", false);
          pr.put("operatorId", "default");
          return pr;
        }).toList()
    );
    return row;
  }

  private static Map<String, Object> gameRowFromRoom(Room room) {
    long bots = room.getPlayers().stream().filter(RoomPlayer::isBot).count();
    long humans = room.getPlayers().size() - bots;
    double fee = room.getEntryFee();
    double pot = fee * humans;
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", room.getId());
    row.put("roomId", room.getId());
    row.put("roundId", room.getId());
    row.put("mode", room.getMaxPlayers() + "P");
    row.put("playerCount", room.getMaxPlayers());
    row.put("entryFee", round2(fee));
    row.put("pot", round2(pot));
    row.put("winnerPayout", 0);
    row.put("platformProfit", round2(pot));
    row.put("currency", "INR");
    row.put("operatorId", "default");
    row.put("operatorName", "Ludo King");
    row.put("finishedAt", room.getEndedAt() != null ? room.getEndedAt() : room.getStartedAt());
    row.put("realPlayers", (int) humans);
    row.put("botPlayers", (int) bots);
    row.put(
        "players",
        room.getPlayers().stream().map(p -> {
          Map<String, Object> pr = new LinkedHashMap<>();
          pr.put("userId", p.getUserId());
          pr.put("username", p.getUsername());
          pr.put("bet", p.isBot() ? 0 : fee);
          pr.put("payout", 0);
          pr.put("pnl", p.isBot() ? 0 : round2(-fee));
          pr.put("isBot", p.isBot());
          pr.put("operatorId", "default");
          return pr;
        }).toList()
    );
    return row;
  }

  private static Map<String, List<MatchEconomyEntry>> groupByMatch(List<MatchEconomyEntry> all) {
    return all.stream().collect(Collectors.groupingBy(MatchEconomyEntry::getMatchId));
  }

  private static boolean isSettledMatch(List<MatchEconomyEntry> rows) {
    return rows.stream().anyMatch(e -> MatchEconomyEntry.SETTLED.equals(e.getStatus()));
  }

  private static List<String> distinctUsers(List<MatchEconomyEntry> all) {
    return all.stream().map(MatchEconomyEntry::getUserId).distinct().toList();
  }

  private static Map<String, Object> paginate(
      List<Map<String, Object>> items,
      int page,
      int limit,
      String key
  ) {
    int p = Math.max(1, page);
    int lim = Math.max(1, Math.min(100, limit));
    int total = items.size();
    int totalPages = Math.max(1, (int) Math.ceil(total / (double) lim));
    int from = Math.min((p - 1) * lim, total);
    int to = Math.min(from + lim, total);
    Map<String, Object> out = new HashMap<>();
    out.put(key, items.subList(from, to));
    out.put("items", items.subList(from, to));
    out.put("page", p);
    out.put("limit", lim);
    out.put("total", total);
    out.put("totalPages", totalPages);
    return out;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
