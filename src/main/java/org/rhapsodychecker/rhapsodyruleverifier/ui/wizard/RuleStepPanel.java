// ui/wizard/RuleStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;

import javax.swing.*;
import java.awt.*;

/**
 * Step 2: add / edit / delete rules.
 */
public final class RuleStepPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final WizardState         state;
    private final FastDetectionResult fast;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            ruleList  = new JList<>(listModel);
    // Mirrors ElementSetStepPanel so the two wizard steps feel identical:
    // Add is PRIMARY purple, Edit is supporting, Remove carries the red accent.
    private final GradientAccentButton addBtn    = GradientAccentButton.primary("Add Rule...", AccentColors.PURPLE_HEX);
    private final GradientAccentButton editBtn   = GradientAccentButton.secondary("Edit...", AccentColors.PURPLE_HEX);
    private final GradientAccentButton removeBtn = GradientAccentButton.secondary("Remove", AccentColors.RED_HEX);

    private static final String CARD_LIST  = "list";
    private static final String CARD_EMPTY = "empty";
    private final JPanel      centerPanel = new JPanel(new CardLayout());

    public RuleStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.fast  = fast;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        refreshList();
    }

    private void build() {
        ruleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel emptyLabel = new JLabel(
                "<html><div style='text-align:center;'>No rules yet.<br>" +
                "Click \"Add Rule...\" to define your first one.</div></html>",
                SwingConstants.CENTER);
        emptyLabel.setForeground(AccentColors.mutedText());
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));

        centerPanel.add(new JScrollPane(ruleList), CARD_LIST);
        centerPanel.add(emptyLabel, CARD_EMPTY);
        add(centerPanel, BorderLayout.CENTER);

        // Variants/colours are set on the field declarations above.
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
            listModel.addElement(formatEntry(r));
        }

        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, listModel.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    /**
     * Formats a RuleRequest for display in the list.
     */
    private static String formatEntry(WizardState.RuleRequest r) {
        StringBuilder sb = new StringBuilder(r.id());
        sb.append("  [").append(r.ruleType().toLowerCase()).append("]");
        if (r.elementSetId()   != null) sb.append("  on ").append(r.elementSetId());
        if (r.targetSpec()     != null) sb.append("  → ").append(r.targetSpec().kind());
        if (r.group() != null && !r.group().trim().isEmpty()) {
            sb.append("  group=").append(r.group());
        }
        return sb.toString();
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