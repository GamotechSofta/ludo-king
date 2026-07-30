package com.ludo.backend.bot.ai;

import static com.ludo.backend.game.BoardConstants.JAIL;
import static com.ludo.backend.game.BoardConstants.isExit;
import static com.ludo.backend.game.BoardConstants.isHome;
import static com.ludo.backend.game.BoardConstants.isJail;
import static com.ludo.backend.game.BoardConstants.isSafe;

import com.ludo.backend.bot.BotBoardMath;
import com.ludo.backend.bot.BotMatchAnalysis;
import com.ludo.backend.game.GameSnapshot;
import com.ludo.backend.game.LudoColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Scans every seat/pawn once per bot turn into {@link ScannedPawn} records.
 */
@Component
public class BoardScanner {

  public List<ScannedPawn> scan(GameSnapshot snap, BotMatchAnalysis analysis) {
    List<ScannedPawn> out = new ArrayList<>(16);
    if (snap == null || snap.getSeatColors() == null || snap.getTokenPositions() == null) {
      return out;
    }
    List<String> colors = snap.getSeatColors();
    Map<String, List<Integer>> all = snap.getTokenPositions();
    boolean[] isBot = analysis != null ? analysis.isBot : snap.getIsBot();
    int leader = analysis != null ? analysis.leaderSeat : -1;

    for (int s = 0; s < colors.size(); s++) {
      String name = colors.get(s);
      LudoColor color = BotBoardMath.parseColor(name);
      List<Integer> positions = all.get(name);
      if (color == null || positions == null) {
        continue;
      }
      boolean bot = isBot != null && s < isBot.length && isBot[s];
      boolean isLeader = s == leader;
      for (int p = 0; p < positions.size(); p++) {
        int pos = positions.get(p) == null ? JAIL : positions.get(p);
        int rem = BotBoardMath.remainingDistance(color, pos);
        if (rem == Integer.MAX_VALUE) {
          rem = isHome(pos) ? 0 : BotBoardMath.MAX_PAWN_PROGRESS;
        }
        out.add(
            new ScannedPawn(
                s,
                p,
                color,
                pos,
                BotBoardMath.pawnProgress(color, pos),
                rem,
                isSafe(pos),
                isJail(pos),
                isHome(pos),
                isExit(pos),
                bot,
                isLeader));
      }
    }
    return out;
  }
}
