package com.ludo.backend.admin;

import com.ludo.backend.platform.wallet.MatchEconomyEntry;
import com.ludo.backend.platform.wallet.MatchEconomyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Aggregates match_economy into admin P&amp;L views expected by the PotLudo admin UI.
 */
@Service
public class AdminProfitLossService {

  private final MatchEconomyRepository repository;

  public AdminProfitLossService(MatchEconomyRepository repository) {
    this.repository = repository;
  }

  public Map<String, Object> summary(Integer players, String operatorId) {
    List<MatchEconomyEntry> all = repository.findAll();
    Map<String, List<MatchEconomyEntry>> byMatch = groupByMatch(all);

    double income = 0;
    double payouts = 0;
    int settledGames = 0;
    for (List<MatchEconomyEntry> rows : byMatch.values()) {
      if (!isSettledMatch(rows)) {
        continue;
      }
      settledGames++;
      for (MatchEconomyEntry e : rows) {
        income += e.getEntryAmount();
        payouts += e.getSettleAmount();
      }
    }

    Map<String, Object> defaultOp = new LinkedHashMap<>();
    defaultOp.put("operatorId", "default");
    defaultOp.put("name", "Ludo King");
    defaultOp.put("domain", "local");
    defaultOp.put("games", settledGames);
    defaultOp.put("users", distinctUsers(all).size());
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
    out.put("totalGames", settledGames);
    out.put("games", settledGames);
    out.put("totalIncome", round2(income));
    out.put("income", round2(income));
    out.put("totalPayouts", round2(payouts));
    out.put("payouts", round2(payouts));
    out.put("platformProfit", round2(income - payouts));
    out.put("profit", round2(income - payouts));
    out.put("totalUsers", distinctUsers(all).size());
    out.put("users", distinctUsers(all).size());
    out.put("operators", List.of(defaultOp));
    out.put("topOperators", List.of(defaultOp));
    return out;
  }

  public Map<String, Object> games(int page, int limit, Integer players, String operatorId) {
    List<MatchEconomyEntry> all = repository.findAll();
    Map<String, List<MatchEconomyEntry>> byMatch = groupByMatch(all);

    List<Map<String, Object>> games = new ArrayList<>();
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
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", e.getKey());
      row.put("roomId", e.getKey());
      row.put("roundId", e.getKey());
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
          e.getValue().stream()
              .map(MatchEconomyEntry::getUpdatedAt)
              .filter(t -> t != null)
              .max(Comparator.naturalOrder())
              .orElse(null)
      );
      row.put("realPlayers", seats);
      row.put("botPlayers", 0);
      row.put(
          "players",
          e.getValue().stream().map(p -> {
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
      games.add(row);
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

    List<Map<String, Object>> users = new ArrayList<>();
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
      users.add(row);
    }

    users.sort((a, b) -> Double.compare(
        ((Number) b.get("wagered")).doubleValue(),
        ((Number) a.get("wagered")).doubleValue()
    ));

    return paginate(users, page, limit, "users");
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
