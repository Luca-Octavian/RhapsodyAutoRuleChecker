package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import org.rhapsodychecker.rhapsodyruleverifier.fix.FixAction;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixEntry;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixPlan;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixProgressListener;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixService;
import org.rhapsodychecker.rhapsodyruleverifier.fix.FixStatus;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AccentColors;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.AppTheme;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientAccentButton;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.GradientProgressBar;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Modal dialog that previews the proposed auto-fix actions before applying them.
 * The "New Value" column is editable — pre-filled when the rule can suggest a
 * value, empty when the user needs to type one in. Unchecked rows and rows
 * missing required input are skipped. Input is sanitized (trimmed, control chars stripped).
 */
public final class FixPreviewDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final FixPlan plan;
    private final FixService fixService;
    private final FixPreviewState previewState;
    private final FixTableModel tableModel;
    private final JTable table;
    private final TableRowSorter<FixTableModel> rowSorter;
    private final GradientAccentButton simulateBtn;
    private final GradientAccentButton applyBtn;
    private final GradientAccentButton closeBtn;
    private final JLabel statusLabel;

    // Filter controls
    private JTextField searchField;
    private JComboBox<String> actionTypeFilter;
    private JComboBox<String> statusFilter;

    private boolean applied = false;

    public FixPreviewDialog(JFrame owner, FixPlan plan, FixService fixService,
            FixPreviewState previewState) {
        super(owner, "Auto-Fix Preview", true);
        this.plan = plan;
        this.fixService = fixService;
        this.previewState = previewState;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeAndSave();
            }
        });

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
                + "Unchecked rows and rows missing a required value will be skipped."
                + "</span></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
        headerPanel.add(hint, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // Filter bar
        JPanel filterPanel = buildFilterPanel(plan.entries());
        headerPanel.add(filterPanel, BorderLayout.SOUTH);

        // Table
        tableModel = new FixTableModel(plan.entries(), previewState);
        table = new JTable(tableModel);
        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);
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
            if (!FixUiSupport.requiresNewValue(entry.action())) {
                continue;
            }
            if (FixUiSupport.hasRequiredInput(entry.action())) {
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
        applyBtn.setToolTipText("Apply checked fixes that have all required values");

        simulateBtn.addActionListener(e -> onSimulate());
        applyBtn.addActionListener(e -> onApply());
        closeBtn.addActionListener(e -> closeAndSave());

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
        commitPendingEdit();
        syncUserValuesToActions();

        // Recalculate every editable row from its current value and selection.
        for (FixEntry entry : plan.entries()) {
            FixStatus s = entry.status();
            if (s == FixStatus.SIMULATED || s == FixStatus.CONFLICT
                    || s == FixStatus.PENDING || s == FixStatus.SKIPPED) {
                entry.resetToPending();
            }
        }
        markUnavailableAsSkipped();

        int conflicts = fixService.simulate(plan);
        tableModel.fireTableDataChanged();

        int ok = plan.countByStatus(FixStatus.SIMULATED);
        int skipped = plan.countByStatus(FixStatus.SKIPPED);
        StringBuilder msg = new StringBuilder("Simulation complete: ")
                .append(ok).append(" OK");
        if (skipped > 0) msg.append(", ").append(skipped).append(" skipped");
        if (conflicts > 0) msg.append(", ").append(conflicts).append(" conflicts");
        statusLabel.setText(msg.toString());
    }

    private void onApply() {
        commitPendingEdit();

        // Sync values and classify anything the user deliberately excluded.
        syncUserValuesToActions();
        markUnavailableAsSkipped();

        // Validate that at least one selected row has its action-specific input.
        int actionableCount = 0;
        for (int i = 0; i < plan.entries().size(); i++) {
            FixEntry entry = plan.entries().get(i);
            FixStatus status = entry.status();
            if (FixUiSupport.isActionable(entry.action(), tableModel.isChecked(i))
                    && (status == FixStatus.PENDING || status == FixStatus.SIMULATED)) {
                actionableCount++;
            }
        }

        if (actionableCount == 0) {
            JOptionPane.showMessageDialog(this,
                    "No actionable fixes. Select at least one row and provide any\n"
                    + "required value, then try again.",
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

        // ── Build modal progress dialog ────────────────────────────────
        final JDialog progressDialog = new JDialog(this, "Applying Fixes", true);
        progressDialog.setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        progressDialog.setResizable(false);

        JPanel progressContent = new JPanel();
        progressContent.setLayout(new BoxLayout(progressContent, BoxLayout.Y_AXIS));
        progressContent.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel titleLabel = new JLabel("Applying fixes\u2026");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(titleLabel);
        progressContent.add(Box.createVerticalStrut(12));

        final GradientProgressBar progressBar = new GradientProgressBar();
        progressBar.setMinimum(0);
        progressBar.setMaximum(actionableCount);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("0 / " + actionableCount);
        progressBar.setPreferredSize(new Dimension(380, 24));
        progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        progressBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(progressBar);
        progressContent.add(Box.createVerticalStrut(8));

        final JLabel elementLabel = new JLabel(" ");
        elementLabel.setForeground(AccentColors.mutedText());
        elementLabel.setFont(elementLabel.getFont().deriveFont(Font.PLAIN, 12f));
        elementLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        progressContent.add(elementLabel);
        progressContent.add(Box.createVerticalStrut(14));

        final GradientAccentButton cancelBtn = GradientAccentButton.neutral("Cancel");
        cancelBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel cancelPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        cancelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        cancelPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        cancelPanel.add(cancelBtn);
        progressContent.add(cancelPanel);

        progressDialog.setContentPane(progressContent);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(this);

        // Cancel flag
        final boolean[] cancelled = {false};
        cancelBtn.addActionListener(e -> {
            cancelled[0] = true;
            cancelBtn.setEnabled(false);
            cancelBtn.setText("Cancelling\u2026");
        });

        // ── SwingWorker with progress ──────────────────────────────────
        final int total = plan.entries().size();

        new SwingWorker<Integer, int[]>() {
            private int appliedCount;

            @Override
            protected Integer doInBackground() {
                appliedCount = fixService.apply(plan, true, new FixProgressListener() {
                    @Override
                    public boolean onProgress(int current, int totalEntries, String elementName) {
                        publish(new int[]{current, totalEntries});
                        // Use element name for label update via invokeLater
                        final String name = elementName != null ? elementName : "";
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                elementLabel.setText(name.length() > 55
                                        ? name.substring(0, 52) + "\u2026" : name);
                            }
                        });
                        return !cancelled[0];
                    }
                });
                return appliedCount;
            }

            @Override
            protected void process(java.util.List<int[]> chunks) {
                int[] last = chunks.get(chunks.size() - 1);
                int current = last[0];
                int t = last[1];
                progressBar.setMaximum(t);
                progressBar.setValue(current);
                int pct = t > 0 ? (int) ((current * 100L) / t) : 0;
                progressBar.setString(current + " / " + t + "  (" + pct + "%)");
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get(); // surface any exception from doInBackground
                } catch (Exception ex) {
                    statusLabel.setText("Apply failed: " + ex.getMessage());
                    simulateBtn.setEnabled(true);
                    applyBtn.setEnabled(true);
                    return;
                }
                tableModel.fireTableDataChanged();
                applied = true;

                // Dispose preview and show result dialog
                dispose();
                showResultDialog();
            }
        }.execute();

        // Show the modal dialog — blocks until done() calls dispose()
        progressDialog.setVisible(true);
    }

    private void commitPendingEdit() {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private void closeAndSave() {
        commitPendingEdit();
        syncUserValuesToActions();
        dispose();
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
     * Status classification is handled separately. Called before simulation,
     * application, and closing so edits survive reopening within the session.
     */
    private void syncUserValuesToActions() {
        for (int i = 0; i < plan.entries().size(); i++) {
            FixEntry entry = plan.entries().get(i);
            String userValue = sanitize(tableModel.getUserNewValue(i));

            // Rebuild whenever the edited value differs, including when a value
            // is cleared or its row is unchecked.
            FixAction current = entry.action();
            String currentNew = current.newValue() != null ? current.newValue() : "";
            String editedNew = userValue != null ? userValue : "";
            if (!currentNew.equals(editedNew)) {
                FixAction updated = FixAction.builder()
                        .elementGuid(current.elementGuid())
                        .elementName(current.elementName())
                        .actionType(current.actionType())
                        .field(current.field())
                        .oldValue(current.oldValue())
                        .newValue(editedNew)
                        .ruleId(current.ruleId())
                        .description(current.description())
                        .options(current.options())
                        .build();
                FixEntry replacement = new FixEntry(updated, entry.status());
                if (entry.status() == FixStatus.CONFLICT && entry.errorMessage() != null) {
                    replacement.markConflict(entry.errorMessage());
                } else if (entry.status() == FixStatus.FAILED && entry.errorMessage() != null) {
                    replacement.markFailed(entry.errorMessage());
                } else if (entry.status() == FixStatus.SKIPPED) {
                    replacement.markSkipped(entry.errorMessage());
                }
                plan.entries().set(i, replacement);
            }
        }
    }

    /**
     * Mark unchecked rows and rows missing action-specific required input as SKIPPED.
     */
    private void markUnavailableAsSkipped() {
        for (int i = 0; i < plan.entries().size(); i++) {
            FixEntry entry = plan.entries().get(i);
            if (entry.status() != FixStatus.PENDING && entry.status() != FixStatus.SIMULATED) {
                continue;
            }
            if (!tableModel.isChecked(i)) {
                entry.markSkipped("Excluded by user");
            } else if (!FixUiSupport.hasRequiredInput(entry.action())) {
                entry.markSkipped("No value provided");
            }
        }
    }

    // ── Filter panel ───────────────────────────────────────────────────────

    private JPanel buildFilterPanel(List<FixEntry> entries) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)));

        // Search by element name
        panel.add(new JLabel("Search:"));
        searchField = new JTextField(16);
        searchField.setToolTipText("Filter by element name (case-insensitive)");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilters(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });
        panel.add(searchField);

        // Action type filter
        panel.add(Box.createHorizontalStrut(8));
        panel.add(new JLabel("Action:"));
        Set<String> actionTypes = new LinkedHashSet<>();
        actionTypes.add("All");
        for (FixEntry entry : entries) {
            actionTypes.add(entry.action().actionType().name());
        }
        actionTypeFilter = new JComboBox<>(actionTypes.toArray(new String[0]));
        actionTypeFilter.setToolTipText("Filter by fix action type");
        actionTypeFilter.addActionListener(e -> applyFilters());
        panel.add(actionTypeFilter);

        // Status filter
        panel.add(Box.createHorizontalStrut(8));
        panel.add(new JLabel("Status:"));
        statusFilter = new JComboBox<>(new String[]{
                "All", "Pending", "OK", "Applied", "Failed", "Rolled Back", "Conflict", "Skipped"
        });
        statusFilter.setToolTipText("Filter by fix status");
        statusFilter.addActionListener(e -> applyFilters());
        panel.add(statusFilter);

        return panel;
    }

    private void applyFilters() {
        List<RowFilter<FixTableModel, Integer>> filters = new ArrayList<>();

        // Text search on element name (column 1)
        String searchText = searchField.getText().trim();
        if (!searchText.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(searchText), 1));
        }

        // Action type filter (column 2)
        String selectedAction = (String) actionTypeFilter.getSelectedItem();
        if (selectedAction != null && !"All".equals(selectedAction)) {
            filters.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(selectedAction), 2));
        }

        // Status filter (column 5)
        String selectedStatus = (String) statusFilter.getSelectedItem();
        if (selectedStatus != null && !"All".equals(selectedStatus)) {
            filters.add(RowFilter.regexFilter(
                    "^" + java.util.regex.Pattern.quote(selectedStatus) + "$", 5));
        }

        if (filters.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.andFilter(filters));
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
        private final FixPreviewState previewState;
        private final List<String> userNewValues;

        FixTableModel(List<FixEntry> entries, FixPreviewState previewState) {
            this.entries = entries;
            this.previewState = previewState;
            this.userNewValues = new ArrayList<String>();
            for (int i = 0; i < entries.size(); i++) {
                previewState.isSelected(i);
                String nv = entries.get(i).action().newValue();
                userNewValues.add(nv != null ? nv : "");
            }
        }

        boolean isChecked(int row) {
            return previewState.isSelected(row);
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
                case 0: return Boolean.valueOf(previewState.isSelected(row));
                case 1: return entry.action().elementName();
                case 2: return entry.action().actionType().name()
                        + (entry.action().field() != null ? " [" + entry.action().field() + "]" : "");
                case 3: return entry.action().oldValue() != null ? entry.action().oldValue() : "";
                case 4: return userNewValues.get(row);
                case 5: return FixUiSupport.statusLabel(entry.status());
                default: return "";
            }
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            FixEntry entry = entries.get(row);
            if (col == 0) {
                boolean selected = ((Boolean) value).booleanValue();
                previewState.setSelected(row, selected);
                if (!selected && isReprocessable(entry.status())) {
                    entry.markSkipped("Excluded by user");
                } else if (selected && entry.status() == FixStatus.SKIPPED) {
                    entry.resetToPending();
                }
                fireTableRowsUpdated(row, row);
            } else if (col == 4) {
                userNewValues.set(row, value != null ? value.toString() : "");
                if (isReprocessable(entry.status())) {
                    if (previewState.isSelected(row)) {
                        entry.resetToPending();
                    } else {
                        entry.markSkipped("Excluded by user");
                    }
                }
                fireTableRowsUpdated(row, row);
            }
        }

        private static boolean isReprocessable(FixStatus status) {
            return status == FixStatus.PENDING || status == FixStatus.SIMULATED
                    || status == FixStatus.CONFLICT || status == FixStatus.SKIPPED;
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
            int modelRow = table.convertRowIndexToModel(row);
            FixEntry entry = entries.get(modelRow);
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
                if ("Applied".equals(status)) {
                    setForeground(new Color(0x21, 0x73, 0x46));
                } else if ("Failed".equals(status)) {
                    setForeground(AccentColors.failure());
                } else if ("Conflict".equals(status)) {
                    setForeground(new Color(255, 165, 0));
                } else if ("Skipped".equals(status) || "Rolled Back".equals(status)) {
                    setForeground(Color.GRAY);
                } else if ("OK".equals(status)) {
                    setForeground(new Color(0x21, 0x73, 0x46));
                } else {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }
}