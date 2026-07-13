package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

/**
 * Outcome of a rule evaluation.
 */
public enum RuleStatus {
    PASS,
    FAIL,
    SKIPPED // Not applicable or could not be evaluated (lenient behavior)
}