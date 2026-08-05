// ui/wizard/ElementSetStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;

import javax.swing.*;
import java.awt.*;

public final class ElementSetStepPanel extends JPanel {

    private final WizardState         state;
    private final FastDetectionResult fast;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            setList   = new JList<>(listModel);
    private final JButton addBtn    = new JButton("Add Set...");
    private final JButton editBtn   = new JButton("Edit...");
    private final JButton removeBtn = new JButton("Remove");

    private static final String CARD_LIST  = "list";
    private static final String CARD_EMPTY = "empty";
    private final JPanel centerPanel = new JPanel(new CardLayout());

    public ElementSetStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.fast  = fast;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        refreshList();
    }

    private void build() {
        setList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel emptyLabel = new JLabel(
                "<html><div style='text-align:center;'>No element sets yet.<br>" +
                "Click \"Add Set...\" to define your first one.</div></html>",
                SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));

        JScrollPane setListScroll = new JScrollPane(setList);
        setListScroll.getVerticalScrollBar().setUnitIncrement(16);

        centerPanel.add(setListScroll, CARD_LIST);
        centerPanel.add(emptyLabel, CARD_EMPTY);
        add(centerPanel, BorderLayout.CENTER);

        addBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_FILL_STYLE);
        editBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);
        removeBtn.putClientProperty("FlatLaf.style", AccentColors.PURPLE_HOVER_STYLE);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e    -> openDialog(null));
        editBtn.addActionListener(e   -> openEditDialog());
        removeBtn.addActionListener(e -> removeSelected());

        setList.addListSelectionListener(e -> {
            boolean sel = setList.getSelectedIndex() >= 0;
            editBtn.setEnabled(sel);
            removeBtn.setEnabled(sel);
        });
        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
    }

    private void refreshList() {
        listModel.clear();
        for (ElementSetDefinition s : state.sets()) {
            listModel.addElement(formatEntry(s));
        }

        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, listModel.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    private String formatEntry(ElementSetDefinition s) {
        StringBuilder sb = new StringBuilder(s.id());

        boolean hasKinds   = !s.kinds().isEmpty();
        boolean hasTypes   = !s.types().isEmpty();
        boolean hasStereos = !s.stereotypes().isEmpty();

        if (hasKinds || hasTypes || hasStereos) {
            sb.append("  —  ");
            if (hasKinds)   sb.append(String.join(", ", s.kinds()));
            if (hasTypes) {
                if (hasKinds) sb.append("  |  ");
                sb.append(String.join(", ", s.types()));
            }
            if (hasStereos) {
                if (hasKinds || hasTypes) sb.append("  |  ");
                sb.append(String.join(", ", s.stereotypes()));
            }
        }

        // Exact count from pre-cached data
        int count = ElementSetCountEstimator.estimateTotal(
                fast, s.kinds(), s.types(), s.stereotypes());
        if (count >= 0) {
            sb.append("    (").append(count).append(" elements)");
        }

        return sb.toString();
    }

    private void openDialog(ElementSetDefinition prefill) {
        ElementSetDialog dialog = new ElementSetDialog(
                SwingUtilities.getWindowAncestor(this), fast, prefill);
        dialog.setVisible(true);
        dialog.getResult().ifPresent(set -> {
            state.putSet(set);
            refreshList();
        });
    }

    private void openEditDialog() {
        int idx = setList.getSelectedIndex();
        if (idx < 0) return;
        ElementSetDefinition existing = state.sets().get(idx);
        openDialog(existing);
    }

    private void removeSelected() {
        int idx = setList.getSelectedIndex();
        if (idx < 0) return;
        String id = state.sets().get(idx).id();
        state.removeSet(id);
        refreshList();
    }
}