package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel;

import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.CheckboxListField;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help.HelpIcon;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleFormLayout;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.RuleValueCodec;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class RelationExistsPanel implements RuleParamPanel {

    private static final String[] RELATION_COUNT_MODES =
            {"At least", "Exactly", "At most", "More than", "Fewer than"};
    private static final List<String> RELATION_KINDS = Arrays.asList(
            "any", "dependency", "association", "generalization",
            "usage", "realization", "abstraction", "link");

    private CheckboxListField relationKind;
    private final JComboBox<String> relationDirection =
            new JComboBox<String>(new String[]{"any", "outgoing", "incoming"});
    private CheckboxListField relationStereotypes;
    private final JComboBox<String> relationCountMode =
            new JComboBox<String>(RELATION_COUNT_MODES);
    private final JSpinner relationCount =
            new JSpinner(new SpinnerNumberModel(1, 0, 9999, 1));

    private final JPanel root;

    public RelationExistsPanel(List<String> detectedStereotypes, Runnable onChange) {
        root = new JPanel(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = RuleFormLayout.constraints();

        relationKind = PanelSupport.addSelection(
                root, gbc, "Relation Kind:", "rule.params.relationExists.relationKind",
                RELATION_KINDS, 0, 0.5, onChange);
        RuleFormLayout.addRow(root, gbc, 2, "Direction",
                "rule.params.relationExists.direction", relationDirection);
        relationStereotypes = PanelSupport.addSelection(
                root, gbc, "Relation Stereotypes:",
                "rule.params.relationExists.relationStereotypes",
                detectedStereotypes != null ? detectedStereotypes : Collections.<String>emptyList(),
                3, 0.5, onChange);

        JPanel count = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        count.add(new JLabel("Require"));
        count.add(relationCountMode);
        count.add(relationCount);
        count.add(new JLabel("relation(s)"));
        RuleFormLayout.addFullWidth(root, gbc, 5,
                HelpIcon.labelWithHelp("Required Count:",
                        "rule.params.relationExists.count"));
        RuleFormLayout.addFullWidth(root, gbc, 6, count);
    }

    @Override public JComponent component() { return root; }

    @Override
    public Map<String, Object> buildParams() {
        Map<String, Object> params = new LinkedHashMap<String, Object>();
        PanelSupport.putFirst(params, "relationKind", relationKind);
        params.put("direction", relationDirection.getSelectedItem());
        if (!PanelSupport.empty(relationStereotypes)) {
            params.put("relationStereotypes",
                    relationStereotypes.getSelectedValues());
        }
        params.put("operator", RuleValueCodec.countModeToOperator(
                (String) relationCountMode.getSelectedItem()));
        params.put("value", ((Number) relationCount.getValue()).intValue());
        return params;
    }

    @Override
    public void prefill(Map<String, Object> params) {
        if (params == null) return;
        PanelSupport.selectOne(relationKind, params.get("relationKind"));
        if (params.get("direction") != null) {
            relationDirection.setSelectedItem(params.get("direction").toString());
        }
        PanelSupport.selectMany(relationStereotypes, params.get("relationStereotypes"));
        relationCountMode.setSelectedItem(
                RuleValueCodec.operatorToCountMode(
                        PanelSupport.text(params, "operator", "gte")));
        if (params.get("value") != null) {
            try {
                relationCount.setValue(
                        Integer.parseInt(params.get("value").toString()));
            } catch (NumberFormatException ignored) {}
        }
    }
}