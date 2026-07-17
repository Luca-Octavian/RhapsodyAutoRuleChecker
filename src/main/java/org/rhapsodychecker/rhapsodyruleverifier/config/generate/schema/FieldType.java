// config/generate/schema/FieldType.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema;

/**
 * Tipul de input pe care wizard-ul trebuie să îl randeze pentru un câmp.
 *
 * TEXT          → input text liber (ex: pattern regex, mesaj custom)
 * NUMBER        → input numeric natural (ex: minLength, minValue)
 * BOOLEAN       → checkbox (ex: nonEmpty, passIfAbsent)
 * DROPDOWN      → selecție dintr-o listă fixă predefinită (ex: operator, direction)
 * MULTI_SELECT  → checkboxuri multiple din sugestii detectate (ex: anyOf, requiredStereotypes)
 * SINGLE_SELECT → radio/dropdown din sugestii detectate (ex: ownerStereotype)
 * KIND_SELECT   → selecție din ElementKind enum (ex: allowedKinds)
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
