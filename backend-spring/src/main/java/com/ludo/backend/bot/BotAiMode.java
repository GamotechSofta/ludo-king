package com.ludo.backend.bot;

/**
 * Supported HARD-bot table shapes for dynamic difficulty.
 *
 * <ul>
 *   <li>{@link #MODE_1} – 2P: 1 Human, 1 Bot (expected ~80–90% bot WR)
 *   <li>{@link #MODE_2} – 4P: 1 Human, 3 Bots (collective ~80%)
 *   <li>{@link #MODE_3} – 4P: 2 Humans, 2 Bots (collective ~60–70%)
 *   <li>{@link #MODE_4} – 4P: 3 Humans, 1 Bot (~35–45% bot WR)
 * </ul>
 */
public enum BotAiMode {
  MODE_1,
  MODE_2,
  MODE_3,
  MODE_4,
  /** Fallback (odd seat mixes / EASY-MEDIUM tables). */
  OTHER
}
