package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixEntry;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixPlan;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixService;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixStatus;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog that previews the proposed auto-fix actions before applying them.
 * The "New Value" column is editable — pre-filled when the rule can suggest a
 * value, empty when the user needs to type one in. Rows with empty new values
 * are skipped on apply. Input is sanitized (trimmed, control chars stripped).
 */
public final class FixPreviewDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final FixPlan plan;
    private final FixService fixService;
    private final FixTableModel tableModel;
    private final JTable table;
    private final GradientAccentButton simulateBtn;
    private final GradientAccentButton applyBtn;
    private final GradientAccentButton closeBtn;
    private final JLabel statusLabel;

    private boolean applied = false;

    public FixPreviewDialog(JFrame owner, FixPlan plan, FixService fixService) {
        super(owner, "Auto-Fix Preview", true);
        this.plan = plan;
        this.fixService = fixService;

        setSize(950, 520);
        setMinimumSize(new Dimension(750, 380));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(new SectionHeader("Proposed Fixes"), BorderLayout.NORTH);
        JLabel hint = new JLabel(
                "<html><span style='color:" + AppTheme.mutedTextHex() + ";'>"
                + "Review each row. Edit the \u201cNew Value\u201d column to set what you want. "
                + "Rows with empty new values will be skipped."
                + "</span></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
        headerPanel.add(hint, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Table
        tableModel = new FixTableModel(plan.entries());
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(0).setMaxWidth(40);   // checkbox
        table.getColumnModel().getColumn(0).setMinWidth(40);
        table.getColumnModel().getColumn(5).setMinWidth(80);   // status
        table.getColumnModel().getColumn(4).setPreferredWidth(180); // new value — wider for editing
        table.setDefaultRenderer(String.class, new StatusCellRenderer());
        // Ensure the new value column stands out as editable
        table.getColumnModel().getColumn(4).setCellRenderer(new EditableCellRenderer());

        // Per-row cell editor: use JComboBox when options are available, plain text otherwise
        table.getColumnModel().getColumn(4).setCellEditor(new OptionAwareCellEditor(plan.entries()));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // Bottom: status + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        int prefilled = 0;
        int needsInput = 0;
        for (FixEntry entry : plan.entries()) {
            if (entry.action().newValue() != null && !entry.action().newValue().isEmpty()) {
                prefilled++;
            } else {
                needsInput++;
            }
        }
        StringBuilder statusText = new StringBuilder();
        statusText.append(plan.entries().size()).append(" fix actions");
        if (prefilled > 0) statusText.append(" (").append(prefilled).append(" pre-filled)");
        if (needsInput > 0) statusText.append(" (").append(needsInput).append(" need your input)");
        statusLabel = new JLabel(statusText.toString());
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        simulateBtn = GradientAccentButton.primary("Simulate", AccentColors.ORANGE_HEX);
        applyBtn = GradientAccentButton.primary("Apply Fixes", AccentColors.RED_HEX);
        closeBtn = GradientAccentButton.neutral("Close");

        simulateBtn.setToolTipText("Dry-run: checks for conflicts without changing anything");
        applyBtn.setToolTipText("Apply checked fixes with non-empty values to the Rhapsody model");

        simulateBtn.addActionListener(e -> onSimulate());
        applyBtn.addActionListener(e -> onApply());
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(simulateBtn);
        buttonPanel.add(applyBtn);
        buttonPanel.add(closeBtn);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    public boolean wasApplied() {
        return applied;
    }

    private void onSimulate() {
        // Commit any pending cell edits before simulate
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        // Reset all non-terminal entries back to PENDING so they can be re-simulated
        for (FixEntry entry : plan.entries()) {
            FixStatus s = entry.status();
            if (s == FixStatus.SIMULATED || s == FixStatus.CONFLICT || s == FixStatus.PENDING) {
                entry.resetToPending();
            }
        }

        // Sync user values into actions (no CONFLICT marking for empty values)
        syncUserValuesToActions();

        int conflicts = fixService.simulate(plan);
        tableModel.fireTableDataChanged();

        // Count rows that were skipped (still PENDING = empty value or unchecked)
        int skipped = 0;
        for (FixEntry entry : plan.entries()) {
            if (entry.status() == FixStatus.PENDING) skipped++;
        }

        StringBuilder msg = new StringBuilder();
        if (conflicts == 0) {
            msg.append("Simulation passed \u2014 no conflicts found");
        } else {
            msg.append("Simulation found ").append(conflicts)
               .append(conflicts == 1 ? " conflict" : " conflicts")
               .append(" \u2014 check the Status column");
        }
        if (skipped > 0) {
            msg.append(" (").append(skipped).append(" skipped \u2014 needs input)");
        }
        statusLabel.setText(msg.toString());
    }

    private void onApply() {
        // Commit any pending cell edits
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }

        // Sync user values, then mark empty/unchecked rows as SKIPPED (apply-time only)
        syncUserValuesToActions();
        markEmptyAsSkipped();

        // Validate: check that at least one row has a non-empty new value and is checked
        int actionableCount = 0;
        for (int i = 0; i < plan.entries().size(); i++) {
            if (tableModel.isChecked(i)) {
                FixEntry entry = plan.entries().get(i);
                String nv = entry.action().newValue();
                if (nv != null && !nv.trim().isEmpty()) {
                    actionableCount++;
                }
            }
        }

        if (actionableCount == 0) {
            JOptionPane.showMessageDialog(this,
                    "No actionable fixes. Fill in the \"New Value\" column for rows\n"
                    + "you want to fix, then try again.",
                    "Nothing to Apply", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Warning popup
        int choice = JOptionPane.showConfirmDialog(this,
                "WARNING: Auto-fix has the chance to corrupt the Rhapsody model.\n"
                + "Changes will NOT be auto-saved \u2014 you can close Rhapsody\n"
                + "without saving to revert.\n\n"
                + actionableCount + " fix(es) will be applied. Proceed?",
                "Confirm Apply",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        simulateBtn.setEnabled(false);
        applyBtn.setEnabled(false);
        statusLabel.setText("Applying fixes...");

        new SwingWorker<Integer, Void>() {
            private int appliedCount;

            @Override
            protected Integer doInBackground() {
                appliedCount = fixService.apply(plan, true);
                return appliedCount;
            }

            @Override
            protected void done() {
                tableModel.fireTableDataChanged();
                applied = true;

                int failed = plan.countByStatus(FixStatus.FAILED);
                int conflict = plan.countByStatus(FixStatus.CONFLICT);

                StringBuilder sb = new StringBuilder();
                sb.append(appliedCount).append(" applied");
                if (failed > 0) sb.append(", ").append(failed).append(" failed");
                if (conflict > 0) sb.append(", ").append(conflict).append(" conflicts");
                statusLabel.setText(sb.toString());

                showResultDialog();
            }
        }.execute();
    }

    /**
     * Sanitize a user value: trim whitespace and strip control characters.
     */
    private static String sanitize(String value) {
        if (value == null) return null;
        value = value.trim();
        StringBuilder cleaned = new StringBuilder();
        for (int c = 0; c < value.length(); c++) {
            char ch = value.charAt(c);
            if (ch == '\n' || ch == '\r' || !Character.isISOControl(ch)) {
                cleaned.append(ch);
            }
        }
        return cleaned.toString();
    }

    /**
     * Sync user-edited values from the table model back into the FixEntry actions.
     * Does NOT mark empty/unchecked rows as CONFLICT — those stay PENDING so the
     * simulator skips them naturally. Called before both simulate and apply.
     */
    private void syncUserValuesToActions() {
        for (int i = 0; i < plan.entries().size(); i++) {
            FixEntry entry = plan.entries().get(i);
            String userValue = sanitize(tableModel.getUserNewValue(i));

            // Skip unchecked or empty-value rows — leave them as-is (PENDING)
            if (!tableModel.isChecked(i) || userValue == null || userValue.isEmpty()) {
                continue;
            }

            // Rebuild the action with the user's value if it differs
            FixAction current = entry.action();
            String currentNew = current.newValue();
            if (currentNew == null || !currentNew.equals(userValue)) {
                FixAction updated = FixAction.builder()
                        .elementGuid(current.elementGuid())
                        .elementName(current.elementName())
                        .actionType(current.actionType())
                        .field(current.field())
                        .oldValue(current.oldValue())
                        .newValue(userValue)
                        .ruleId(current.ruleId())
                        .description(current.description())
                        .build();
                plan.entries().set(i, new FixEntry(updated, entry.status()));
            }
        }
    }

    /**
     * Mark unchecked and empty-value rows as SKIPPED. Called only at apply time.
     */
    private void markEmptyAsSkipped() {
        for (int i = 0; i < plan.entries().size(); i++) {
            FixEntry entry = plan.entries().get(i);
            if (entry.status() != FixStatus.PENDING && entry.status() != FixStatus.SIMULATED) {
                continue;
            }
            if (!tableModel.isChecked(i)) {
                entry.markSkipped("Excluded by user");
            } else {
                String nv = entry.action().newValue();
                if (nv == null || nv.trim().isEmpty()) {
                    entry.markSkipped("No value provided");
                }
            }
        }
    }

    private void showResultDialog() {
        FixResultDialog resultDialog = new FixResultDialog(
                (JFrame) getOwner(), plan, fixService);
        resultDialog.setVisible(true);
    }

    // ── Table model ────────────────────────────────────────────────────────

    private static final class FixTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "\u2611", "Element", "Action", "Current Value", "New Value", "Status"
        };

        private final List<FixEntry> entries;
        private final List<Boolean> checked;
        private final List<String> userNewValues;

        FixTableModel(List<FixEntry> entries) {
            this.entries = entries;
            this.checked = new ArrayList<Boolean>();
            this.userNewValues = new ArrayList<String>();
            for (int i = 0; i < entries.size(); i++) {
                checked.add(Boolean.TRUE);
                String nv = entries.get(i).action().newValue();
                userNewValues.add(nv != null ? nv : "");
            }
        }

        boolean isChecked(int row) {
            return checked.get(row).booleanValue();
        }

        String getUserNewValue(int row) {
            return userNewValues.get(row);
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            // Checkbox (col 0) and New Value (col 4) are editable
            return col == 0 || col == 4;
        }

        @Override
        public Object getValueAt(int row, int col) {
            FixEntry entry = entries.get(row);
            switch (col) {
                case 0: return checked.get(row);
                case 1: return entry.action().elementName();
                case 2: return entry.action().actionType().name()
                        + (entry.action().field() != null ? " [" + entry.action().field() + "]" : "");
                case 3: return entry.action().oldValue() != null ? entry.action().oldValue() : "";
                case 4: return userNewValues.get(row);
                case 5: return entry.status().name();
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (col == 0) {
                checked.set(row, (Boolean) value);
                fireTableCellUpdated(row, col);
            } else if (col == 4) {
                userNewValues.set(row, value != null ? value.toString() : "");
                fireTableCellUpdated(row, col);
            }
        }
    }

    // ── Per-row cell editor — JComboBox for rows with options, text field otherwise ──

    private static final class OptionAwareCellEditor extends AbstractCellEditor implements TableCellEditor {

        private static final long serialVersionUID = 1L;
        private final List<FixEntry> entries;
        private JComponent activeEditor;

        OptionAwareCellEditor(List<FixEntry> entries) {
            this.entries = entries;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            FixEntry entry = entries.get(row);
            List<String> options = entry.action().options();

            if (options != null && !options.isEmpty()) {
                // Combo box with an empty first item so user must actively pick
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem("");  // blank = no selection
                for (String opt : options) {
                    combo.addItem(opt);
                }
                String current = value != null ? value.toString() : "";
                combo.setSelectedItem(current);
                activeEditor = combo;
                return combo;
            } else {
                JTextField tf = new JTextField(value != null ? value.toString() : "");
                activeEditor = tf;
                return tf;
            }
        }

        @Override
        public Object getCellEditorValue() {
            if (activeEditor instanceof JComboBox) {
                Object sel = ((JComboBox<?>) activeEditor).getSelectedItem();
                return sel != null ? sel.toString() : "";
            } else if (activeEditor instanceof JTextField) {
                return ((JTextField) activeEditor).getText();
            }
            return "";
        }
    }

    // ── Editable cell renderer — gives a visual hint the cell is editable ──

    private static final class EditableCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            String text = value != null ? value.toString() : "";
            if (text.isEmpty() && !isSelected) {
                setText("(click to enter value)");
                setForeground(AccentColors.mutedText());
                setFont(getFont().deriveFont(Font.ITALIC));
            } else {
                setFont(getFont().deriveFont(Font.PLAIN));
                if (!isSelected) {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }

    // ── Status cell renderer ───────────────────────────────────────────────

    private static final class StatusCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if (value instanceof String && !isSelected) {
                String status = (String) value;
                if ("APPLIED".equals(status)) {
                    setForeground(new Color(0x21, 0x73, 0x46));
                } else if ("FAILED".equals(status)) {
                    setForeground(AccentColors.failure());
                } else if ("CONFLICT".equals(status)) {
                    setForeground(new Color(255, 165, 0));
                } else if ("SKIPPED".equals(status)) {
                    setForeground(Color.GRAY);
                } else if ("SIMULATED".equals(status)) {
                    setForeground(AccentColors.primary());
                } else {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }
}