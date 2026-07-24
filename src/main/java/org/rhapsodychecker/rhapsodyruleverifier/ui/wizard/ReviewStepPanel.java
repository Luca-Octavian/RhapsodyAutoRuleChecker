// ui/wizard/ReviewStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState.RuleRequest;
import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;


import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pasul final de review:
 *  - Scope + Mode (text read-only)
 *  - Aliases (text read-only)
 *  - Element Sets (text read-only)
 *  - Rules — câte un JCheckBox per regulă; debifat = disabled
 */
public class ReviewStepPanel extends JPanel {

    private final WizardState state;

    // checkbox-urile corespund 1:1 cu state.rules()
    private final List<JCheckBox> ruleCheckBoxes = new ArrayList<>();

    public ReviewStepPanel(WizardState state) {
        this.state = state;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        rebuild();
    }

    // ── API public ────────────────────────────────────────────────────────────

    /**
     * Apelat de WizardDialog înainte de a afișa panelul.
     * Reconstruiește întregul conținut pe baza stării curente.
     */
    public void refresh() {
        removeAll();
        ruleCheckBoxes.clear();
        rebuild();
        revalidate();
        repaint();
    }

    /**
     * Sincronizează starea checkbox-urilor înapoi în WizardState.
     * Apelat de WizardDialog înainte de Finish / Save.
     */
    public void applyToState() {
        List<RuleRequest> rules = state.rules();
        for (int i = 0; i < ruleCheckBoxes.size() && i < rules.size(); i++) {
            state.setRuleEnabled(i, ruleCheckBoxes.get(i).isSelected());
        }
    }

    // ── Construcție UI ────────────────────────────────────────────────────────

    private void rebuild() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildScopeSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildAliasesSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildSetsSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildRulesSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildScopeSection() {
        JPanel p = titledPanel("Configuration");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.add(scopeRow("Mode:", state.mode()));
        return p;
    }

    private JPanel scopeRow(String labelText, String value) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label(labelText));
        row.add(readOnly(value));
        return row;
    }

    private JPanel buildAliasesSection() {
        JPanel p = titledPanel("Aliases (" + state.aliases().size() + ")");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        if (state.aliases().isEmpty()) {
            p.add(readOnly("(none)"));
        } else {
            for (AliasDefinition alias : state.aliases()) {
                p.add(readOnly(alias.id() + "  →  " + alias.kind()));
            }
        }
        return p;
    }

    private JPanel buildSetsSection() {
        JPanel p = titledPanel("Element Sets (" + state.sets().size() + ")");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        if (state.sets().isEmpty()) {
            p.add(readOnly("(none)"));
        } else {
            for (ElementSetDefinition set : state.sets()) {
                int filterCount = set.kinds().size() + set.types().size() + set.stereotypes().size();
                p.add(readOnly(set.id() + "  —  " + filterCount + " filter(s)"));
            }
        }
        return p;
    }


    private JPanel buildRulesSection() {
        JPanel p = titledPanel("Rules (" + state.rules().size() + ")  —  uncheck to disable");
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        if (state.rules().isEmpty()) {
            p.add(readOnly("(none)"));
        } else {
            List<RuleRequest> rules = state.rules();
            for (int i = 0; i < rules.size(); i++) {
                RuleRequest r = rules.get(i);
                JCheckBox cb = new JCheckBox(r.toString(), r.isEnabled());
                cb.setToolTipText("Type: " + r.ruleType()
                        + "  |  Set: " + r.elementSetId()
                        + (r.message() != null && !r.message().trim().isEmpty()
                                ? "  |  " + r.message() : ""));
                cb.setAlignmentX(Component.LEFT_ALIGNMENT);
                ruleCheckBoxes.add(cb);
                p.add(cb);
            }
        }
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JPanel titledPanel(String title) {
        JPanel p = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                // BoxLayout never stretches a child wider than its preferred
                // size unless maximumSize says otherwise — without this
                // override, a section with just 1-2 short rows shrinks to a
                // narrow box instead of filling the available width.
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        p.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel label(String text) {
        return new JLabel(text);
    }

    private static JTextField readOnly(String text) {
        JTextField tf = new JTextField(text);
        tf.setEditable(false);
        tf.setBorder(null);
        tf.setBackground(null);
        tf.setOpaque(false);
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        return tf;
    }
}