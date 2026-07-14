
package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Formats rule evaluation results into human-readable output.
 * Transforms GUIDs, paths, and technical details into clear, readable text.
 */
public final class ResultFormatter {

    private final ElementIndex index;

    public ResultFormatter(ElementIndex index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    /**
     * Format a single result into a readable line.
     */
    public String format(RuleResult result) {
        Optional<ElementRecord> rec = index.repository().get(result.elementGuid());
        String name = rec.map(ElementRecord::name).orElse("<unknown>");
        String path = rec.flatMap(ElementRecord::ownerPath).orElse("");
        String kind = rec.map(r -> r.kind().name()).orElse("?");
        String stereo = rec.map(r -> r.stereotypes().isEmpty() ? "" : " " + r.stereotypes().toString())
                .orElse("");

        String readablePath = formatPath(path, name);

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(result.status()).append("] ");
        sb.append(result.ruleId());
        sb.append("\n    Element:  ").append(name).append(" (").append(kind).append(")").append(stereo);
        sb.append("\n    Location: ").append(readablePath);
        if (result.status() == RuleStatus.FAIL || result.status() == RuleStatus.SKIPPED) {
            sb.append("\n    Reason:   ").append(result.message());
        }
        return sb.toString();
    }

    /**
     * Format all results grouped by status, then by rule.
     */
    public String formatSummary(RuleEngine.EvaluationSummary summary) {
        StringBuilder sb = new StringBuilder();

        sb.append("╔══════════════════════════════════════════════╗\n");
        sb.append("║           RULE EVALUATION REPORT            ║\n");
        sb.append("╠══════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total checks:  %-27d ║\n", summary.totalCount()));
        sb.append(String.format("║  ✓ Passed:      %-27d ║\n", summary.passCount()));
        sb.append(String.format("║  ✗ Failed:      %-27d ║\n", summary.failCount()));
        sb.append(String.format("║  ○ Skipped:     %-27d ║\n", summary.skipCount()));
        sb.append("╚══════════════════════════════════════════════╝\n");

        // Fail count per rule
        if (!summary.failed().isEmpty()) {
            sb.append("\n── Failures per rule ──────────────────────────\n");
            Map<String, Long> failsByRule = summary.failed().stream()
                    .collect(Collectors.groupingBy(RuleResult::ruleId,
                            LinkedHashMap::new, Collectors.counting()));
            for (Map.Entry<String, Long> entry : failsByRule.entrySet()) {
                sb.append(String.format("  ✗ %-35s %d\n", entry.getKey(), entry.getValue()));
            }
        }

        // Pass count per rule
        if (!summary.passed().isEmpty()) {
            sb.append("\n── Passes per rule ───────────────────────────\n");
            Map<String, Long> passByRule = summary.passed().stream()
                    .collect(Collectors.groupingBy(RuleResult::ruleId,
                            LinkedHashMap::new, Collectors.counting()));
            for (Map.Entry<String, Long> entry : passByRule.entrySet()) {
                sb.append(String.format("  ✓ %-35s %d\n", entry.getKey(), entry.getValue()));
            }
        }

        // Detailed failures grouped by rule
        if (!summary.failed().isEmpty()) {
            sb.append("\n══════════════════════════════════════════════\n");
            sb.append("  DETAILED FAILURES\n");
            sb.append("══════════════════════════════════════════════\n");

            Map<String, List<RuleResult>> groupedFails = summary.failed().stream()
                    .collect(Collectors.groupingBy(RuleResult::ruleId,
                            LinkedHashMap::new, Collectors.toList()));

            for (Map.Entry<String, List<RuleResult>> entry : groupedFails.entrySet()) {
                sb.append("\n┌─ Rule: ").append(entry.getKey());
                sb.append(" (").append(entry.getValue().size()).append(" failures) ─┐\n");

                for (RuleResult r : entry.getValue()) {
                    Optional<ElementRecord> rec = index.repository().get(r.elementGuid());
                    String name = rec.map(ElementRecord::name).orElse("<unknown>");
                    String path = rec.flatMap(ElementRecord::ownerPath).orElse("");
                    String kind = rec.map(e -> e.kind().name()).orElse("?");

                    String readablePath = formatPath(path, name);

                    sb.append("│\n");
                    sb.append("│  Element:  ").append(name).append(" (").append(kind).append(")\n");
                    sb.append("│  Location: ").append(readablePath).append("\n");
                    sb.append("│  Reason:   ").append(r.message()).append("\n");
                }
                sb.append("└──────────────────────────────────────────┘\n");
            }
        }

        // Skipped (if any)
        if (!summary.skipped().isEmpty()) {
            sb.append("\n── Skipped (").append(summary.skipCount()).append(") ──\n");
            for (RuleResult r : summary.skipped()) {
                Optional<ElementRecord> rec = index.repository().get(r.elementGuid());
                String name = rec.map(ElementRecord::name).orElse("<unknown>");
                sb.append("  ○ ").append(r.ruleId())
                        .append(" | ").append(name)
                        .append(" | ").append(r.message()).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Transform a raw owner path into a human-readable location.
     * Example:
     *   "L2a_System::VDU::Solution_space::Logical_VP::SE_VDU::Elements"
     *   becomes:
     *   "L2a_System > VDU > Solution_space > Logical_VP > SE_VDU > Elements"
     */
    private String formatPath(String ownerPath, String elementName) {
        if (ownerPath == null || ownerPath.isEmpty()) {
            return elementName + " (project root)";
        }
        String readable = ownerPath.replace("::", " > ");
        return readable + " > " + elementName;
    }
}
