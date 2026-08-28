package org.rhapsodychecker.rhapsodyruleverifier.ui.fix;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
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
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Modal dialog showing past fix sessions parsed from journal files.
 * Allows the user to view session details and rollback a session's applied fixes.
 */
public final class FixHistoryDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final FixService fixService;
    private final List<SessionSummary> sessions;
    private final SessionTableModel tableModel;
    private final JTable table;
    private final JLabel statusLabel;

    public FixHistoryDialog(JFrame owner, FixService fixService) {
        super(owner, "Fix Log", true);
        this.fixService = fixService;

        setSize(850, 450);
        setMinimumSize(new Dimension(650, 350));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(0, 0));

        // Header
        add(new SectionHeader("Fix Session History"), BorderLayout.NORTH);

        // Load sessions
        sessions = loadSessions();
        tableModel = new SessionTableModel(sessions);
        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new SessionCellRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createCompoundBorder(
                AppTheme.edgeBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));

        statusLabel = new JLabel(sessions.size() + " session(s) found");
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        GradientAccentButton rollbackBtn = GradientAccentButton.primary(
                "Rollback This Session", AccentColors.RED_HEX);
        rollbackBtn.setToolTipText("Undo all applied changes from the selected session");
        rollbackBtn.addActionListener(e -> onRollbackSelected());

        GradientAccentButton openFolderBtn = GradientAccentButton.neutral("Open Folder");
        openFolderBtn.setToolTipText("Open the journal folder in Explorer (for advanced users)");
        openFolderBtn.addActionListener(e -> onOpenFolder());

        GradientAccentButton closeBtn = GradientAccentButton.neutral("Close");
        closeBtn.addActionListener(e -> dispose());

        buttonPanel.add(rollbackBtn);
        buttonPanel.add(openFolderBtn);
        buttonPanel.add(closeBtn);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private List<SessionSummary> loadSessions() {
        List<SessionSummary> result = new ArrayList<SessionSummary>();
        try {
            List<File> journals = fixService.listJournals();
            for (File f : journals) {
                try {
                    FixPlan plan = fixService.loadJournal(f);
                    result.add(new SessionSummary(f, plan));
                } catch (Exception ex) {
                    AppLogger.warn("Skipping unreadable journal: " + f.getName()
                            + " (" + ex.getMessage() + ")");
                }
            }
        } catch (Exception ex) {
            AppLogger.warn("Could not list journals: " + ex.getMessage());
        }
        return result;
    }

    private void onRollbackSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                    "Select a session to rollback.", "No Selection",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        SessionSummary session = sessions.get(row);
        if (session.applied == 0) {
            JOptionPane.showMessageDialog(this,
                    "This session has no applied entries to rollback.",
                    "Nothing to Rollback", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int choice = JOptionPane.showConfirmDialog(this,
                "WARNING: This will attempt to reverse " + session.applied
                + " applied fix(es) from session\n"
                + session.timestamp + ".\n\n"
                + "Rollback requires a live Rhapsody connection and that the\n"
                + "target elements still exist. Proceed?",
                "Confirm Rollback",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice != JOptionPane.YES_OPTION) return;

        try {
            FixPlan plan = fixService.loadJournal(session.file);
            int rolled = fixService.rollback(plan);
            statusLabel.setText(rolled + " entries rolled back");

            // Refresh the session row
            session.refresh(plan);
            tableModel.fireTableRowsUpdated(row, row);

            JOptionPane.showMessageDialog(this,
                    rolled + " fix(es) rolled back successfully.",
                    "Rollback Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            statusLabel.setText("Rollback failed: " + ex.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Rollback failed:\n" + ex.getMessage(),
                    "Rollback Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onOpenFolder() {
        try {
            File journalDir = new File(new File(System.getProperty("java.io.tmpdir"),
                    ".rhapsody-logs"), "fix-journals");
            if (!journalDir.exists()) journalDir.mkdirs();
            Desktop.getDesktop().open(journalDir);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open folder:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ── Session summary ────────────────────────────────────────────────────

    private static final class SessionSummary {
        final File file;
        String timestamp;
        int total;
        int applied;
        int failed;
        int rolledBack;
        int skipped;

        SessionSummary(File file, FixPlan plan) {
            this.file = file;
            refresh(plan);
        }

        void refresh(FixPlan plan) {
            this.timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new Date(plan.timestamp()));
            this.total = plan.entries().size();
            this.applied = plan.countByStatus(FixStatus.APPLIED);
            this.failed = plan.countByStatus(FixStatus.FAILED);
            this.rolledBack = plan.countByStatus(FixStatus.ROLLED_BACK);
            this.skipped = plan.countByStatus(FixStatus.SKIPPED);
        }

        String statusSummary() {
            StringBuilder sb = new StringBuilder();
            if (applied > 0) sb.append(applied).append(" applied");
            if (failed > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(failed).append(" failed");
            }
            if (rolledBack > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(rolledBack).append(" rolled back");
            }
            if (skipped > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(skipped).append(" skipped");
            }
            if (sb.length() == 0) sb.append("no actions");
            return sb.toString();
        }
    }

    // ── Table model ────────────────────────────────────────────────────────

    private static final class SessionTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "Timestamp", "Total", "Applied", "Failed", "Rolled Back", "Summary"
        };

        private final List<SessionSummary> sessions;

        SessionTableModel(List<SessionSummary> sessions) {
            this.sessions = sessions;
        }

        @Override public int getRowCount() { return sessions.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Object getValueAt(int row, int col) {
            SessionSummary s = sessions.get(row);
            switch (col) {
                case 0: return s.timestamp;
                case 1: return s.total;
                case 2: return s.applied;
                case 3: return s.failed;
                case 4: return s.rolledBack;
                case 5: return s.statusSummary();
                default: return "";
            }
        }
    }

    // ── Cell renderer ──────────────────────────────────────────────────────

    private static final class SessionCellRenderer extends DefaultTableCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected && column == 3) {
                // Failed column — highlight non-zero
                if (value instanceof Integer && ((Integer) value) > 0) {
                    setForeground(AccentColors.failure());
                } else {
                    setForeground(table.getForeground());
                }
            } else if (!isSelected && column == 2) {
                // Applied column — green for non-zero
                if (value instanceof Integer && ((Integer) value) > 0) {
                    setForeground(new Color(0x21, 0x73, 0x46));
                } else {
                    setForeground(table.getForeground());
                }
            } else if (!isSelected) {
                setForeground(table.getForeground());
            }
            return this;
        }
    }
}