// config/generate/schema/RuleParamSchema.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.Collections;
import java.util.List;

/**
 * Complete parameter schema for a rule type.
 * The wizard uses it to dynamically render the correct form.
 *
 * ruleType - the rule type this schema applies to
 * fields   - ordered list of fields the wizard displays
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

    /** Shortcut: returns only the required fields. */
    public List<FieldSpec> requiredFields() {
        return fields.stream()
                .filter(FieldSpec::required)
                .collect(java.util.stream.Collectors.toList());
    }
}
