package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Panel for UniqueNameRule — no parameters needed.
 * Shows an informational label explaining the rule's behaviour.
 */
public final class UniqueNamePanel implements RuleParamPanel {

    private final JPanel root;

    public UniqueNamePanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        JLabel info = new JLabel(
                "<html><body style='width:350px'>"
                + "<b>No parameters required.</b><br><br>"
                + "This rule flags elements that share the same name under the same owner. "
                + "All sibling elements with duplicate names will be reported as violations."
                + "</body></html>");
        info.setToolTipText("Detects sibling elements with identical names under the same parent element.");
        root.add(info, gbc);
    }

    @Override public JComponent component() { return root; }

    @Override public String validationMessage() { return null; }
    @Override public boolean usesElementSet() { return false; }

    @Override public JTextField liveValidationField() { return null; }

    @Override
    public Map<String, Object> buildParams() {
        return Collections.emptyMap();
    }

    @Override
    public void prefill(Map<String, Object> params) {
        // Nothing to prefill
    }
}