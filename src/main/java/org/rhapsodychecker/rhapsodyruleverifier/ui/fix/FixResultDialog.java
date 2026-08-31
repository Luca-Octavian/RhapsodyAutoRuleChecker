package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

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
import java.awt.*;
import java.util.List;

/**
 * Modal dialog showing fix results after application.
 * Displays each entry's final status and offers a rollback button
 * if any entries were applied.
 */
public final class FixResultDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    public FixResultDialog(JFrame owner, FixPlan plan, FixService fixService) {
        super(owner, "Fix Results", true);

        setSize(800, 400);
        setMinimumSize(new Dimension(600, 300));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        // Header
        add(new SectionHeader("Fix Results"), BorderLayout.NORTH);

        // Table
        ResultTableModel tableModel = new ResultTableModel(plan.entries());
        JTable table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(3).setMinWidth(80);
        table.setDefaultRenderer(String.class, new StatusCellRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // Bottom
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        int applied = plan.countByStatus(FixStatus.APPLIED);
        int failed = plan.countByStatus(FixStatus.FAILED);
        int conflict = plan.countByStatus(FixStatus.CONFLICT);
        int skipped = plan.countByStatus(FixStatus.SKIPPED);
        StringBuilder sb = new StringBuilder();
        sb.append(applied).append(" applied");
        if (failed > 0) sb.append(", ").append(failed).append(" failed");
        if (conflict > 0) sb.append(", ").append(conflict).append(" conflicts");
        if (skipped > 0) sb.append(", ").append(skipped).append(" skipped");
        JLabel statusLabel = new JLabel(sb.toString());
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        if (plan.hasAppliedEntries()) {
            GradientAccentButton rollbackBtn = GradientAccentButton.primary(
                    "Rollback All", AccentColors.RED_HEX);
            rollbackBtn.setToolTipText("Undo all applied changes (reverses in order)");
            rollbackBtn.addActionListener(e -> {
                int choice = JOptionPane.showConfirmDialog(this,
                        "Roll back all applied fixes?",
                        "Confirm Rollback",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (choice == JOptionPane.YES_OPTION) {
                    int rolled = fixService.rollback(plan);
                    tableModel.fireTableDataChanged();
                    statusLabel.setText(rolled + " rolled back");
                    rollbackBtn.setEnabled(false);
                }
            });
            buttonPanel.add(rollbackBtn);
        }

        GradientAccentButton closeBtn = GradientAccentButton.neutral("Close");
        closeBtn.addActionListener(e -> dispose());
        buttonPanel.add(closeBtn);

        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ── Table model ────────────────────────────────────────────────────────

    private static final class ResultTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "Element", "Action", "Description", "Status", "Error"
        };

        private final List<FixEntry> entries;

        ResultTableModel(List<FixEntry> entries) {
            this.entries = entries;
        }

        @Override public int getRowCount() { return entries.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }
        @Override public Class<?> getColumnClass(int col) { return String.class; }

        @Override
        public Object getValueAt(int row, int col) {
            FixEntry entry = entries.get(row);
            switch (col) {
                case 0: return entry.action().elementName();
                case 1: return entry.action().actionType().name();
                case 2: return entry.action().description();
                case 3: return FixUiSupport.statusLabel(entry.status());
                case 4: return entry.errorMessage() != null ? entry.errorMessage() : "";
                default: return "";
            }
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
                } else if ("Rolled Back".equals(status) || "Skipped".equals(status)) {
                    setForeground(AccentColors.mutedText());
                } else {
                    setForeground(table.getForeground());
                }
            }
            return this;
        }
    }
}