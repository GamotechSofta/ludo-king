package com.ludo.backend.bot.ai;

/** Assigned personality + effective weights for one HARD bot seat. */
public final class PersonalityProfile {

  private final BotPersonality personality;
  private final BehaviorWeights baseWeights;
  private final BehaviorWeights effectiveWeights;
  private final String evolutionLabel;
  private final boolean enabled;

  public PersonalityProfile(
      BotPersonality personality,
      BehaviorWeights baseWeights,
      BehaviorWeights effectiveWeights,
      String evolutionLabel,
      boolean enabled
  ) {
    this.personality = personality == null ? BotPersonality.BALANCED : personality;
    this.baseWeights = baseWeights == null ? BehaviorWeights.neutral() : baseWeights;
    this.effectiveWeights =
        effectiveWeights == null ? this.baseWeights : effectiveWeights;
    this.evolutionLabel = evolutionLabel == null ? "" : evolutionLabel;
    this.enabled = enabled;
  }

  public static PersonalityProfile disabled() {
    return new PersonalityProfile(
        BotPersonality.BALANCED,
        BehaviorWeights.neutral(),
        BehaviorWeights.neutral(),
        "disabled",
        false);
  }

  public BotPersonality personality() {
    return personality;
  }

  public BehaviorWeights baseWeights() {
    return baseWeights;
  }

  public BehaviorWeights weights() {
    return effectiveWeights;
  }

  public String evolutionLabel() {
    return evolutionLabel;
  }

  public boolean enabled() {
    return enabled;
  }

  public String debugLine() {
    BehaviorWeights w = effectiveWeights;
    return "Bot Personality "
        + personality.displayName()
        + (evolutionLabel.isEmpty() ? "" : " [" + evolutionLabel + "]")
        + " Capture Weight "
        + w.displayCapture()
        + " Escape Weight "
        + w.displayEscape()
        + " Home Weight "
        + w.displayHome();
  }
}
