package org.sonar.sonarvalidator_backend.extension;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class StrategyRegistry<T> {
    private final List<T> strategies;
    private final ToIntFunction<T> priority;

    public StrategyRegistry(List<T> strategies, ToIntFunction<T> priority) {
        this.strategies = List.copyOf(strategies);
        this.priority = priority;
    }

    public Optional<T> select(Predicate<T> supports) {
        T selected = null;
        int selectedPriority = Integer.MIN_VALUE;
        boolean ambiguous = false;
        for (final T strategy : strategies) {
            if (!supports.test(strategy)) continue;
            final int candidatePriority = priority.applyAsInt(strategy);
            if (selected == null || candidatePriority > selectedPriority) {
                selected = strategy;
                selectedPriority = candidatePriority;
                ambiguous = false;
            } else if (candidatePriority == selectedPriority) {
                ambiguous = true;
            }
        }
        if (ambiguous) throw new IllegalStateException("ambiguous strategies at priority " + selectedPriority);
        return Optional.ofNullable(selected);
    }
}
