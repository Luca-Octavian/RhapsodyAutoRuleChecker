package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import javax.swing.*;
import java.util.Map;

public interface RuleParamPanel {
    JComponent component();
    Map<String, Object> buildParams();
    void prefill(Map<String, Object> params);

    default String validationMessage() { return null; }
    default void updateValidationMarkers() {}
    default boolean usesTargetSpec() { return false; }
    /** Whether this rule type uses an element set (appliesTo). Return false to hide it in the wizard. */
    default boolean usesElementSet() { return true; }
    default JTextField liveValidationField() { return null; }
}