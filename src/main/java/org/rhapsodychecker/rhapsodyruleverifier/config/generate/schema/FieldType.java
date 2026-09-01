// config/generate/schema/FieldType.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

/**
 * The type of input control the wizard renders for a field.
 *
 * TEXT          - free text input (e.g. regex pattern, custom message)
 * NUMBER        - natural number input (e.g. minLength, minValue)
 * BOOLEAN       - checkbox (e.g. nonEmpty, passIfAbsent)
 * DROPDOWN      - selection from a predefined fixed list (e.g. operator, direction)
 * MULTI_SELECT  - multiple checkboxes from detected suggestions (e.g. anyOf, requiredStereotypes)
 * SINGLE_SELECT - radio/dropdown from detected suggestions (e.g. ownerStereotype)
 * KIND_SELECT   - selection from the ElementKind enum (e.g. allowedKinds)
 */
public enum FieldType {
    TEXT,
    NUMBER,
    BOOLEAN,
    DROPDOWN,
    MULTI_SELECT,
    SINGLE_SELECT,
    KIND_SELECT
}
