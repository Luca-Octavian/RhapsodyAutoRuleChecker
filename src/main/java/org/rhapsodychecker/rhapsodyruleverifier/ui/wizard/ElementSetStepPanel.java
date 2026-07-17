// ui/wizard/ElementSetStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import javax.swing.*;
import java.awt.*;

/**
 * Pasul 2: definește ElementSets.
 */
public final class ElementSetStepPanel extends JPanel {

    private final WizardState         state;
    private final FastDetectionResult fast;

    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String>            setList   = new JList<>(listModel);
    private final JButton addBtn    = new JButton("Add Set...");
    private final JButton editBtn   = new JButton("Edit...");
    private final JButton removeBtn = new JButton("Remove");

    public ElementSetStepPanel(WizardState state, FastDetectionResult fast) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.fast  = fast;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
        refreshList();
    }

    private void build() {
        JLabel title = new JLabel("Define Element Sets");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        setList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(setList), BorderLayout.CENTER);

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
            String label = s.id();
            if (!s.types().isEmpty())       label += "  [types: " + s.types() + "]";
            if (!s.stereotypes().isEmpty()) label += "  [stereos: " + s.stereotypes() + "]";
            listModel.addElement(label);
        }
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
