package org.rhapsodychecker.rhapsodyruleverifier.core.config;

/**
 * Comparison operators for value constraints in rules.
 */
public enum ComparisonOperator {
    EQ,             // equals
    NEQ,            // not equals
    GT,             // greater than
    GTE,            // greater than or equal
    LT,             // less than
    LTE,            // less than or equal
    BETWEEN,        // inclusive range [min, max]
    IN,             // value is one of a set
    NOT_IN,         // value is not in a set
    MATCHES;        // regex match

    public static ComparisonOperator fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ComparisonOperator must not be null/empty");
        }
        switch (value.trim().toLowerCase()) {
            case "eq":
            case "equals":          return EQ;
            case "neq":
            case "not_equals":      return NEQ;
            case "gt":
            case "greater_than":    return GT;
            case "gte":
            case "greater_or_equal": return GTE;
            case "lt":
            case "less_than":       return LT;
            case "lte":
            case "less_or_equal":   return LTE;
            case "between":
            case "range":           return BETWEEN;
            case "in":
            case "one_of":          return IN;
            case "not_in":          return NOT_IN;
            case "matches":
            case "regex":           return MATCHES;
            default:
                throw new IllegalArgumentException("Unknown ComparisonOperator: '" + value + "'. "
                        + "Allowed: eq, neq, gt, gte, lt, lte, between, in, not_in, matches");
        }
    }
}
