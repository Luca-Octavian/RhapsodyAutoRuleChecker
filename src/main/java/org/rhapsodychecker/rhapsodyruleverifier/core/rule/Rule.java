package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;

import java.util.Map;
import java.util.Optional;

public interface Rule {

    String id();

    default String title() {
        return id();
    }

    void configure(Map<String, Object> params);

    boolean appliesTo(ElementRecord element, EvaluationContext context);

    RuleResult evaluate(ElementRecord element, EvaluationContext context);

    /**
     * Optionally suggest an auto-fix action for a failing element.
     * Rules that support auto-fix override this; others return empty.
     */
    default Optional<FixAction> suggestFix(ElementRecord element, EvaluationContext context) {
        return Optional.empty();
    }
}
