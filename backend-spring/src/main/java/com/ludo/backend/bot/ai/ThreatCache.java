package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-room turn cache for {@link DangerMap}. Recalculate only when board fingerprint changes.
 */
@Component
public class ThreatCache {

  private final ConcurrentHashMap<String, Cached> byRoom = new ConcurrentHashMap<>();

  private final DangerMapConfig config;
  private final BoardScanner scanner;
  private final ThreatAnalyzer analyzer;

  public ThreatCache(DangerMapConfig config, BoardScanner scanner, ThreatAnalyzer analyzer) {
    this.config = config;
    this.scanner = scanner;
    this.analyzer = analyzer;
  }

  public DangerMap getOrBuild(
      String roomId,
      GameSnapshot snap,
      int botSeat,
      LudoColor botColor,
      BotMatchAnalysis analysis
  ) {
    if (!config.enabled()) {
      return DangerMap.empty(botSeat);
    }
    String fingerprint = fingerprint(snap);
    if (config.cacheDangerMap() && roomId != null) {
      Cached cached = byRoom.get(roomId);
      if (cached != null
          && cached.botSeat == botSeat
          && fingerprint.equals(cached.fingerprint)) {
        return cached.map;
      }
    }
    var pawns = scanner.scan(snap, analysis);
    DangerMap map = analyzer.buildMap(botSeat, botColor, pawns);
    if (config.cacheDangerMap() && roomId != null) {
      byRoom.put(roomId, new Cached(botSeat, fingerprint, map));
    }
    return map;
  }

  public void invalidate(String roomId) {
    if (roomId != null) {
      byRoom.remove(roomId);
    }
  }

  static String fingerprint(GameSnapshot snap) {
    if (snap == null || snap.getTokenPositions() == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder(128);
    sb.append(snap.getCurrentSeatIndex()).append('|');
    if (snap.getSeatColors() != null) {
      for (String c : snap.getSeatColors()) {
        sb.append(c).append(':');
        var pos = snap.getTokenPositions().get(c);
        if (pos != null) {
          sb.append(pos);
        }
        sb.append(';');
      }
    }
    return sb.toString();
  }

  private static final class Cached {
    final int botSeat;
    final String fingerprint;
    final DangerMap map;

    Cached(int botSeat, String fingerprint, DangerMap map) {
      this.botSeat = botSeat;
      this.fingerprint = fingerprint;
      this.map = map;
    }
  }
}
