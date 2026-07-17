// config/generate/schema/RuleParamSchema.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.Collections;
import java.util.List;

/**
 * Schema completă a parametrilor pentru un tip de regulă.
 * Wizard-ul o folosește pentru a randa dinamic formularul corect.
 *
 * ruleType  → tipul de regulă pentru care se aplică schema
 * fields    → lista ordonată de câmpuri pe care wizard-ul le afișează
 */
public final class RuleParamSchema {

    private final RuleType       ruleType;
    private final List<FieldSpec> fields;

    public RuleParamSchema(RuleType ruleType, List<FieldSpec> fields) {
        this.ruleType = ruleType;
        this.fields   = Collections.unmodifiableList(fields);
    }

    public RuleType        ruleType() { return ruleType; }
    public List<FieldSpec> fields()   { return fields; }

    /** Shortcut: returnează doar câmpurile obligatorii. */
    public List<FieldSpec> requiredFields() {
        return fields.stream()
                .filter(FieldSpec::required)
                .collect(java.util.stream.Collectors.toList());
    }
}
