// config/generate/WizardParamsCollector.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.FieldSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.RuleParamSchema;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.RuleParamSchemaRegistry;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.*;

/**
 * Validează params colectați din UI față de schema regulii alese.
 *
 * Utilizare:
 *   ValidationResult result = WizardParamsCollector.validate(RuleType.REQUIRED_VALUE, params);
 *   if (result.isValid()) { // construiește RuleRequest }
 *   else { // afișează result.errors() în UI }
 */
public final class WizardParamsCollector {

    private WizardParamsCollector() {}

    public static ValidationResult validate(RuleType ruleType, Map<String, Object> params) {
        RuleParamSchema schema = RuleParamSchemaRegistry.getInstance().getSchema(ruleType);
        List<String> errors = new ArrayList<>();

        for (FieldSpec field : schema.requiredFields()) {
            Object value = params.get(field.paramKey());

            if (isMissing(value)) {
                errors.add("'" + field.label() + "' is required.");
                continue;
            }

            validateFieldValue(field, value, errors);
        }

        // Validări cross-field per tip de regulă
        validateCrossFields(ruleType, params, errors);

        return new ValidationResult(errors);
    }

    // -------------------------------------------------------------------------

    private static boolean isMissing(Object value) {
        if (value == null) return true;
        if (value instanceof String)  return ((String) value).trim().isEmpty();
        if (value instanceof List)    return ((List<?>) value).isEmpty();
        return false;
    }

    private static void validateFieldValue(FieldSpec field, Object value, List<String> errors) {
        switch (field.fieldType()) {
            case NUMBER:
                validateNumber(field, value, errors);
                break;
            case MULTI_SELECT:
            case KIND_SELECT:
                if (!(value instanceof List) || ((List<?>) value).isEmpty()) {
                    errors.add("'" + field.label() + "' must have at least one selection.");
                }
                break;
            default:
                break;
        }
    }

    private static void validateNumber(FieldSpec field, Object value, List<String> errors) {
        try {
            double num = Double.parseDouble(value.toString());
            if (num < 0) {
                errors.add("'" + field.label() + "' must be a non-negative number.");
            }
        } catch (NumberFormatException e) {
            errors.add("'" + field.label() + "' must be a valid number.");
        }
    }

    private interface CrossFieldValidator {
        void validate(Map<String, Object> params, List<String> errors);
    }

    private static final Map<RuleType, CrossFieldValidator> CROSS_FIELD_VALIDATORS
            = new EnumMap<>(RuleType.class);

    static {
        CROSS_FIELD_VALIDATORS.put(RuleType.REQUIRED_VALUE,
                WizardParamsCollector::validateRequiredValueCrossFields);
        CROSS_FIELD_VALIDATORS.put(RuleType.RELATION_EXISTS,
                WizardParamsCollector::validateRelationExistsCrossFields);
    }

    private static void validateCrossFields(
            RuleType ruleType,
            Map<String, Object> params,
            List<String> errors
    ) {
        CrossFieldValidator v = CROSS_FIELD_VALIDATORS.get(ruleType);
        if (v != null) v.validate(params, errors);
    }

    private static void validateRequiredValueCrossFields(
            Map<String, Object> params,
            List<String> errors
    ) {
        String operator = asString(params.get("operator"));
        if (operator == null) return;

        switch (operator) {
            case "in":
            case "not_in":
                if (isMissing(params.get("values"))) {
                    errors.add("Operator '" + operator + "' requires 'values' to be non-empty.");
                }
                break;
            case "between":
                if (isMissing(params.get("min")) || isMissing(params.get("max"))) {
                    errors.add("Operator 'between' requires both 'min' and 'max'.");
                }
                break;
            case "matches":
                if (isMissing(params.get("pattern"))) {
                    errors.add("Operator 'matches' requires a 'pattern'.");
                }
                break;
            case "eq": case "neq": case "gt": case "gte": case "lt": case "lte":
                if (isMissing(params.get("value"))) {
                    errors.add("Operator '" + operator + "' requires a 'value'.");
                }
                break;
            default:
                break;
        }
    }

    private static void validateRelationExistsCrossFields(
            Map<String, Object> params,
            List<String> errors
    ) {
        Object value = params.get("value");
        if (!isMissing(value)) {
            try {
                int count = Integer.parseInt(value.toString());
                if (count < 0) {
                    errors.add("'Expected relation count' must be a non-negative integer.");
                }
            } catch (NumberFormatException e) {
                errors.add("'Expected relation count' must be a valid integer.");
            }
        }
    }

    private static String asString(Object v) {
        return v != null ? v.toString().trim() : null;
    }

    // -------------------------------------------------------------------------

    public static final class ValidationResult {

        private final List<String> errors;

        ValidationResult(List<String> errors) {
            this.errors = Collections.unmodifiableList(errors);
        }

        public boolean     isValid() { return errors.isEmpty(); }
        public List<String> errors() { return errors; }

        @Override
        public String toString() {
            return isValid() ? "ValidationResult{VALID}" : "ValidationResult{errors=" + errors + "}";
        }
    }
}
