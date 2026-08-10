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
    default JTextField liveValidationField() { return null; }
}