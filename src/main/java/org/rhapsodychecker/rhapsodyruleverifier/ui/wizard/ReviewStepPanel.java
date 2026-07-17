// ui/wizard/ReviewStepPanel.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;

import javax.swing.*;
import java.awt.*;

/**
 * Pasul 5: sumar al configurației înainte de salvare.
 * refresh() trebuie apelat de WizardDialog înainte de a afișa pasul.
 */
public final class ReviewStepPanel extends JPanel {

    private final WizardState state;
    private final JTextArea   summaryArea = new JTextArea();

    public ReviewStepPanel(WizardState state) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        build();
    }

    private void build() {
        JLabel title = new JLabel("Review Configuration");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
        add(title, BorderLayout.NORTH);

        summaryArea.setEditable(false);
        summaryArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        summaryArea.setLineWrap(false);
        add(new JScrollPane(summaryArea), BorderLayout.CENTER);

        add(new JLabel("  Click \"Save & Close\" to write the YAML file."),
                BorderLayout.SOUTH);
    }

    /**
     * Apelat de WizardDialog la fiecare intrare pe pasul Review,
     * pentru a afișa starea curenta a wizard-ului.
     */
    public void refresh() {
        StringBuilder sb = new StringBuilder();

        sb.append("Mode: ").append(state.mode()).append("\n");
        sb.append("Scope: ")
          .append(state.scopePath().isEmpty() ? "(entire model)" : state.scopePath())
          .append("\n\n");

        // ── Aliases ───────────────────────────────────────────────────────────
        sb.append("=== Aliases (").append(state.aliases().size()).append(") ===\n");
        if (state.aliases().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (AliasDefinition a : state.aliases()) {
                sb.append("  ").append(a.id())
                  .append("  [").append(a.kind().name().toLowerCase()).append("]");
                a.title().ifPresent(t -> sb.append("  \"").append(t).append("\""));
                sb.append("\n");
            }
        }

        // ── Element Sets ──────────────────────────────────────────────────────
        sb.append("\n=== Element Sets (").append(state.sets().size()).append(") ===\n");
        if (state.sets().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (ElementSetDefinition s : state.sets()) {
                sb.append("  ").append(s.id());
                if (!s.types().isEmpty())       sb.append("  types=").append(s.types());
                if (!s.stereotypes().isEmpty()) sb.append("  stereos=").append(s.stereotypes());
                if (!s.includePackages().isEmpty()) sb.append("  include=").append(s.includePackages());
                if (!s.excludePackages().isEmpty()) sb.append("  exclude=").append(s.excludePackages());
                sb.append("\n");
            }
        }

        // ── Rules ─────────────────────────────────────────────────────────────
        sb.append("\n=== Rules (").append(state.rules().size()).append(") ===\n");
        if (state.rules().isEmpty()) {
            sb.append("  (none)\n");
        } else {
            for (WizardState.RuleRequest r : state.rules()) {
                sb.append("  ").append(r.id())
                  .append("  [").append(r.ruleType()).append("]");
                if (r.elementSetId()  != null) sb.append("  → ").append(r.elementSetId());
                if (r.targetAliasId() != null) sb.append("  target=").append(r.targetAliasId());
                if (!r.params().isEmpty())      sb.append("  params=").append(r.params());
                sb.append("\n");
            }
        }

        summaryArea.setText(sb.toString());
        summaryArea.setCaretPosition(0);
    }
}
