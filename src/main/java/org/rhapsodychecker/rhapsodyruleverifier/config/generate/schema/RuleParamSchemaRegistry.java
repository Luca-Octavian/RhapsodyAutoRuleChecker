// config/generate/schema/RuleParamSchemaRegistry.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/**
 * Registru singleton al schemelor de parametri per tip de regulă.
 *
 * Wizard-ul apelează getSchema(ruleType) și primește lista de câmpuri
 * pe care trebuie să le afișeze utilizatorului.
 *
 * Schemele reflectă exact parametrii acceptați de implementările Rule:
 *   RequiredValueRule, RequiredStereotypeRule, RequiredStereotypeOneOfRule,
 *   RelationExistsRule, OwnerStereotypeConstraintRule, NamingPatternRule,
 *   FlowPropertyConstraintRule.
 */
public final class RuleParamSchemaRegistry {

    private static final RuleParamSchemaRegistry INSTANCE = new RuleParamSchemaRegistry();

    private final Map<RuleType, RuleParamSchema> schemas;

    private RuleParamSchemaRegistry() {
        schemas = new EnumMap<>(RuleType.class);
        schemas.put(RuleType.REQUIRED_VALUE,             buildRequiredValueSchema());
        schemas.put(RuleType.REQUIRED_STEREOTYPE,        buildRequiredStereotypeSchema());
        schemas.put(RuleType.REQUIRED_STEREOTYPE_ONE_OF, buildRequiredStereotypeOneOfSchema());
        schemas.put(RuleType.RELATION_EXISTS,            buildRelationExistsSchema());
        schemas.put(RuleType.OWNER_STEREOTYPE_CONSTRAINT, buildOwnerStereotypeConstraintSchema());
        schemas.put(RuleType.NAMING_PATTERN,             buildNamingPatternSchema());
        schemas.put(RuleType.FLOW_PROPERTY_CONSTRAINT,   buildFlowPropertyConstraintSchema());
    }

    public static RuleParamSchemaRegistry getInstance() { return INSTANCE; }

    public RuleParamSchema getSchema(RuleType ruleType) {
        RuleParamSchema schema = schemas.get(ruleType);
        if (schema == null) {
            throw new IllegalArgumentException("No schema registered for rule type: " + ruleType);
        }
        return schema;
    }

    // -------------------------------------------------------------------------
    // RequiredValue
    // -------------------------------------------------------------------------

    private RuleParamSchema buildRequiredValueSchema() {
        return new RuleParamSchema(RuleType.REQUIRED_VALUE, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("nonEmpty")
                        .label("Value must not be empty")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("true")
                        .build(),

                FieldSpec.builder()
                        .paramKey("passIfAbsent")
                        .label("Pass if value is absent (do not fail missing)")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("false")
                        .build(),

                FieldSpec.builder()
                        .paramKey("minLength")
                        .label("Minimum length (characters)")
                        .fieldType(FieldType.NUMBER)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("maxLength")
                        .label("Maximum length (characters)")
                        .fieldType(FieldType.NUMBER)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("operator")
                        .label("Comparison operator")
                        .fieldType(FieldType.DROPDOWN)
                        .required(false)
                        .fixedOptions(Arrays.asList("eq", "neq", "gt", "gte", "lt", "lte",
                                              "in", "not_in", "between", "matches"))
                        .build(),

                FieldSpec.builder()
                        .paramKey("value")
                        .label("Comparison value (for eq/neq/gt/gte/lt/lte)")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("min")
                        .label("Range minimum (for between)")
                        .fieldType(FieldType.NUMBER)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("max")
                        .label("Range maximum (for between)")
                        .fieldType(FieldType.NUMBER)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("values")
                        .label("Allowed values (for in/not_in) — detected from model")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(false)
                        .suggestionsSource(FieldSpec.SuggestionsSource.TAG_VALUES)
                        .build(),

                FieldSpec.builder()
                        .paramKey("pattern")
                        .label("Regex pattern (for matches)")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // RequiredStereotype
    // -------------------------------------------------------------------------

    private RuleParamSchema buildRequiredStereotypeSchema() {
        return new RuleParamSchema(RuleType.REQUIRED_STEREOTYPE, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("requiredStereotypes")
                        .label("Required stereotypes (ALL must be present)")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(true)
                        .suggestionsSource(FieldSpec.SuggestionsSource.STEREOTYPES)
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // RequiredStereotypeOneOf
    // -------------------------------------------------------------------------

    private RuleParamSchema buildRequiredStereotypeOneOfSchema() {
        return new RuleParamSchema(RuleType.REQUIRED_STEREOTYPE_ONE_OF, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("anyOf")
                        .label("Allowed stereotypes (at least ONE must be present)")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(true)
                        .suggestionsSource(FieldSpec.SuggestionsSource.STEREOTYPES)
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // RelationExists
    // -------------------------------------------------------------------------

    private RuleParamSchema buildRelationExistsSchema() {
        return new RuleParamSchema(RuleType.RELATION_EXISTS, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("relationKind")
                        .label("Relation kind")
                        .fieldType(FieldType.DROPDOWN)
                        .required(false)
                        .fixedOptions(Arrays.asList("any", "dependency", "association",
                                              "generalization", "realization"))
                        .defaultValue("dependency")
                        .build(),

                FieldSpec.builder()
                        .paramKey("direction")
                        .label("Relation direction")
                        .fieldType(FieldType.DROPDOWN)
                        .required(false)
                        .fixedOptions(Arrays.asList("any", "outgoing", "incoming"))
                        .defaultValue("any")
                        .build(),

                FieldSpec.builder()
                        .paramKey("relationStereotypes")
                        .label("Relation stereotypes (ex: satisfy, refine)")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(false)
                        .suggestionsSource(FieldSpec.SuggestionsSource.STEREOTYPES)
                        .build(),

                FieldSpec.builder()
                        .paramKey("targetSet")
                        .label("Target element set ID (optional)")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("operator")
                        .label("Count operator")
                        .fieldType(FieldType.DROPDOWN)
                        .required(false)
                        .fixedOptions(Arrays.asList("eq", "neq", "gt", "gte", "lt", "lte"))
                        .defaultValue("gte")
                        .build(),

                FieldSpec.builder()
                        .paramKey("value")
                        .label("Expected relation count")
                        .fieldType(FieldType.NUMBER)
                        .required(true)
                        .defaultValue("1")
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // OwnerStereotypeConstraint
    // -------------------------------------------------------------------------

    private RuleParamSchema buildOwnerStereotypeConstraintSchema() {
        return new RuleParamSchema(RuleType.OWNER_STEREOTYPE_CONSTRAINT, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("ownerStereotype")
                        .label("Owner stereotype (element applies only if owner has this)")
                        .fieldType(FieldType.SINGLE_SELECT)
                        .required(true)
                        .suggestionsSource(FieldSpec.SuggestionsSource.STEREOTYPES)
                        .build(),

                FieldSpec.builder()
                        .paramKey("allowedKinds")
                        .label("Allowed element kinds under that owner")
                        .fieldType(FieldType.KIND_SELECT)
                        .required(true)
                        .suggestionsSource(FieldSpec.SuggestionsSource.ELEMENT_KINDS)
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // NamingPattern
    // -------------------------------------------------------------------------

    private RuleParamSchema buildNamingPatternSchema() {
        return new RuleParamSchema(RuleType.NAMING_PATTERN, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("startsWith")
                        .label("Name must start with")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("endsWith")
                        .label("Name must end with")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("contains")
                        .label("Name must contain")
                        .fieldType(FieldType.TEXT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("caseSensitive")
                        .label("Case-sensitive matching")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("true")
                        .build()
        ));
    }

    // -------------------------------------------------------------------------
    // FlowPropertyConstraint
    // -------------------------------------------------------------------------

    private RuleParamSchema buildFlowPropertyConstraintSchema() {
        return new RuleParamSchema(RuleType.FLOW_PROPERTY_CONSTRAINT, Arrays.asList(

                FieldSpec.builder()
                        .paramKey("type.required")
                        .label("Type is required")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("false")
                        .build(),

                FieldSpec.builder()
                        .paramKey("type.allowed")
                        .label("Allowed type names")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(false)
                        .build(),

                FieldSpec.builder()
                        .paramKey("initialValue.required")
                        .label("Initial value is required")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("false")
                        .build(),

                FieldSpec.builder()
                        .paramKey("initialValue.mustBeEmpty")
                        .label("Initial value must be empty")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("false")
                        .build(),

                FieldSpec.builder()
                        .paramKey("direction.required")
                        .label("Direction is required")
                        .fieldType(FieldType.BOOLEAN)
                        .required(false)
                        .defaultValue("false")
                        .build(),

                FieldSpec.builder()
                        .paramKey("direction.allowed")
                        .label("Allowed direction values")
                        .fieldType(FieldType.MULTI_SELECT)
                        .required(false)
                        .fixedOptions(Arrays.asList("In", "Out", "Bidirectional"))
                        .build()
        ));
    }
}