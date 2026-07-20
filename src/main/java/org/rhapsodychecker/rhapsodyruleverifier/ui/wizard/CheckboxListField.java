// ui/wizard/CheckboxListField.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

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
    private static final Color    NORMAL_BORDER_COLOR  = Color.LIGHT_GRAY;
    private static final Color    INVALID_BORDER_COLOR = new Color(200, 60, 60);

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

        noMatchesLabel = new JLabel("  No matches for this search.");
        noMatchesLabel.setForeground(Color.GRAY);
        noMatchesLabel.setFont(noMatchesLabel.getFont().deriveFont(Font.ITALIC, 11f));
        noMatchesLabel.setVisible(false);

        boolean hasSuggestions = suggestions != null && !suggestions.isEmpty();

        if (!hasSuggestions) {
            checkPanel.add(noSuggestionsLabel);
        } else {
            for (String suggestion : suggestions) {
                JCheckBox cb = new JCheckBox(suggestion);
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
        scroll.setBorder(BorderFactory.createLineBorder(NORMAL_BORDER_COLOR));
        listWrapper.add(scroll, BorderLayout.CENTER);

        add(listWrapper, BorderLayout.CENTER);

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
     * Ataseaza un callback care ruleaza la orice schimbare de selectie
     * (bifare/debifare checkbox) sau la orice modificare a custom field-ului.
     * Folosit pentru validare live (ex: "cel putin un stereotip selectat").
     */
    public void addChangeListener(final Runnable callback) {
        for (JCheckBox cb : checkBoxes) {
            cb.addItemListener(e -> callback.run());
        }
        customField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { callback.run(); }
            @Override public void removeUpdate(DocumentEvent e)  { callback.run(); }
            @Override public void changedUpdate(DocumentEvent e) { callback.run(); }
        });
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
                valid ? NORMAL_BORDER_COLOR : INVALID_BORDER_COLOR, valid ? 1 : 2));
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