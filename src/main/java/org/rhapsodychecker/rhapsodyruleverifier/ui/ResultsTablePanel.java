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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ResultsTablePanel extends JPanel {

    private static final String[] COLUMNS = {"Rule ID", "Element Name", "Location"};

    private final JTable table;
    private final DefaultTableModel tableModel;
    private final JTextField filterField;
    private final JLabel statusLabel;
    private TableRowSorter<DefaultTableModel> sorter;

    // Swaps between the results table and a placeholder message shown before
    // any evaluation has been run yet.
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel     centerPanel  = new JPanel(centerLayout);
    private static final String CARD_TABLE       = "table";
    private static final String CARD_PLACEHOLDER = "placeholder";

    // Store GUIDs parallel to table rows (model index -> GUID)
    private final List<String> rowGuids = new ArrayList<>();

    // Callback for double-click navigation
    private Consumer<String> onElementDoubleClick;

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

        table.getColumnModel().getColumn(0).setPreferredWidth(250);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        // Double-click listener
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onElementDoubleClick != null) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    // Convert view index to model index (handles sorting/filtering)
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    if (modelRow >= 0 && modelRow < rowGuids.size()) {
                        String guid = rowGuids.get(modelRow);
                        onElementDoubleClick.accept(guid);
                    }
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);

        // Placeholder shown before any evaluation has been run
        JLabel placeholderLabel = new JLabel(
                "<html><div style='text-align:center;'>No output. Run a config file to see its output here.</div></html>",
                SwingConstants.CENTER);
        placeholderLabel.setForeground(Color.GRAY);
        JPanel placeholderPanel = new JPanel(new GridBagLayout());
        placeholderPanel.add(placeholderLabel);

        centerPanel.add(scrollPane, CARD_TABLE);
        centerPanel.add(placeholderPanel, CARD_PLACEHOLDER);
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);

        // Status bar
        statusLabel = new JLabel("  No results");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));

        add(filterPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);
    }

    /**
     * Set callback for when a user double-clicks a result row.
     * The callback receives the element GUID.
     */
    public void setOnElementDoubleClick(Consumer<String> callback) {
        this.onElementDoubleClick = callback;
    }

    public void loadResults(List<RuleResult> results, ElementIndex index) {
        tableModel.setRowCount(0);
        rowGuids.clear();
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
            rowGuids.add(r.elementGuid());
            failCount++;
        }

        statusLabel.setText("  Failures: " + failCount + "  (double-click to navigate in Rhapsody)");
        filterField.setText("");
        centerLayout.show(centerPanel, CARD_TABLE);
    }

    public void clear() {
        tableModel.setRowCount(0);
        rowGuids.clear();
        statusLabel.setText("  No results");
        filterField.setText("");
        centerLayout.show(centerPanel, CARD_PLACEHOLDER);
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