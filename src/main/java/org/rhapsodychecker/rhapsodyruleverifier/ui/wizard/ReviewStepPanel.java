// ui/wizard/ReviewStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState.RuleRequest;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.ui.style.SectionHeader;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Pasul final de review:
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

    public void refresh() {
        removeAll();
        ruleCheckBoxes.clear();
        rebuild();
        revalidate();
        repaint();
    }

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

        content.add(buildSetsSection());
        content.add(Box.createVerticalStrut(6));
        content.add(buildRulesSection());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildSetsSection() {
        JPanel p = sectionPanel("Element Sets (" + state.sets().size() + ")");
        JPanel content = sectionContent();
        p.add(content, BorderLayout.CENTER);

        if (state.sets().isEmpty()) {
            content.add(readOnly("(none)"));
        } else {
            for (ElementSetDefinition set : state.sets()) {
                int filterCount = set.kinds().size() + set.types().size() + set.stereotypes().size();
                content.add(readOnly(set.id() + "  —  " + filterCount + " filter(s)"));
            }
        }
        return p;
    }


    private JPanel buildRulesSection() {
        JPanel p = sectionPanel("Rules (" + state.rules().size() + ")  —  uncheck to disable");
        JPanel content = sectionContent();
        p.add(content, BorderLayout.CENTER);

        if (state.rules().isEmpty()) {
            content.add(readOnly("(none)"));
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
                content.add(cb);
            }
        }
        return p;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static JPanel sectionPanel(String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new SectionHeader(title), BorderLayout.NORTH);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel sectionContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        return content;
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