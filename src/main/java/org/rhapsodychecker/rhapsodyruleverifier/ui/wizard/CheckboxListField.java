// ui/wizard/CheckboxListField.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Componenta refolosibila: lista de checkboxes din sugestii detectate in model
 * + un JTextField pentru adaugare manuala de valori custom.
 *
 * Folosita in ElementSetDialog, AliasDialog, RuleDialog oriunde
 * user-ul trebuie sa selecteze valori dintr-o lista cunoscuta din model.
 */
public final class CheckboxListField extends JPanel {

    private final List<JCheckBox> checkBoxes  = new ArrayList<>();
    private final JTextField      customField = new JTextField();
    private final JPanel          checkPanel  = new JPanel();
    private final JLabel          noSuggestionsLabel;

    /**
     * @param suggestions  Lista de valori detectate in model (poate fi goala)
     */
    public CheckboxListField(List<String> suggestions) {
        super(new BorderLayout(4, 4));

        // ── Lista de checkboxes ───────────────────────────────────────────────
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));

        noSuggestionsLabel = new JLabel("  No values detected in model.");
        noSuggestionsLabel.setForeground(Color.GRAY);
        noSuggestionsLabel.setFont(noSuggestionsLabel.getFont().deriveFont(Font.ITALIC, 11f));

        if (suggestions == null || suggestions.isEmpty()) {
            checkPanel.add(noSuggestionsLabel);
        } else {
            for (String suggestion : suggestions) {
                JCheckBox cb = new JCheckBox(suggestion);
                checkBoxes.add(cb);
                checkPanel.add(cb);
            }
        }

        JScrollPane scroll = new JScrollPane(checkPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(300, 120));
        scroll.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        add(scroll, BorderLayout.CENTER);

        // ── Custom field ──────────────────────────────────────────────────────
        JPanel customPanel = new JPanel(new BorderLayout(4, 0));
        JLabel customLabel = new JLabel("Custom:");
        customLabel.setFont(customLabel.getFont().deriveFont(11f));
        customLabel.setForeground(Color.GRAY);
        customField.setToolTipText(
                "Add values not detected in the model (comma separated)");
        customPanel.add(customLabel,  BorderLayout.WEST);
        customPanel.add(customField,  BorderLayout.CENTER);
        add(customPanel, BorderLayout.SOUTH);
    }

    /**
     * Returneaza toate valorile selectate: bifate din lista + custom CSV.
     */
    public List<String> getSelectedValues() {
        List<String> result = new ArrayList<>();

        for (JCheckBox cb : checkBoxes) {
            if (cb.isSelected()) {
                result.add(cb.getText());
            }
        }

        String custom = customField.getText().trim();
        if (!custom.isEmpty()) {
            for (String part : custom.split(",")) {
                String val = part.trim();
                if (!val.isEmpty() && !result.contains(val)) {
                    result.add(val);
                }
            }
        }

        return result;
    }

    /**
     * Pre-populeaza componenta la edit: bifeaza ce exista in lista,
     * restul le pune in custom field.
     */
    public void setSelectedValues(List<String> values) {
        if (values == null || values.isEmpty()) return;

        List<String> custom = new ArrayList<>();

        for (String val : values) {
            boolean found = false;
            for (JCheckBox cb : checkBoxes) {
                if (cb.getText().equals(val)) {
                    cb.setSelected(true);
                    found = true;
                    break;
                }
            }
            if (!found) {
                custom.add(val);
            }
        }

        if (!custom.isEmpty()) {
            customField.setText(String.join(", ", custom));
        }
    }

    /**
     * Reseteaza toate selectiile si custom field-ul.
     */
    public void clearAll() {
        for (JCheckBox cb : checkBoxes) {
            cb.setSelected(false);
        }
        customField.setText("");
    }
}
