// config/generate/ConfigToWizardStateMapper.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Converteste un RuleCheckerConfig existent → WizardState.
 * Folosit de "Edit in Wizard" pentru a pre-popula wizard-ul
 * dintr-un YAML incarcat anterior.
 *
 * Daca FastDetectionResult e disponibil, marcheaza in warnings
 * elementele care nu sunt detectate in modelul curent.
 * Daca nu e disponibil (model neîncarcat), importa totul fara validare.
 */
public final class ConfigToWizardStateMapper {

    private ConfigToWizardStateMapper() {}

    /**
     * Convenience overload for callers that don't have (or don't care about)
     * the original file path.
     */
    public static MappingResult map(RuleCheckerConfig config, FastDetectionResult fast) {
        return map(config, fast, null);
    }

    /**
     * @param sourcePath path of the YAML file this config was loaded from,
     *                    so "Edit in Wizard" can save back to it directly
     *                    without re-prompting. Pass null if unknown.
     */
    public static MappingResult map(RuleCheckerConfig config, FastDetectionResult fast, String sourcePath) {
        WizardState  state    = new WizardState();
        state.sourcePath(sourcePath);
        List<String> warnings = new ArrayList<>();

        // ── Element Sets ──────────────────────────────────────────────────────
        for (ElementSetDefinition set : config.elementSets().values()) {
            state.addSet(set);

            if (fast != null) {
                // Build case-insensitive lookup of detected stereotypes
                Set<String> detectedStereosLower = new HashSet<String>();
                for (String s : fast.countsByStereotype().keySet()) {
                    detectedStereosLower.add(s.toLowerCase());
                }

                // Stereotypes that come from kind-to-stereotype auto-translation
                // are structural markers, not real model stereotypes — skip them
                Set<String> kindTranslatedLower = new HashSet<String>();
                for (String k : set.kinds()) {
                    for (String translated : translateKindsToStereotypes(
                            Collections.singletonList(k))) {
                        kindTranslatedLower.add(translated.toLowerCase());
                    }
                }

                for (String stereo : set.stereotypes()) {
                    if (kindTranslatedLower.contains(stereo.toLowerCase())) continue;
                    if (!detectedStereosLower.contains(stereo.toLowerCase())) {
                        warnings.add("ElementSet '" + set.id() + "': stereotype '"
                                + stereo + "' not detected in current model.");
                    }
                }

                Set<String> detectedMetaLower = new HashSet<String>();
                for (String m : fast.countsByMetaClass().keySet()) {
                    detectedMetaLower.add(m.toLowerCase());
                }
                for (String type : set.types()) {
                    if (!detectedMetaLower.contains(type.toLowerCase())) {
                        warnings.add("ElementSet '" + set.id() + "': type '"
                                + type + "' not detected in current model.");
                    }
                }
            }
        }

        // ── Rules ─────────────────────────────────────────────────────────────
        for (RuleSpec spec : config.rules()) {
            // Warn if elementSet doesn't exist
            if (spec.appliesToSet().isPresent()
                    && !config.elementSets().containsKey(spec.appliesToSet().get())) {
                warnings.add("Rule '" + spec.id() + "': elementSet '"
                        + spec.appliesToSet().get() + "' not found in elementSets.");
            }

            WizardState.RuleRequest req = new WizardState.RuleRequest(
                    spec.id(),
                    spec.title().orElse(spec.id()),
                    spec.type().name(),
                    spec.target().orElse(null),
                    spec.appliesToSet().orElse(null),
                    spec.params(),
                    spec.message().orElse(null),
                    spec.enabled(),
                    spec.group().orElse(null)
            );
            state.addRule(req);
        }

        return new MappingResult(state, warnings);
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static final class MappingResult {

        private final WizardState  wizardState;
        private final List<String> warnings;

        MappingResult(WizardState wizardState, List<String> warnings) {
            this.wizardState = wizardState;
            this.warnings    = Collections.unmodifiableList(warnings);
        }

        public WizardState  wizardState() { return wizardState; }
        public List<String> warnings()    { return warnings; }
        public boolean      hasWarnings() { return !warnings.isEmpty(); }
    }

    private static List<String> translateKindsToStereotypes(List<String> kinds) {
        List<String> result = new ArrayList<>();
        for (String kind : kinds) {
            switch (kind.toUpperCase()) {
                case "BLOCK":           result.add("Block");          break;
                case "INTERFACE_BLOCK": result.add("InterfaceBlock"); break;
                case "PORT":            result.add("Port");           break;
                case "PORT_FULL":       result.add("fullPort");      break;
                case "PORT_PROXY":      result.add("proxyPort");     break;
                case "PORT_FLOW":       result.add("flowPort");      break;
                case "INTERFACE":       result.add("Interface");      break;
                case "PACKAGE":         result.add("Package");        break;
                case "REQUIREMENT":     result.add("Requirement");    break;
                // STATE_CONNECTOR (statechart/activity pseudostate / IRPConnector)
                // and LINK (structural link / IRPLink) are identified by
                // metaClass, not by a stereotype. Emitting a fake stereotype
                // here produced spurious "not detected in current model"
                // warnings, so both are deliberately unmapped.
                case "FLOW_PROPERTY":   result.add("FlowProperty");   break;
                default: break;
            }
        }
        return result;
    }

}