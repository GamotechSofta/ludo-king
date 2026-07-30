package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fully evaluated pawn: value + priority + labels for logging. */
public final class PawnPriority {

  private final int pawnIndex;
  private final int value;
  private final int baseValue;
  private final PawnImportance importance;
  private final PawnState state;
  private final List<String> labels;
  private final boolean escapeNeeded;
  private final boolean neverSacrifice;

  public PawnPriority(
      int pawnIndex,
      int value,
      int baseValue,
      PawnImportance importance,
      PawnState state,
      List<String> labels,
      boolean escapeNeeded,
      boolean neverSacrifice
  ) {
    this.pawnIndex = pawnIndex;
    this.value = value;
    this.baseValue = baseValue;
    this.importance = importance;
    this.state = state;
    this.labels = labels == null ? List.of() : List.copyOf(labels);
    this.escapeNeeded = escapeNeeded;
    this.neverSacrifice = neverSacrifice;
  }

  public int pawnIndex() {
    return pawnIndex;
  }

  public int value() {
    return value;
  }

  public int baseValue() {
    return baseValue;
  }

  public PawnImportance importance() {
    return importance;
  }

  public PawnState state() {
    return state;
  }

  public List<String> labels() {
    return labels;
  }

  public boolean escapeNeeded() {
    return escapeNeeded;
  }

  public boolean neverSacrifice() {
    return neverSacrifice;
  }

  public String debugLine() {
    StringBuilder sb = new StringBuilder(96);
    sb.append("Pawn ").append(pawnIndex).append(" Value ").append(value);
    for (String l : labels) {
      sb.append(' ').append(l);
    }
    sb.append(" Priority ").append(importance);
    if (escapeNeeded) {
      sb.append(" Escape Needed");
    }
    return sb.toString();
  }

  static List<String> mutableLabels() {
    return new ArrayList<>(6);
  }

  static List<String> freeze(List<String> labels) {
    return Collections.unmodifiableList(new ArrayList<>(labels));
  }
}
