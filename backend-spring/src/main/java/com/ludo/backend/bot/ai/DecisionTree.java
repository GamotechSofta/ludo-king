package com.ludo.backend.bot.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Root-level Monte Carlo decision tree (one node per legal move). */
public final class DecisionTree {

  private final List<SimulationNode> roots;

  public DecisionTree(List<SimulationNode> roots) {
    this.roots = roots == null ? new ArrayList<>() : new ArrayList<>(roots);
  }

  public List<SimulationNode> roots() {
    return Collections.unmodifiableList(roots);
  }

  public List<SimulationNode> alive() {
    List<SimulationNode> out = new ArrayList<>(roots.size());
    for (SimulationNode n : roots) {
      if (!n.pruned()) {
        out.add(n);
      }
    }
    return out;
  }

  public SimulationNode bestByExpectedValue(ExpectedValueCalculator ev) {
    SimulationNode best = null;
    double bestEv = Double.NEGATIVE_INFINITY;
    for (SimulationNode n : roots) {
      if (n.pruned()) {
        continue;
      }
      double v = ev.expectedValue(n);
      if (v > bestEv) {
        bestEv = v;
        best = n;
      }
    }
    return best;
  }

  public SimulationNode bestByPrior() {
    return roots.stream()
        .filter(n -> !n.pruned())
        .max(Comparator.comparingInt(SimulationNode::priorScore))
        .orElse(null);
  }
}
