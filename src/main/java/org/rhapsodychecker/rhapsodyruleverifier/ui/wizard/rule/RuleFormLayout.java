package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;

import javax.swing.*;
import java.awt.*;

/**
 * Shared GridBag form layout conventions for rule editor components.
 */
public final class RuleFormLayout {

    private RuleFormLayout() {}

    public static GridBagConstraints constraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 10, 4, 10);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        return constraints;
    }

    public static void addRow(JPanel panel, GridBagConstraints constraints,
                              int row, String label, String helpKey,
                              JComponent field) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        constraints.gridwidth = 1;
        panel.add(HelpIcon.labelWithHelp(label + ":", helpKey), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
    }

    public static void addFullWidth(JPanel panel, GridBagConstraints constraints,
                                    int row, JComponent component) {
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        panel.add(component, constraints);
    }
}