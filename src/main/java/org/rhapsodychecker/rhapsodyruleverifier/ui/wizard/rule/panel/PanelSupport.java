package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class PanelSupport {

    private PanelSupport() {}

    public static CheckboxListField addSelection(
            JPanel container, GridBagConstraints gbc, String label, String helpKey,
            List<String> values, int row, double weight, Runnable onChange) {
        RuleFormLayout.addFullWidth(container, gbc, row,
                HelpIcon.labelWithHelp(label, helpKey));
        CheckboxListField field = new CheckboxListField(values);
        field.addChangeListener(onChange);
        gbc.weighty = weight;
        gbc.fill = GridBagConstraints.BOTH;
        RuleFormLayout.addFullWidth(container, gbc, row + 1, field);
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        return field;
    }

    public static JPanel formPanel() {
        return new JPanel(new GridBagLayout());
    }

    public static void putFirst(Map<String, Object> params, String key,
                                CheckboxListField field) {
        if (!empty(field)) params.put(key, field.getSelectedValues().get(0));
    }

    public static void putInteger(Map<String, Object> params, String key,
                                  JTextField field) {
        String value = field.getText().trim();
        if (!value.isEmpty()) params.put(key, Integer.parseInt(value));
    }

    public static void putText(Map<String, Object> params, String key,
                               JTextField field) {
        String value = field.getText().trim();
        if (!value.isEmpty()) params.put(key, value);
    }

    public static String text(Map<String, Object> params,
                              String key, String fallback) {
        Object value = params.get(key);
        return value != null ? value.toString() : fallback;
    }

    public static void setText(JTextField field, Object value) {
        if (value != null) field.setText(value.toString());
    }

    public static void selectOne(CheckboxListField field, Object value) {
        if (field != null && value != null) {
            field.setSelectedValues(Collections.singletonList(value.toString()));
        }
    }

    public static void selectMany(CheckboxListField field, Object value) {
        if (field == null || value == null) return;
        List<String> values = new ArrayList<String>();
        if (value instanceof Iterable) {
            for (Object item : (Iterable<?>) value) {
                if (item != null) values.add(item.toString());
            }
        } else {
            values.add(value.toString());
        }
        field.setSelectedValues(values);
    }

    public static boolean empty(CheckboxListField field) {
        return field == null || field.getSelectedValues().isEmpty();
    }

    public static void mark(CheckboxListField field) {
        if (field != null) field.setValid(!field.getSelectedValues().isEmpty());
    }
}