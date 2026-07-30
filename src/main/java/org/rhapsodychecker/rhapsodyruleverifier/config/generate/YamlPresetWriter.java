// config/generate/YamlPresetWriter.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Serializes WizardState to a YAML file compatible with ConfigLoader.
 * Manual writing (no Jackson/SnakeYAML) to control formatting.
 */
public final class YamlPresetWriter {

    private YamlPresetWriter() {}

    public static void write(WizardState state, String outputPath) throws IOException {
        List<String> lines = new ArrayList<>();

        lines.add("schemaVersion: 1");
        lines.add("mode: " + state.mode());
        lines.add("");

        // ── aliases ───────────────────────────────────────────────────────────
        if (!state.aliases().isEmpty()) {
            lines.add("aliases:");
            for (AliasDefinition alias : state.aliases()) {
                writeAlias(alias, lines);
            }
            lines.add("");
        }

        // ── elementSets ───────────────────────────────────────────────────────
        if (!state.sets().isEmpty()) {
            lines.add("elementSets:");
            for (ElementSetDefinition set : state.sets()) {
                writeElementSet(set, lines);
            }
            lines.add("");
        }

        // ── rules ─────────────────────────────────────────────────────────────
        if (!state.rules().isEmpty()) {
            lines.add("rules:");
            for (WizardState.RuleRequest rule : state.rules()) {
                writeRule(rule, lines);
            }
        }

        Files.write(Paths.get(outputPath), lines, StandardCharsets.UTF_8);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressWarnings("incomplete-switch")
	private static void writeAlias(AliasDefinition a, List<String> out) {
        out.add("  " + a.id() + ":");
        out.add("    kind: " + kindYaml(a.kind()));
        a.title().ifPresent(t      -> out.add("    title: \"" + escape(t) + "\""));
        a.help().ifPresent(h       -> out.add("    help: \"" + escape(h) + "\""));

        switch (a.kind()) {
            case TAGGED_VALUE:
                a.profileName().ifPresent(p    -> out.add("    profileName: " + p));
                a.tagName().ifPresent(t        -> out.add("    tagName: " + t));
                a.stereotypeName().ifPresent(s -> out.add("    stereotypeName: " + s));
                out.add("    type: " + a.valueType().name().toLowerCase());
                if (!a.values().isEmpty()) {
                    out.add("    values: " + toInlineList(a.values()));
                }
                break;
            case STEREOTYPE:
                a.stereotypeName().ifPresent(s -> out.add("    stereotypeName: " + s));
                break;
            case STEREOTYPE_SET:
                a.profileName().ifPresent(p -> out.add("    profileName: " + p));
                if (!a.stereotypeNames().isEmpty()) {
                    out.add("    stereotypeNames: " + toInlineList(a.stereotypeNames()));
                }
                break;
            case DESCRIPTION:
            case NAME:
                // no extra fields
                break;
        }
    }

    private static void writeElementSet(ElementSetDefinition s, List<String> out) {
        out.add("  " + s.id() + ":");
        s.title().ifPresent(t -> out.add("    title: \"" + escape(t) + "\""));
        if (!s.kinds().isEmpty())           out.add("    kinds: " + toInlineList(s.kinds()));
        if (!s.types().isEmpty())           out.add("    types: " + toInlineList(s.types()));
        if (!s.stereotypes().isEmpty())     out.add("    stereotypes: " + toInlineList(s.stereotypes()));
        if (!s.includePackages().isEmpty()) out.add("    includePackages: " + toInlineList(s.includePackages()));
        if (!s.excludePackages().isEmpty()) out.add("    excludePackages: " + toInlineList(s.excludePackages()));
    }

    private static void writeRule(WizardState.RuleRequest r, List<String> out) {
        out.add("  - id: " + r.id());
        if (r.title() != null && !r.title().isEmpty()) {
            out.add("    title: \"" + escape(r.title()) + "\"");
        }
        out.add("    type: " + r.ruleType());

        if (r.group() != null && !r.group().isEmpty()) {
            out.add("    group: " + r.group());
        }

        if (r.elementSetId() != null) {
            out.add("    appliesTo: { set: " + r.elementSetId() + " }");
        }
        if (r.targetAliasId() != null) {
            out.add("    target: " + r.targetAliasId());
        }
        if (!r.params().isEmpty()) {
            out.add("    params:");
            for (Map.Entry<String, Object> e : r.params().entrySet()) {
                out.add("      " + e.getKey() + ": " + paramValueYaml(e.getValue()));
            }
        }
        if (r.message() != null && !r.message().isEmpty()) {
            out.add("    message: \"" + escape(r.message()) + "\"");
        }
        if (!r.isEnabled()) {
            out.add("    enabled: false");
        }
        out.add("");
    }

    // ── YAML formatting ────────────────────────────────────────────────────────

    private static String kindYaml(AliasKind kind) {
        switch (kind) {
            case TAGGED_VALUE:   return "taggedValue";
            case STEREOTYPE:     return "stereotype";
            case STEREOTYPE_SET: return "stereotypeSet";
            case DESCRIPTION:    return "description";
            case NAME:           return "name";
            default:             return kind.name().toLowerCase();
        }
    }

    private static String toInlineList(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(", ");
            String val = items.get(i);
            // Quote values that YAML might misinterpret as non-string types
            // (integers, floats, booleans, or values with special characters)
            if (needsQuoting(val)) {
                sb.append("\"").append(escape(val)).append("\"");
            } else {
                sb.append(val);
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Returns true if the value needs YAML quoting to prevent type coercion.
     * Values like "1", "1.0", "true", "yes", "1..1" get quoted to stay as strings.
     */
    private static boolean needsQuoting(String val) {
        if (val == null || val.isEmpty()) return true;
        // Numeric values
        try { Integer.parseInt(val); return true; } catch (NumberFormatException e) { /* not int */ }
        try { Double.parseDouble(val); return true; } catch (NumberFormatException e) { /* not double */ }
        // YAML boolean words
        String lower = val.toLowerCase();
        if ("true".equals(lower) || "false".equals(lower)
                || "yes".equals(lower) || "no".equals(lower)
                || "on".equals(lower) || "off".equals(lower)) return true;
        // Contains dots between digits (e.g. "1..1") — YAML might parse oddly
        if (val.contains("..")) return true;
        return false;
    }

    private static String paramValueYaml(Object value) {
        if (value == null) return "";
        if (value instanceof List) {
            // Safely convert all items to strings (List may contain Integer/String mix)
            List<String> stringList = new ArrayList<>();
            for (Object item : (List<?>) value) {
                stringList.add(item != null ? item.toString() : "");
            }
            return toInlineList(stringList);
        }
        if (value instanceof Boolean || value instanceof Integer) {
            return value.toString();
        }
        return value.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}