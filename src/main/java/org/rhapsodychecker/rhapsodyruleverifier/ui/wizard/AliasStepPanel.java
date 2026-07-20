// ui/wizard/AliasStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;

/**
 * Pasul 3: configurare alias-uri.
 */
public final class AliasStepPanel extends JPanel {

    private final WizardState         state;
    private final FastDetectionResult fast;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            aliasList = new JList<>(listModel);
    private final JButton addBtn    = new JButton("Add Alias...");
    private final JButton editBtn   = new JButton("Edit...");
    private final JButton removeBtn = new JButton("Remove");

    private static final String CARD_LIST  = "list";
    private static final String CARD_EMPTY = "empty";
    private final JPanel      centerPanel = new JPanel(new CardLayout());

    public AliasStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.fast  = fast;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        refreshList();
    }

    private void build() {
        JLabel title = new JLabel("Configure Aliases");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        aliasList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel emptyLabel = new JLabel(
                "<html><div style='text-align:center;'>No aliases yet.<br>" +
                "Click \"Add Alias...\" to define your first one.</div></html>",
                SwingConstants.CENTER);
        emptyLabel.setForeground(Color.GRAY);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(Font.ITALIC, 12f));

        centerPanel.add(new JScrollPane(aliasList), CARD_LIST);
        centerPanel.add(emptyLabel, CARD_EMPTY);
        add(centerPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e    -> openDialog(null));
        editBtn.addActionListener(e   -> openEditDialog());
        removeBtn.addActionListener(e -> removeSelected());

        aliasList.addListSelectionListener(e -> {
            boolean sel = aliasList.getSelectedIndex() >= 0;
            editBtn.setEnabled(sel);
            removeBtn.setEnabled(sel);
        });
        editBtn.setEnabled(false);
        removeBtn.setEnabled(false);
    }

    private void refreshList() {
        listModel.clear();
        for (AliasDefinition a : state.aliases()) {
            listModel.addElement(a.id()
                    + "  [" + a.kind().name().toLowerCase() + "]"
                    + a.title().map(t -> "  \"" + t + "\"").orElse(""));
        }

        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.show(centerPanel, listModel.isEmpty() ? CARD_EMPTY : CARD_LIST);
    }

    private void openDialog(AliasDefinition prefill) {
        AliasDialog dialog = new AliasDialog(
                SwingUtilities.getWindowAncestor(this), fast, prefill);
        dialog.setVisible(true);
        dialog.getResult().ifPresent(alias -> {
            state.putAlias(alias);
            refreshList();
        });
    }

    private void openEditDialog() {
        int idx = aliasList.getSelectedIndex();
        if (idx < 0) return;
        AliasDefinition existing = state.aliases().get(idx);
        openDialog(existing);
    }

    private void removeSelected() {
        int idx = aliasList.getSelectedIndex();
        if (idx < 0) return;
        String id = state.aliases().get(idx).id();
        state.removeAlias(id);
        refreshList();
    }
}