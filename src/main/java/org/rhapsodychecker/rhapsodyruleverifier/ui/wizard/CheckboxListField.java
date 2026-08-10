// ui/wizard/CheckboxListField.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
    private final JTextField      searchField = new JTextField();
    private final JPanel          checkPanel  = new JPanel();
    private final JLabel          noSuggestionsLabel;
    private final JLabel          noMatchesLabel;
    private JScrollPane           scroll;
    private final List<Runnable> changeListeners = new ArrayList<>();

    /**
     * @param suggestions  Lista de valori detectate in model (poate fi goala)
     */
    public CheckboxListField(List<String> suggestions) {
        super(new BorderLayout(4, 4));

        // ── Lista de checkboxes ───────────────────────────────────────────────
        checkPanel.setLayout(new BoxLayout(checkPanel, BoxLayout.Y_AXIS));

        noSuggestionsLabel = new JLabel("  No values detected in model.");
        noSuggestionsLabel.setForeground(mutedTextColor());
        noSuggestionsLabel.setFont(noSuggestionsLabel.getFont().deriveFont(Font.ITALIC, 11f));

        noMatchesLabel = new JLabel("  No matches for this search.");
        noMatchesLabel.setForeground(mutedTextColor());
        noMatchesLabel.setFont(noMatchesLabel.getFont().deriveFont(Font.ITALIC, 11f));
        noMatchesLabel.setVisible(false);

        boolean hasSuggestions = suggestions != null && !suggestions.isEmpty();

        if (!hasSuggestions) {
            checkPanel.add(noSuggestionsLabel);
        } else {
            for (String suggestion : suggestions) {
                JCheckBox cb = new JCheckBox(suggestion);
                cb.addActionListener(e -> fireChange());
                checkBoxes.add(cb);
                checkPanel.add(cb);
            }
            checkPanel.add(noMatchesLabel);
        }

        JPanel listWrapper = new JPanel(new BorderLayout(0, 4));

        // Search field - util doar daca sunt sugestii de filtrat prin ele;
        // daca lista e goala, n-are sens sa aratam un field de cautare gol.
        if (hasSuggestions) {
            searchField.putClientProperty("JTextField.placeholderText", "Filter...");
            searchField.setToolTipText("Type to filter the list below");
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
                @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
                @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
            });
            listWrapper.add(searchField, BorderLayout.NORTH);
        }

        scroll = new JScrollPane(checkPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(300, 120));
        scroll.setBorder(BorderFactory.createLineBorder(borderColor()));
        listWrapper.add(scroll, BorderLayout.CENTER);

        add(listWrapper, BorderLayout.CENTER);

        // ── Custom field ──────────────────────────────────────────────────────
        JPanel customPanel = new JPanel(new BorderLayout(4, 0));
        JLabel customLabel = new JLabel("Custom:");
        customLabel.setFont(customLabel.getFont().deriveFont(11f));
        customLabel.setForeground(mutedTextColor());
        customField.setToolTipText(
                "Add values not detected in the model (comma separated)");
        customField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { fireChange(); }
            @Override public void removeUpdate(DocumentEvent e)  { fireChange(); }
            @Override public void changedUpdate(DocumentEvent e) { fireChange(); }
        });
        customPanel.add(customLabel,  BorderLayout.WEST);
        customPanel.add(customField,  BorderLayout.CENTER);
        add(customPanel, BorderLayout.SOUTH);
    }

    private static Color borderColor() {
        Color color = UIManager.getColor("Component.borderColor");
        return color != null ? color : Color.LIGHT_GRAY;
    }

    private static Color mutedTextColor() {
        Color color = UIManager.getColor("Label.disabledForeground");
        return color != null ? color : Color.GRAY;
    }

    /**
     * Filtreaza checkbox-urile vizibile dupa textul din searchField.
     * Nu elimina checkbox-urile din model (selectiile bifate raman valabile
     * chiar daca sunt ascunse temporar de filtru), doar le ascunde vizual.
     */
    private void applyFilter() {
        String query = searchField.getText().trim().toLowerCase();
        int visibleCount = 0;
        for (JCheckBox cb : checkBoxes) {
            boolean match = query.isEmpty() || cb.getText().toLowerCase().contains(query);
            cb.setVisible(match);
            if (match) visibleCount++;
        }
        noMatchesLabel.setVisible(visibleCount == 0 && !checkBoxes.isEmpty());
        checkPanel.revalidate();
        checkPanel.repaint();
    }

    /**
     * Inregistreaza un callback apelat la orice schimbare de selectie
     * (checkbox bifat/debifat sau custom field modificat). Poti inregistra
     * oricati listeneri - toti sunt notificati la fiecare schimbare.
     */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChange() {
        for (Runnable r : changeListeners) {
            r.run();
        }
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
     * Marcheaza vizual intreaga componenta ca valida/invalida (contur pe
     * scroll pane), pentru cazurile "cel putin o valoare trebuie selectata".
     */
    public void setValid(boolean valid) {
        scroll.setBorder(BorderFactory.createLineBorder(
                valid ? borderColor() : AccentColors.failure(), 1));
    }

    /**
     * Reseteaza toate selectiile, custom field-ul si filtrul de cautare.
     */
    public void clearAll() {
        for (JCheckBox cb : checkBoxes) {
            cb.setSelected(false);
        }
        customField.setText("");
        searchField.setText("");
    }
}