// ui/wizard/RuleStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;

/**
 * Pasul 4: adăugare / editare / ștergere reguli.
 */
public final class RuleStepPanel extends JPanel {

    private final WizardState         state;
    private final FastDetectionResult fast;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            ruleList  = new JList<>(listModel);
    private final JButton addBtn    = new JButton("Add Rule...");
    private final JButton editBtn   = new JButton("Edit...");
    private final JButton removeBtn = new JButton("Remove");

    public RuleStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.fast  = fast;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        refreshList();
    }

    private void build() {
        JLabel title = new JLabel("Add Rules");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(ruleList), BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e    -> openDialog(-1, null));
        editBtn.addActionListener(e   -> openEditDialog());
        removeBtn.addActionListener(e -> removeSelected());

        ruleList.addListSelectionListener(e -> {
            boolean sel = ruleList.getSelectedIndex() >= 0;
            editBtn.setEnabled(sel);
            removeBtn.setEnabled(sel);
        });
        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
    }

    private void refreshList() {
        listModel.clear();
        for (WizardState.RuleRequest r : state.rules()) {
            String label = r.toString();
            if (r.elementSetId() != null) label += "  → " + r.elementSetId();
            if (r.targetAliasId() != null) label += "  [" + r.targetAliasId() + "]";
            listModel.addElement(label);
        }
    }

    private void openDialog(int editIndex, WizardState.RuleRequest prefill) {
        RuleDialog dialog = new RuleDialog(
                SwingUtilities.getWindowAncestor(this), state, fast, prefill);
        dialog.setVisible(true);
        dialog.getResult().ifPresent(rule -> {
            if (editIndex >= 0) {
                state.replaceRule(editIndex, rule);
            } else {
                state.addRule(rule);
            }
            refreshList();
        });
    }

    private void openEditDialog() {
        int idx = ruleList.getSelectedIndex();
        if (idx < 0) return;
        WizardState.RuleRequest existing = state.rules().get(idx);
        openDialog(idx, existing);
    }

    private void removeSelected() {
        int idx = ruleList.getSelectedIndex();
        if (idx < 0) return;
        state.removeRule(idx);
        refreshList();
    }
}
