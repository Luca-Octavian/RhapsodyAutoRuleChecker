package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import java.util.Map;
import java.util.Optional;

/**
 * Immutable outcome of evaluating a rule on a single element.
 * Implementations should be simple data holders.
 */
public interface RuleResult {
    RuleStatus status();
    String ruleId();
    String elementGuid();
    String message();                    // human-readable message (for FAIL typically)
    Optional<Map<String, Object>> details(); // optional structured details (for export/diagnostics)
}