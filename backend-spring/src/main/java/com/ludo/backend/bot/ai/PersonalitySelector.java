package com.ludo.backend.bot.ai;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/** Assigns a {@link BotPersonality} per HARD bot seat. */
@Component
public class PersonalitySelector {

  private final PersonalityConfig config;

  public PersonalitySelector(PersonalityConfig config) {
    this.config = config;
  }

  public BotPersonality select() {
    if (!config.randomMode()) {
      return BotPersonality.fromConfig(config.defaultPersonality());
    }
    BotPersonality[] all = BotPersonality.values();
    return all[ThreadLocalRandom.current().nextInt(all.length)];
  }

  public BotPersonality selectFixed(String name) {
    return BotPersonality.fromConfig(name);
  }
}
