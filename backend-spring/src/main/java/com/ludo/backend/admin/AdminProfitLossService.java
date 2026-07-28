package com.ludo.backend.admin;

import com.ludo.backend.platform.wallet.MatchEconomyEntry;
import com.ludo.backend.platform.wallet.MatchEconomyRepository;
import com.ludo.backend.platform.wallet.MatchEconomyService;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomRepository;
import com.ludo.backend.room.RoomStatus;
import com.ludo.backend.user.UserService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Admin P&amp;L: business view = real cash-in vs winner payout.
 * <pre>
 * totalRealIncome = Σ real player bets
 * winnerPayout    = win amount if winner real, else 0
 * platformProfit  = totalRealIncome − winnerPayout
 * </pre>
 * Display pot/rake include synthetic bot seats (informative).
 */
@Service
public class AdminProfitLossService {

  private final MatchEconomyRepository repository;
  private final RoomRepository roomRepository;
  private final MatchEconomyService matchEconomy;
  private final AdminSettingsService adminSettings;
  private final UserService userService;

  private static final long GAMES_CACHE_TTL_MS = 45_000;
  private final Map<String, CachedGames> gamesCache = new java.util.concurrent.ConcurrentHashMap<>();

  private record CachedGames(List<Map<String, Object>> games, long expiresAtMs) {}

  public AdminProfitLossService(
      MatchEconomyRepository repository,
      RoomRepository roomRepository,
      MatchEconomyService matchEconomy,
      AdminSettingsService adminSettings,
      UserService userService
  ) {
    this.repository = repository;
    this.roomRepository = roomRepository;
    this.matchEconomy = matchEconomy;
    this.adminSettings = adminSettings;
    this.userService = userService;
  }

  public Map<String, Object> summary(Integer players, String operatorId) {
    if (unknownOperator(operatorId)) {
      return emptySummary();
    }

    List<Map<String, Object>> games = buildAllGames(players, false);
    double income = 0;
    double payouts = 0;
    double totalProfit = 0;
    double totalLoss = 0;
    double totalRake = 0;
    int gamesProfit = 0;
    int gamesLoss = 0;
    int gamesFlat = 0;
    int games2p = 0;
    int games4p = 0;
    int gamesOther = 0;
    int houseWins = 0;
    int humanWins = 0;
    int withBots = 0;
    int allHuman = 0;
    double income2p = 0;
    double income4p = 0;
    double profit2p = 0;
    double profit4p = 0;
    Set<String> users = new HashSet<>();
    for (Map<String, Object> g : games) {
      double gIncome = number(g.get("totalRealIncome"), number(g.get("income"), 0));
      double gPayout = number(g.get("winnerPayout"), 0);
      double gRake = number(g.get("rake"), number(g.get("displayPotRake"), 0));
      income += gIncome;
      payouts += gPayout;
      totalRake += gRake;
      double gameProfit = number(g.get("platformProfit"), number(g.get("profit"), 0));
      if (gameProfit > 0.009) {
        totalProfit += gameProfit;
        gamesProfit++;
      } else if (gameProfit < -0.009) {
        totalLoss += Math.abs(gameProfit);
        gamesLoss++;
      } else {
        gamesFlat++;
      }

      int pc = (int) number(g.get("playerCount"), 0);
      if (pc == 2) {
        games2p++;
        income2p += gIncome;
        profit2p += gameProfit;
      } else if (pc == 4) {
        games4p++;
        income4p += gIncome;
        profit4p += gameProfit;
      } else {
        gamesOther++;
      }

      int bots = (int) number(g.get("botPlayers"), 0);
      if (bots > 0) {
        withBots++;
      } else {
        allHuman++;
      }
      // House win ≈ real income kept with zero payout
      if (gPayout <= 0.009 && gIncome > 0.009) {
        houseWins++;
      } else if (gPayout > 0.009) {
        humanWins++;
      }

      Object plist = g.get("players");
      if (plist instanceof List<?> list) {
        for (Object o : list) {
          if (o instanceof Map<?, ?> p && !truthy(p.get("isBot"))) {
            Object uid = p.get("userId");
            if (uid != null) {
              users.add(String.valueOf(uid));
            }
          }
        }
      }
    }

    double netProfit = round2(income - payouts);
    double profitOnly = round2(totalProfit);
    double lossOnly = round2(totalLoss);
    double marginPct = income > 0 ? round2((netProfit / income) * 100.0) : 0;
    double avgIncomePerGame = games.isEmpty() ? 0 : round2(income / games.size());
    double avgNetPerGame = games.isEmpty() ? 0 : round2(netProfit / games.size());

    Map<String, Object> defaultOp = new LinkedHashMap<>();
    defaultOp.put("operatorId", "default");
    defaultOp.put("name", "Ludo King");
    defaultOp.put("domain", "local");
    defaultOp.put("games", games.size());
    defaultOp.put("users", users.size());
    defaultOp.put("income", round2(income));
    defaultOp.put("totalRealIncome", round2(income));
    defaultOp.put("platformProfit", netProfit);
    defaultOp.put("profit", profitOnly);
    defaultOp.put("loss", lossOnly);
    defaultOp.put("attributedProfit", netProfit);

    Map<String, Object> byMode = new LinkedHashMap<>();
    byMode.put("2P", Map.of(
        "games", games2p,
        "income", round2(income2p),
        "platformProfit", round2(profit2p)
    ));
    byMode.put("4P", Map.of(
        "games", games4p,
        "income", round2(income4p),
        "platformProfit", round2(profit4p)
    ));
    if (gamesOther > 0) {
      byMode.put("other", Map.of("games", gamesOther, "income", 0, "platformProfit", 0));
    }

    Map<String, Object> charts = new LinkedHashMap<>();
    charts.put(
        "profitLoss",
        List.of(
            Map.of("label", "Profit", "value", profitOnly, "color", "#047857"),
            Map.of("label", "Loss", "value", lossOnly, "color", "#b91c1c")
        )
    );
    charts.put(
        "incomePayout",
        List.of(
            Map.of("label", "Income", "value", round2(income), "color", "#0f766e"),
            Map.of("label", "Winner payouts", "value", round2(payouts), "color", "#0369a1")
        )
    );
    charts.put(
        "modeGames",
        List.of(
            Map.of("label", "2 Player", "value", (double) games2p, "color", "#0ea5e9"),
            Map.of("label", "4 Player", "value", (double) games4p, "color", "#8b5cf6"),
            Map.of("label", "Other", "value", (double) gamesOther, "color", "#94a3b8")
        )
    );
    charts.put(
        "outcome",
        List.of(
            Map.of("label", "House / bot win", "value", (double) houseWins, "color", "#047857"),
            Map.of("label", "Human win", "value", (double) humanWins, "color", "#ca8a04"),
            Map.of("label", "Break-even", "value", (double) gamesFlat, "color", "#94a3b8")
        )
    );
    charts.put(
        "fill",
        List.of(
            Map.of("label", "With bots", "value", (double) withBots, "color", "#f59e0b"),
            Map.of("label", "All humans", "value", (double) allHuman, "color", "#14b8a6")
        )
    );
    charts.put(
        "modeProfit",
        List.of(
            Map.of("label", "2P net", "value", round2(profit2p), "color", "#0ea5e9"),
            Map.of("label", "4P net", "value", round2(profit4p), "color", "#8b5cf6")
        )
    );

    Map<String, Object> insights = new LinkedHashMap<>();
    insights.put("formula", "platformProfit = totalRealIncome - winnerPayout");
    insights.put("rakeNote", "Rake = platformFeePerPlayer x seats (bots count as paid seats)");
    insights.put("botFillNote", "Bot-heavy matches can show loss when a real player wins the full pot - rake");
    insights.put("marginPct", marginPct);
    insights.put("avgIncomePerGame", avgIncomePerGame);
    insights.put("avgNetPerGame", avgNetPerGame);
    insights.put("winningGames", gamesProfit);
    insights.put("losingGames", gamesLoss);
    insights.put("flatGames", gamesFlat);
    insights.put("houseWins", houseWins);
    insights.put("humanWins", humanWins);
    insights.put("withBots", withBots);
    insights.put("allHuman", allHuman);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("currency", "INR");
    out.put("totalGames", games.size());
    out.put("games", games.size());
    out.put("totalIncome", round2(income));
    out.put("income", round2(income));
    out.put("totalRealIncome", round2(income));
    out.put("totalPayouts", round2(payouts));
    out.put("payouts", round2(payouts));
    out.put("winnerPayouts", round2(payouts));
    out.put("platformProfit", netProfit);
    out.put("profit", profitOnly);
    out.put("totalProfit", profitOnly);
    out.put("loss", lossOnly);
    out.put("totalLoss", lossOnly);
    out.put("totalRake", round2(totalRake));
    out.put("rake", round2(totalRake));
    out.put("marginPct", marginPct);
    out.put("avgIncomePerGame", avgIncomePerGame);
    out.put("avgNetPerGame", avgNetPerGame);
    out.put("byMode", byMode);
    out.put("charts", charts);
    out.put("insights", insights);
    out.put("totalUsers", users.size());
    out.put("users", users.size());
    out.put("totalOperators", 1);
    out.put("operatorsCount", 1);
    out.put("operators", List.of(defaultOp));
    out.put("topOperators", List.of(defaultOp));
    out.put("platformFeePerPlayer", adminSettings.platformFeePerPlayer());
    return out;
  }

  public Map<String, Object> games(int page, int limit, Integer players, String operatorId) {
    if (unknownOperator(operatorId)) {
      return paginate(List.of(), page, limit, "games");
    }
    // List view: skip per-user DB name lookups (room usernames are enough for table/modal)
    List<Map<String, Object>> games = buildAllGames(players, false);
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
    if (unknownOperator(operatorId)) {
      return paginate(List.of(), page, limit, "users");
    }

    // Aggregate from finished games (respects 2P/4P filter).
    Map<String, Map<String, Object>> userRows = new LinkedHashMap<>();
    for (Map<String, Object> g : buildAllGames(players, false)) {
      Object plist = g.get("players");
      if (!(plist instanceof List<?> list)) {
        continue;
      }
      for (Object o : list) {
        if (!(o instanceof Map<?, ?> p) || truthy(p.get("isBot"))) {
          continue;
        }
        String userId = String.valueOf(p.get("userId"));
        if (userId == null || "null".equals(userId)) {
          continue;
        }
        double bet = number(p.get("bet"), 0);
        double payout = number(p.get("payout"), 0);
        Map<String, Object> row = userRows.computeIfAbsent(userId, id -> {
          Map<String, Object> r = new LinkedHashMap<>();
          r.put("userId", id);
          String uname = p.get("username") != null ? String.valueOf(p.get("username")) : id;
          r.put("username", uname);
          r.put("name", uname);
          r.put("operatorId", "default");
          r.put("operatorName", "Ludo King");
          r.put("games", 0L);
          r.put("wagered", 0.0);
          r.put("totalBet", 0.0);
          r.put("payout", 0.0);
          r.put("totalWin", 0.0);
          r.put("pnl", 0.0);
          r.put("profitLoss", 0.0);
          r.put("wins", 0L);
          r.put("losses", 0L);
          r.put("currency", "INR");
          return r;
        });
        if (p.get("username") != null) {
          String uname = String.valueOf(p.get("username"));
          if (!UserService.isGenericPlayerLabel(uname)) {
            row.put("username", uname);
            row.put("name", uname);
          }
        }
        row.put("games", ((Number) row.get("games")).longValue() + 1);
        double wagered = number(row.get("wagered"), 0) + bet;
        double win = number(row.get("payout"), 0) + payout;
        row.put("wagered", round2(wagered));
        row.put("totalBet", round2(wagered));
        row.put("payout", round2(win));
        row.put("totalWin", round2(win));
        row.put("pnl", round2(win - wagered));
        row.put("profitLoss", round2(win - wagered));
        if (payout > 0) {
          row.put("wins", ((Number) row.get("wins")).longValue() + 1);
        } else if (bet > 0) {
          row.put("losses", ((Number) row.get("losses")).longValue() + 1);
        }
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

  private List<Map<String, Object>> buildAllGames(Integer players) {
    return buildAllGames(players, true);
  }

  /**
   * @param includePlayers when false, skip per-player name lookups (summary path).
   */
  private List<Map<String, Object>> buildAllGames(Integer players, boolean includePlayers) {
    // Short TTL cache — first admin paint hits this hard otherwise.
    String cacheKey = (players == null ? "all" : String.valueOf(players))
        + "|"
        + includePlayers;
    CachedGames hit = gamesCache.get(cacheKey);
    long now = System.currentTimeMillis();
    if (hit != null && hit.expiresAtMs() > now) {
      return new ArrayList<>(hit.games());
    }

    List<MatchEconomyEntry> all = repository.findAll();
    Map<String, List<MatchEconomyEntry>> byMatch = groupByMatch(all);

    List<Room> finished = finishedRooms();
    Map<String, Room> roomsById = new HashMap<>(Math.max(16, finished.size() * 2));
    for (Room room : finished) {
      roomsById.put(room.getId(), room);
    }

    // Batch-load any economy match rooms missing from finished set (one query).
    List<String> missing = byMatch.keySet().stream()
        .filter(id -> !roomsById.containsKey(id))
        .toList();
    if (!missing.isEmpty()) {
      for (Room room : roomRepository.findAllById(missing)) {
        roomsById.put(room.getId(), room);
      }
    }

    Map<String, String> nameCache = new HashMap<>();
    List<Map<String, Object>> games = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (Map.Entry<String, List<MatchEconomyEntry>> e : byMatch.entrySet()) {
      if (!isSettledMatch(e.getValue())) {
        continue;
      }
      Room room = roomsById.get(e.getKey());
      Map<String, Object> row =
          gameRowFromEconomy(e.getKey(), e.getValue(), room, includePlayers, nameCache);
      if (!matchesPlayerFilter(row, players)) {
        continue;
      }
      seen.add(e.getKey());
      games.add(row);
    }

    for (Room room : finished) {
      if (seen.contains(room.getId())) {
        continue;
      }
      List<MatchEconomyEntry> rows = byMatch.getOrDefault(room.getId(), List.of());
      Map<String, Object> row = gameRowFromRoom(room, rows, includePlayers, nameCache);
      if (!matchesPlayerFilter(row, players)) {
        continue;
      }
      games.add(row);
    }

    gamesCache.put(cacheKey, new CachedGames(List.copyOf(games), now + GAMES_CACHE_TTL_MS));
    return games;
  }

  private String displayName(String userId, String fallback, Map<String, String> nameCache) {
    if (userId == null) {
      return fallback != null ? fallback : "Player";
    }
    if (nameCache != null) {
      return nameCache.computeIfAbsent(
          userId, id -> userService.resolveDisplayName(id, fallback));
    }
    return userService.resolveDisplayName(userId, fallback);
  }

  private List<Room> finishedRooms() {
    List<Room> out = new ArrayList<>(roomRepository.findByStatus(RoomStatus.COMPLETED));
    for (Room room : roomRepository.findByStatus(RoomStatus.IN_PROGRESS)) {
      if (roomLooksFinished(room)) {
        out.add(room);
      }
    }
    return out;
  }

  private Map<String, Object> gameRowFromEconomy(
      String matchId,
      List<MatchEconomyEntry> rows,
      Room room,
      boolean includePlayers,
      Map<String, String> nameCache
  ) {
    MatchEconomyService.SettlementMath math = room != null
        ? matchEconomy.computeSettlement(room, rows)
        : null;

    double ledgerIncome = rows.stream()
        .filter(e -> !MatchEconomyEntry.REFUNDED.equals(e.getStatus()))
        .mapToDouble(MatchEconomyEntry::getEntryAmount)
        .sum();
    double ledgerPayout = rows.stream().mapToDouble(MatchEconomyEntry::getSettleAmount).sum();

    int playerCount;
    int realPlayers;
    int botPlayers;
    double entryFee;
    double displayPot;
    double rake;
    double realIncome;
    double winnerPayout;
    boolean winnerBot = room != null && isWinnerBot(room);

    if (math != null) {
      playerCount = Math.max(room.getMaxPlayers(), room.getPlayers().size());
      botPlayers = (int) room.getPlayers().stream().filter(RoomPlayer::isBot).count();
      realPlayers = room.getPlayers().size() - botPlayers;
      entryFee = math.seatFee();
      displayPot = math.displayPot();
      rake = math.rake();
      realIncome = ledgerIncome > 0 ? ledgerIncome : math.realIncome();
      if (ledgerPayout > 0) {
        winnerPayout = ledgerPayout;
      } else if (winnerBot) {
        winnerPayout = 0;
      } else {
        winnerPayout = math.winnerPayout();
      }
    } else {
      realPlayers = rows.size();
      botPlayers = 0;
      playerCount = realPlayers;
      entryFee = realPlayers > 0 && ledgerIncome > 0
          ? round2(ledgerIncome / realPlayers)
          : matchEconomy.entryFee();
      displayPot = round2(entryFee * playerCount);
      double feePer = adminSettings.platformFeePerPlayer();
      rake = round2(Math.min(displayPot, feePer * playerCount));
      realIncome = ledgerIncome > 0 ? ledgerIncome : round2(entryFee * realPlayers);
      winnerPayout = ledgerPayout > 0 ? ledgerPayout : round2(Math.max(0, displayPot - rake));
    }

    double platformProfit = round2(realIncome - winnerPayout);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", matchId);
    row.put("roomId", matchId);
    row.put("roundId", matchId);
    row.put("roomCode", room != null ? room.getRoomCode() : null);
    row.put("mode", playerCount + "P");
    row.put("playerCount", playerCount);
    row.put("entryFee", round2(entryFee));
    row.put("pot", round2(displayPot));
    row.put("displayPot", round2(displayPot));
    row.put("displayPotRake", round2(rake));
    row.put("rake", round2(rake));
    row.put("realPotRake", round2(rake));
    row.put("totalRealIncome", round2(realIncome));
    row.put("income", round2(realIncome));
    row.put("winnerPayout", round2(winnerPayout));
    row.put("platformProfit", platformProfit);
    row.put("profit", platformProfit);
    row.put("currency", "INR");
    row.put("operatorId", "default");
    row.put("operatorName", "Ludo King");
    row.put(
        "finishedAt",
        rows.stream()
            .map(MatchEconomyEntry::getUpdatedAt)
            .filter(t -> t != null)
            .max(Comparator.naturalOrder())
            .orElse(room != null ? room.getEndedAt() : null)
    );
    row.put("realPlayers", realPlayers);
    row.put("botPlayers", botPlayers);

    if (!includePlayers) {
      List<Map<String, Object>> light = new ArrayList<>();
      for (MatchEconomyEntry e : rows) {
        if (MatchEconomyEntry.REFUNDED.equals(e.getStatus())) {
          continue;
        }
        Map<String, Object> pr = new LinkedHashMap<>();
        double bet = e.getEntryAmount() > 0 ? e.getEntryAmount() : entryFee;
        double payout = e.getSettleAmount();
        if (payout <= 0 && room != null && room.getWinnerId() != null
            && room.getWinnerId().equals(e.getUserId()) && !winnerBot) {
          payout = winnerPayout;
        }
        pr.put("userId", e.getUserId());
        RoomPlayer rp = room != null
            ? room.getPlayers().stream()
                .filter(p -> e.getUserId() != null && e.getUserId().equals(p.getUserId()))
                .findFirst().orElse(null)
            : null;
        pr.put("username", rp != null ? rp.getUsername() : e.getUserId());
        pr.put("bet", bet);
        pr.put("payout", payout);
        pr.put("isBot", false);
        light.add(pr);
      }
      if (room != null) {
        for (RoomPlayer p : room.getPlayers()) {
          if (!p.isBot()) {
            continue;
          }
          Map<String, Object> pr = new LinkedHashMap<>();
          pr.put("userId", p.getUserId());
          pr.put("username", p.getUsername());
          pr.put("bet", 0);
          pr.put("payout", 0);
          pr.put("isBot", true);
          light.add(pr);
        }
      }
      row.put("players", light);
      return row;
    }

    Map<String, RoomPlayer> byUser = new HashMap<>();
    if (room != null) {
      for (RoomPlayer p : room.getPlayers()) {
        if (p.getUserId() != null) {
          byUser.put(p.getUserId(), p);
        }
      }
    }

    String winnerId = room != null ? room.getWinnerId() : null;
    List<Map<String, Object>> players = new ArrayList<>();
    Set<String> covered = new HashSet<>();
    for (MatchEconomyEntry e : rows) {
      covered.add(e.getUserId());
      RoomPlayer rp = byUser.get(e.getUserId());
      boolean isWinner = winnerId != null && winnerId.equals(e.getUserId());
      double bet = e.getEntryAmount() > 0 ? e.getEntryAmount() : entryFee;
      double payout = e.getSettleAmount();
      if (payout <= 0 && isWinner && !winnerBot) {
        payout = winnerPayout;
      }
      Map<String, Object> pr = new LinkedHashMap<>();
      pr.put("userId", e.getUserId());
      pr.put(
          "username",
          displayName(e.getUserId(), rp != null ? rp.getUsername() : null, nameCache)
      );
      pr.put("bet", bet);
      pr.put("betAmount", bet);
      pr.put("payout", payout);
      pr.put("winAmount", payout);
      pr.put("pnl", round2(payout - bet));
      pr.put("profitLoss", round2(payout - bet));
      pr.put("isBot", false);
      pr.put("operatorId", "default");
      players.add(pr);
    }
    if (room != null) {
      for (RoomPlayer p : room.getPlayers()) {
        if (!p.isBot() || covered.contains(p.getUserId())) {
          continue;
        }
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("userId", p.getUserId());
        pr.put("username", p.getUsername() != null ? p.getUsername() : "Bot");
        pr.put("bet", 0);
        pr.put("betAmount", 0);
        pr.put("payout", 0);
        pr.put("winAmount", 0);
        pr.put("pnl", 0);
        pr.put("profitLoss", 0);
        pr.put("isBot", true);
        pr.put("operatorId", "default");
        players.add(pr);
      }
    }
    row.put("players", players);
    return row;
  }

  private Map<String, Object> gameRowFromRoom(
      Room room,
      List<MatchEconomyEntry> rows,
      boolean includePlayers,
      Map<String, String> nameCache
  ) {
    MatchEconomyService.SettlementMath math = matchEconomy.computeSettlement(room, rows);
    int bots = (int) room.getPlayers().stream().filter(RoomPlayer::isBot).count();
    int humans = room.getPlayers().size() - bots;
    int playerCount = Math.max(room.getMaxPlayers(), room.getPlayers().size());

    boolean winnerBot = isWinnerBot(room);
    double realIncome = math.realIncome() > 0
        ? math.realIncome()
        : round2(math.seatFee() * humans);
    double winnerPayoutRaw = winnerBot ? 0 : math.winnerPayout();
    if (math.seatFee() <= 0 && realIncome <= 0) {
      winnerPayoutRaw = 0;
    }
    final double winnerPayout = winnerPayoutRaw;
    double platformProfit = round2(realIncome - winnerPayout);

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", room.getId());
    row.put("roomId", room.getId());
    row.put("roundId", room.getId());
    row.put("roomCode", room.getRoomCode());
    row.put("mode", playerCount + "P");
    row.put("playerCount", playerCount);
    row.put("entryFee", round2(math.seatFee() > 0 ? math.seatFee() : room.getEntryFee()));
    row.put("pot", round2(math.displayPot()));
    row.put("displayPot", round2(math.displayPot()));
    row.put("displayPotRake", round2(math.rake()));
    row.put("rake", round2(math.rake()));
    row.put("realPotRake", round2(math.rake()));
    row.put("totalRealIncome", round2(realIncome));
    row.put("income", round2(realIncome));
    row.put("winnerPayout", round2(winnerPayout));
    row.put("platformProfit", platformProfit);
    row.put("profit", platformProfit);
    row.put("currency", "INR");
    row.put("operatorId", "default");
    row.put("operatorName", "Ludo King");
    row.put("finishedAt", room.getEndedAt() != null ? room.getEndedAt() : room.getStartedAt());
    row.put("realPlayers", humans);
    row.put("botPlayers", bots);

    if (!includePlayers) {
      // Lightweight seats for user aggregation when needed
      row.put(
          "players",
          room.getPlayers().stream().map(p -> {
            Map<String, Object> pr = new LinkedHashMap<>();
            boolean isWinner = room.getWinnerId() != null
                && room.getWinnerId().equals(p.getUserId());
            double bet = p.isBot() ? 0 : math.seatFee();
            double payout = (!p.isBot() && isWinner && !winnerBot) ? winnerPayout : 0;
            pr.put("userId", p.getUserId());
            pr.put("username", p.getUsername());
            pr.put("bet", bet);
            pr.put("payout", payout);
            pr.put("isBot", p.isBot());
            return pr;
          }).toList()
      );
      return row;
    }

    String winnerId = room.getWinnerId();
    row.put(
        "players",
        room.getPlayers().stream().map(p -> {
          Map<String, Object> pr = new LinkedHashMap<>();
          boolean isWinner = winnerId != null && winnerId.equals(p.getUserId());
          double bet = p.isBot() ? 0 : math.seatFee();
          double payout = (!p.isBot() && isWinner && !winnerBot) ? winnerPayout : 0;
          pr.put("userId", p.getUserId());
          pr.put(
              "username",
              p.isBot()
                  ? (p.getUsername() != null ? p.getUsername() : "Bot")
                  : displayName(p.getUserId(), p.getUsername(), nameCache)
          );
          pr.put("bet", bet);
          pr.put("betAmount", bet);
          pr.put("payout", payout);
          pr.put("winAmount", payout);
          pr.put("pnl", p.isBot() ? 0 : round2(payout - bet));
          pr.put("profitLoss", p.isBot() ? 0 : round2(payout - bet));
          pr.put("isBot", p.isBot());
          pr.put("operatorId", "default");
          return pr;
        }).toList()
    );
    return row;
  }

  private static boolean isWinnerBot(Room room) {
    String winnerId = room.getWinnerId();
    if (winnerId == null || winnerId.isBlank()) {
      // Fallback: parse snapshot winnerSeat if present
      String json = room.getLiveSnapshotJson();
      if (json != null) {
        int idx = json.indexOf("\"winnerSeat\":");
        if (idx >= 0) {
          try {
            String rest = json.substring(idx + "\"winnerSeat\":".length()).trim();
            int end = 0;
            while (end < rest.length() && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '-')) {
              end++;
            }
            if (end > 0) {
              int seat = Integer.parseInt(rest.substring(0, end));
              if (seat >= 0 && seat < room.getPlayers().size()) {
                return room.getPlayers().get(seat).isBot();
              }
            }
          } catch (Exception ignored) {
            // fall through
          }
        }
      }
      return false;
    }
    Optional<RoomPlayer> w = room.getPlayers().stream()
        .filter(p -> winnerId.equals(p.getUserId()))
        .findFirst();
    return w.map(RoomPlayer::isBot).orElse(winnerId.startsWith("bot-"));
  }

  private static boolean matchesPlayerFilter(Map<String, Object> row, Integer players) {
    if (players == null || players <= 0) {
      return true;
    }
    return (int) number(row.get("playerCount"), 0) == players;
  }

  private static boolean roomLooksFinished(Room room) {
    if (room.getEndedAt() != null) {
      return true;
    }
    String json = room.getLiveSnapshotJson();
    return json != null && json.contains("\"phase\":\"FINISHED\"");
  }

  private static Map<String, List<MatchEconomyEntry>> groupByMatch(List<MatchEconomyEntry> all) {
    return all.stream().collect(Collectors.groupingBy(MatchEconomyEntry::getMatchId));
  }

  private static boolean isSettledMatch(List<MatchEconomyEntry> rows) {
    return rows.stream().anyMatch(e -> MatchEconomyEntry.SETTLED.equals(e.getStatus()));
  }

  private static boolean unknownOperator(String operatorId) {
    return operatorId != null && !operatorId.isBlank() && !"default".equals(operatorId);
  }

  private static Map<String, Object> emptySummary() {
    Map<String, Object> empty = new LinkedHashMap<>();
    empty.put("currency", "INR");
    empty.put("totalGames", 0);
    empty.put("totalIncome", 0);
    empty.put("totalRealIncome", 0);
    empty.put("totalPayouts", 0);
    empty.put("platformProfit", 0);
    empty.put("profit", 0);
    empty.put("loss", 0);
    empty.put("totalLoss", 0);
    empty.put("totalUsers", 0);
    empty.put("operators", List.of());
    return empty;
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

  private static double number(Object v, double fallback) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    return fallback;
  }

  private static boolean truthy(Object v) {
    if (v instanceof Boolean b) {
      return b;
    }
    return false;
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }
}
