package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.Map;

public interface Rule {

    String id();

    default String title() {
        return id();
    }

    void configure(Map<String, Object> params);

    boolean appliesTo(ElementRecord element, EvaluationContext context);

    RuleResult evaluate(ElementRecord element, EvaluationContext context);
}
