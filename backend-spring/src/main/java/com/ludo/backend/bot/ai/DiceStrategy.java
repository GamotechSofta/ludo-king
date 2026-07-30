package com.ludo.backend.bot.ai;

import com.ludo.backend.bot.BotAiMode;
import com.ludo.backend.bot.BotGamePhase;
import com.ludo.backend.bot.BotMatchAnalysis;
import org.springframework.stereotype.Component;

/** Phase / mode / lead-lag assist-rate policy for Smart Dice. */
@Component
public class DiceStrategy {

  private final SmartDiceConfig config;

  public DiceStrategy(SmartDiceConfig config) {
    this.config = config;
  }

  /**
   * Probability that Smart Dice intervenes this roll (else fair RNG).
   * Capped at {@link SmartDiceConfig#maxAssist()}.
   * When {@link DifficultyProfile} is present, its assist rate is used (also capped).
   */
  public double assistRate(BotMatchAnalysis analysis) {
    return assistRate(analysis, null);
  }

  public double assistRate(BotMatchAnalysis analysis, DifficultyProfile adaptive) {
    if (adaptive != null && adaptive.enabled()) {
      return Math.min(config.maxAssist(), Math.max(0.0, adaptive.diceAssistRate()));
    }
    if (analysis == null) {
      return config.mid();
    }
    double base =
        switch (analysis.phase) {
          case EARLY -> config.early();
          case END -> config.end();
          default -> config.mid();
        };
    if (analysis.botBehind) {
      base = Math.max(base, config.losing());
    } else if (analysis.botIsLeader) {
      base = Math.min(base, config.winning());
    }
    base *= modeMultiplier(analysis.mode);
    return Math.min(config.maxAssist(), Math.max(0.0, base));
  }

  private double modeMultiplier(BotAiMode mode) {
    if (mode == null) {
      return 1.0;
    }
    return switch (mode) {
      case MODE_1 -> config.mode1Mult();
      case MODE_2 -> config.mode2Mult();
      case MODE_3 -> config.mode3Mult();
      case MODE_4 -> config.mode4Mult();
      default -> 0.5;
    };
  }

  public boolean preferHomeExact(BotGamePhase phase) {
    return phase == BotGamePhase.END;
  }

  public boolean preferOpenPawns(BotGamePhase phase) {
    return phase == BotGamePhase.EARLY;
  }
}
