// config/generate/ConfigToWizardStateMapper.java
package org.rhapsodychecker.rhapsodyruleverifier.config.generate;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.FastDetectionResult;

import java.util.ArrayList;
import java.util.Collections;
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

    public static MappingResult map(RuleCheckerConfig config, FastDetectionResult fast) {
        WizardState  state    = new WizardState().mode(config.mode().name().toLowerCase());
        List<String> warnings = new ArrayList<>();

        // ── Aliases ───────────────────────────────────────────────────────────
        for (AliasDefinition alias : config.aliases().values()) {
            state.addAlias(alias);
        }

        // ── Element Sets ──────────────────────────────────────────────────────
        for (ElementSetDefinition set : config.elementSets().values()) {
            state.addSet(set);

            if (fast != null) {
                Set<String> detectedStereos = fast.countsByStereotype().keySet();
                for (String stereo : set.stereotypes()) {
                    if (!detectedStereos.contains(stereo)) {
                        warnings.add("ElementSet '" + set.id() + "': stereotype '"
                                + stereo + "' not detected in current model.");
                    }
                }

                Set<String> detectedMeta = fast.countsByMetaClass().keySet();
                for (String type : set.types()) {
                    if (!detectedMeta.contains(type)) {
                        warnings.add("ElementSet '" + set.id() + "': type '"
                                + type + "' not detected in current model.");
                    }
                }
            }
        }

        // ── Rules ─────────────────────────────────────────────────────────────
        for (RuleSpec spec : config.rules()) {
            if (!spec.enabled()) continue;

            // Warn daca target alias nu exista
            if (spec.target().isPresent()
                    && !config.aliases().containsKey(spec.target().get())) {
                warnings.add("Rule '" + spec.id() + "': target alias '"
                        + spec.target().get() + "' not found in aliases.");
            }

            // Warn daca elementSet nu exista
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
                    spec.message().orElse(null)
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
}
