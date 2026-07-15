// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/ui/ResultsTablePanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;

/**
 * Panel containing a JTable that displays rule evaluation failures.
 * Supports sorting and a quick filter text field.
 */
public final class ResultsTablePanel extends JPanel {

    private static final String[] COLUMNS = {"Rule ID", "Element Name", "Location"};

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextField filterField;
    private final JLabel statusLabel;
    private TableRowSorter<DefaultTableModel> sorter;

    public ResultsTablePanel() {
        setLayout(new BorderLayout(5, 5));

        // Filter bar
        JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
        filterPanel.add(new JLabel("  Filter: "), BorderLayout.WEST);
        filterField = new JTextField();
        filterField.setToolTipText("Type to filter results");
        filterField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilter(); }
        });
        filterPanel.add(filterField, BorderLayout.CENTER);

        // Table
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);

        // Set column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        JScrollPane scrollPane = new JScrollPane(table);

        // Status bar
        statusLabel = new JLabel("  No results");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        add(filterPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Populate the table with FAIL results.
     */
    public void loadResults(List<RuleResult> results, ElementIndex index) {
        tableModel.setRowCount(0);
        int failCount = 0;

        for (RuleResult r : results) {
            if (r.status() != RuleStatus.FAIL) continue;

            String elementName = index.repository().get(r.elementGuid())
                    .map(ElementRecord::name).orElse("<unknown>");
            String path = index.repository().get(r.elementGuid())
                    .flatMap(ElementRecord::ownerPath).orElse("");
            String readablePath = path.isEmpty()
                    ? elementName + " (project root)"
                    : path.replace("::", " > ") + " > " + elementName;

            tableModel.addRow(new Object[]{r.ruleId(), elementName, readablePath});
            failCount++;
        }

        statusLabel.setText("  Failures: " + failCount);
        filterField.setText("");
    }

    /**
     * Clear the table.
     */
    public void clear() {
        tableModel.setRowCount(0);
        statusLabel.setText("  No results");
        filterField.setText("");
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text));
        }
    }
}
