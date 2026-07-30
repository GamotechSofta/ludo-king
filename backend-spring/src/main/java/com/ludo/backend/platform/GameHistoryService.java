package com.ludo.backend.platform;

import com.ludo.backend.platform.wallet.MatchEconomyEntry;
import com.ludo.backend.platform.wallet.MatchEconomyRepository;
import com.ludo.backend.room.Room;
import com.ludo.backend.room.RoomPlayer;
import com.ludo.backend.room.RoomRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Player-facing completed-match history (public room + economy data only). */
@Service
public class GameHistoryService {

  private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.systemDefault());
  private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("hh:mm a").withZone(ZoneId.systemDefault());

  private final RoomRepository roomRepository;
  private final MatchEconomyRepository economyRepository;

  public GameHistoryService(
      RoomRepository roomRepository, MatchEconomyRepository economyRepository
  ) {
    this.roomRepository = roomRepository;
    this.economyRepository = economyRepository;
  }

  public List<Map<String, Object>> historyForUser(String userId, int limit) {
    if (userId == null || userId.isBlank()) {
      return List.of();
    }
    int max = Math.max(1, Math.min(100, limit));
    List<Room> rooms = roomRepository.findCompletedRoomsForUser(userId.trim());
    Map<String, MatchEconomyEntry> economyByMatch = new HashMap<>();
    for (MatchEconomyEntry e : economyRepository.findByUserId(userId.trim())) {
      if (e.getMatchId() != null) {
        economyByMatch.put(e.getMatchId(), e);
      }
    }

    List<Map<String, Object>> out = new ArrayList<>();
    for (Room room : rooms) {
      out.add(toRow(room, userId.trim(), economyByMatch.get(room.getId())));
    }
    out.sort(
        Comparator.comparing(
            (Map<String, Object> m) -> (String) m.getOrDefault("endedAt", ""),
            Comparator.nullsLast(Comparator.reverseOrder())));
    if (out.size() > max) {
      return out.subList(0, max);
    }
    return out;
  }

  private Map<String, Object> toRow(Room room, String userId, MatchEconomyEntry economy) {
    Instant when =
        room.getEndedAt() != null
            ? room.getEndedAt()
            : (room.getStartedAt() != null ? room.getStartedAt() : room.getCreatedAt());

    boolean won =
        room.getWinnerId() != null && room.getWinnerId().equals(userId);
    String opponent = opponentNames(room, userId);
    String reason = resultReason(room, userId, won);

    double bet =
        economy != null
            ? economy.getEntryAmount()
            : (room.getEntryFee() > 0 ? room.getEntryFee() : 0);
    double winAmount = 0;
    if (economy != null) {
      winAmount = won ? economy.getSettleAmount() : 0;
    } else if (won && room.getEntryFee() > 0) {
      // Fallback estimate when economy row missing
      long humans =
          room.getPlayers() == null
              ? 0
              : room.getPlayers().stream().filter(p -> p != null && !p.isBot()).count();
      winAmount = room.getEntryFee() * Math.max(1, humans);
    }

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("gameId", room.getId());
    row.put("roomCode", room.getRoomCode());
    row.put("gameDate", when != null ? DATE_FMT.format(when) : "-");
    row.put("gameTime", when != null ? TIME_FMT.format(when) : "-");
    row.put("endedAt", when != null ? when.toString() : "");
    row.put("betAmount", bet);
    row.put("winAmount", winAmount);
    row.put("opponentName", opponent);
    row.put("result", won ? "Win" : "Loss");
    row.put("reason", reason);
    return row;
  }

  private static String opponentNames(Room room, String userId) {
    if (room.getPlayers() == null || room.getPlayers().isEmpty()) {
      return "-";
    }
    List<String> names =
        room.getPlayers().stream()
            .filter(p -> p != null && p.getUserId() != null && !p.getUserId().equals(userId))
            .map(p -> {
              String n = p.getUsername();
              if (n == null || n.isBlank()) {
                return p.isBot() ? "Bot" : "Player";
              }
              return p.isBot() ? n + " (Bot)" : n;
            })
            .collect(Collectors.toList());
    return names.isEmpty() ? "-" : String.join(", ", names);
  }

  private static String resultReason(Room room, String userId, boolean won) {
    RoomPlayer self =
        room.getPlayers() == null
            ? null
            : room.getPlayers().stream()
                .filter(p -> p != null && userId.equals(p.getUserId()))
                .findFirst()
                .orElse(null);

    boolean anyOpponentLeft =
        room.getPlayers() != null
            && room.getPlayers().stream()
                .anyMatch(
                    p ->
                        p != null
                            && !userId.equals(p.getUserId())
                            && !p.isBot()
                            && p.getDisconnectedAt() != null
                            && (room.getWinnerId() == null
                                || !p.getUserId().equals(room.getWinnerId())));

    if (won) {
      if (anyOpponentLeft) {
        return "Opponent left";
      }
      return "Finished all pawns";
    }
    if (self != null && self.getDisconnectedAt() != null && !won) {
      return "You left the match";
    }
    if (anyOpponentLeft && room.getWinnerId() != null && !room.getWinnerId().equals(userId)) {
      return "Opponent finished all pawns";
    }
    return "Opponent finished all pawns";
  }
}
