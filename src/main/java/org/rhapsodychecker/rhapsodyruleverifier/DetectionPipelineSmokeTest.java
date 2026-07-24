// DetectionPipelineSmokeTest.java
package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.*;
import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.ConfigBuilder;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardParamsCollector;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.WizardState;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.FieldSpec;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.RuleParamSchema;
import org.rhapsodychecker.rhapsodyruleverifier.config.generate.schema.RuleParamSchemaRegistry;
import org.rhapsodychecker.rhapsodyruleverifier.core.RhapsodyConnectionManager;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.detection.DetectionFacade;
import org.rhapsodychecker.rhapsodyruleverifier.detection.SuggestionsService;
import org.rhapsodychecker.rhapsodyruleverifier.detection.api.*;
import org.rhapsodychecker.rhapsodyruleverifier.detection.rhapsody.PortProbeService;

import java.util.*;

public class DetectionPipelineSmokeTest {

    // ── Configurare ───────────────────────────────────────────────────────────
    private static final String RPYX_PATH =
            "C:\\Users\\uik11305\\Downloads\\Rhapsody Model 2\\Rhapsody Model 2\\L2_PECU_SYS-Architecture.rpyx";

    private static final int PORT_SAMPLE_LIMIT = 50;
    private static final int TAG_MAX_PER_KIND  = 100;

    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        String rpyxPath = args.length >= 1 ? args[0] : RPYX_PATH;

        sep("DetectionPipeline Smoke Test");
        System.out.println("Project: " + rpyxPath);
        System.out.println();

        RhapsodyConnectionManager conn = RhapsodyConnectionManager.getInstance();

        try {
            // ── PASUL 1: Conectare + incarcare model ──────────────────────────
            step("1. Connect & Load Model");
            conn.connect(rpyxPath);
            System.out.println("  [OK] Connected: " + conn.getProject().getName());

            RhapsodyModelSnapshot snapshot =
                    new RhapsodyModelLoader().loadModel(conn.getProject());
            List<ElementRecord> allRecords = snapshot.records();
            System.out.println("  [OK] Loaded " + allRecords.size() + " elements");

            // ── PASUL 2: Fast Scan (zero apeluri native grele) ────────────────
            step("2. Fast Scan (in-memory aggregation)");

            RhapsodyPortInfoResolver portResolver =
                    new RhapsodyPortInfoResolver(snapshot);
            PortProbeService portProbeService = new PortProbeService(portResolver);

            DetectionFacade facade = DetectionFacade.create(portProbeService);
            FastDetectionResult fast = facade.fastScan(
                    allRecords, snapshot.handleByGuid(), conn.getApplication());

            System.out.println("  [OK] Total elements: " + fast.totalElements());
            System.out.println("  [OK] Elements with description: "
                    + fast.elementsWithDescription()
                    + String.format(" (fill rate: %.1f%%)", fast.descriptionFillRate() * 100));

            // MetaClass breakdown
            System.out.println("\n  --- MetaClass counts (top 10) ---");
            fast.countsByMetaClass().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> System.out.printf("    %-30s : %d%n", e.getKey(), e.getValue()));

            // Kind breakdown
            System.out.println("\n  --- ElementKind counts ---");
            fast.countsByKind().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> System.out.printf("    %-30s : %d%n", e.getKey(), e.getValue()));

            // Stereotype breakdown
            System.out.println("\n  --- Stereotype counts (top 20) ---");
            fast.countsByStereotype().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(20)
                    .forEach(e -> System.out.printf("    %-30s : %d%n", e.getKey(), e.getValue()));

            // Owner paths sample
            System.out.println("\n  --- Top owner paths (first 10) ---");
            fast.topOwnerPaths().stream()
                    .limit(10)
                    .forEach(p -> System.out.println("    " + p));

            // ── PASUL 3: Profile Detection ────────────────────────────────────
            step("3. Profile Detection");
            ProfileSummary profile = fast.profile();
            System.out.println("  [OK] Profiles found: " + profile.profileNames());
            System.out.println("  [OK] ASIL hints detected: " + profile.hasAsilHints());

            // ── PASUL 4: Port Probe ───────────────────────────────────────────
            step("4. Port Probe (sample " + PORT_SAMPLE_LIMIT + " ports per category)");
            PortCapabilities ports = fast.ports();

            System.out.println("  --- Directed ports (PORT_FLOW + PORT_PROXY) ---");
            System.out.println("  [OK] Sampled: " + ports.directedSampled());
            System.out.printf("       Type resolvable:         %d/%d (%.0f%%)%n",
                    ports.directedOkType(), ports.directedSampled(),
                    ports.directedTypeSuccessRate() * 100);
            System.out.printf("       Direction resolvable:    %d/%d (%.0f%%)%n",
                    ports.directedOkDirection(), ports.directedSampled(),
                    ports.directedDirectionSuccessRate() * 100);
            System.out.printf("       Multiplicity resolvable: %d/%d (%.0f%%)%n",
                    ports.directedOkMultiplicity(), ports.directedSampled(),
                    ports.directedMultiplicitySuccessRate() * 100);

            System.out.println("  --- Standard ports (PORT) ---");
            System.out.println("  [OK] Sampled: " + ports.standardSampled());
            System.out.printf("       Type resolvable:         %d/%d (%.0f%%)%n",
                    ports.standardOkType(), ports.standardSampled(),
                    ports.standardTypeSuccessRate() * 100);
            System.out.printf("       Direction resolvable:    %d/%d (%.0f%%)%n",
                    ports.standardOkDirection(), ports.standardSampled(),
                    ports.standardDirectionSuccessRate() * 100);
            System.out.printf("       Multiplicity resolvable: %d/%d (%.0f%%)%n",
                    ports.standardOkMultiplicity(), ports.standardSampled(),
                    ports.standardMultiplicitySuccessRate() * 100);

            // Dump primele 10 porturi cu detalii
            System.out.println("\n  --- First 10 ports detail ---");
            int portCount = 0;
            for (ElementRecord r : allRecords) {
                if (!r.kind().isPortKind()) continue;
                if (portCount >= 10) break;
                portCount++;

                org.rhapsodychecker.rhapsodyruleverifier.core.resolve.PortInfo info =
                        portResolver.resolve(r);
                System.out.printf("    [%s] %-25s | stereo=%-20s | dir=%-8s | mult=%-8s | typeName=%s%n",
                        r.kind().name(),
                        r.name(),
                        r.stereotypes().toString(),
                        info.direction(),
                        info.multiplicity(),
                        r.typeName().orElse("<none>"));
            }

            // ── PASUL 5: Tag Discovery (scoped pe BLOCK) ──────────────────────
            step("5. Tag Discovery — scoped to BLOCK kind");
            TagDiscoveryResult blockTags = facade.discoverTagsForKind(
                    allRecords, ElementKind.BLOCK, snapshot.handleByGuid());

            System.out.println("  [OK] Distinct tags found on blocks: "
                    + blockTags.tagNames().size());

            if (blockTags.tagNames().isEmpty()) {
                System.out.println("  [WARN] No tags found on BLOCK elements — "
                        + "check if profile exposes tags via getTags()");
            } else {
                System.out.println("\n  --- Tag names + observed values (blocks) ---");
                blockTags.valuesByTag().forEach((tag, values) ->
                        System.out.printf("    %-30s : %s%n", tag, values));
            }

            // Tag discovery pe PORT_FLOW
            step("5b. Tag Discovery — scoped to PORT_FLOW kind");
            TagDiscoveryResult portTags = facade.discoverTagsForKind(
                    allRecords, ElementKind.PORT_FLOW, snapshot.handleByGuid());

            System.out.println("  [OK] Distinct tags found on flow ports: "
                    + portTags.tagNames().size());
            portTags.valuesByTag().forEach((tag, values) ->
                    System.out.printf("    %-30s : %s%n", tag, values));

            // ── PASUL 6: Alias Guessing ───────────────────────────────────────
            step("6. Alias Guessing");
            AliasGuess guess = facade.guessAliases(fast, Optional.of(blockTags));

            System.out.println("  [OK] Description source: " + guess.descriptionSource());
            guess.descriptionTagName().ifPresent(t ->
                    System.out.println("  [OK] Description tag name: " + t));
            System.out.println("  [OK] ASIL tag candidates:   " + guess.asilTagCandidates());
            System.out.println("  [OK] ASIL stereo candidates: " + guess.asilStereoCandidates());
            System.out.println("  [OK] Port type resolvable:         " + guess.portTypeResolvable());
            System.out.println("  [OK] Port direction resolvable:    " + guess.portDirectionResolvable());
            System.out.println("  [OK] Port multiplicity resolvable: " + guess.portMultiplicityResolvable());

            // ── PASUL 7: Suggestions Service ─────────────────────────────────
            step("7. Suggestions Service");
            SuggestionsService suggestions = new SuggestionsService();

            List<String> stereoSuggestions =
                    suggestions.suggestStereotypesForKind(ElementKind.BLOCK, allRecords);
            System.out.println("  [OK] Stereotype suggestions for BLOCK: " + stereoSuggestions);

            List<String> directedPortStereos =
                    suggestions.suggestStereotypesForDirectedPorts(allRecords);
            System.out.println("  [OK] Stereotype suggestions for directed ports: " + directedPortStereos);

            List<String> kindNames = suggestions.availableElementKinds();
            System.out.println("  [OK] Available ElementKinds: " + kindNames);

            if (!blockTags.tagNames().isEmpty()) {
                String firstTag = blockTags.tagNames().iterator().next();
                List<String> tagValues = suggestions.suggestValuesForTag(firstTag, blockTags);
                System.out.println("  [OK] Values for tag '" + firstTag + "': " + tagValues);
            }

            // ── PASUL 8: RuleParamSchemaRegistry ─────────────────────────────
            step("8. RuleParamSchemaRegistry — schema per rule type");
            RuleParamSchemaRegistry registry = RuleParamSchemaRegistry.getInstance();

            for (RuleType ruleType : RuleType.values()) {
                try {
                    RuleParamSchema schema = registry.getSchema(ruleType);
                    System.out.printf("  [OK] %-35s → %d fields (%d required)%n",
                            ruleType.name(),
                            schema.fields().size(),
                            schema.requiredFields().size());
                    for (FieldSpec f : schema.fields()) {
                        System.out.printf("       %-30s | type=%-15s | required=%s | suggestions=%s%n",
                                f.paramKey(),
                                f.fieldType().name(),
                                f.required(),
                                f.suggestionsSource().name());
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("  [SKIP] " + ruleType + " → " + e.getMessage());
                }
            }

            // ── PASUL 9: WizardParamsCollector — validare params ──────────────
            step("9. WizardParamsCollector — validation");

            // Caz VALID: RequiredValue cu nonEmpty + minLength
            Map<String, Object> validParams = new LinkedHashMap<>();
            validParams.put("nonEmpty", true);
            validParams.put("minLength", 10);
            WizardParamsCollector.ValidationResult r1 =
                    WizardParamsCollector.validate(RuleType.REQUIRED_VALUE, validParams);
            System.out.println("  [RequiredValue - nonEmpty+minLength] valid=" + r1.isValid()
                    + (r1.isValid() ? "" : " errors=" + r1.errors()));

            // Caz INVALID: RequiredValue cu operator=in dar fara values
            Map<String, Object> invalidParams = new LinkedHashMap<>();
            invalidParams.put("operator", "in");
            WizardParamsCollector.ValidationResult r2 =
                    WizardParamsCollector.validate(RuleType.REQUIRED_VALUE, invalidParams);
            System.out.println("  [RequiredValue - in without values] valid=" + r2.isValid()
                    + " errors=" + r2.errors());

            // Caz VALID: RequiredStereotypeOneOf cu anyOf populat
            Map<String, Object> stereoParams = new LinkedHashMap<>();
            stereoParams.put("anyOf", Arrays.asList("Block", "Component"));
            WizardParamsCollector.ValidationResult r3 =
                    WizardParamsCollector.validate(RuleType.REQUIRED_STEREOTYPE_ONE_OF, stereoParams);
            System.out.println("  [RequiredStereotypeOneOf - anyOf ok] valid=" + r3.isValid()
                    + (r3.isValid() ? "" : " errors=" + r3.errors()));

            // Caz INVALID: RequiredStereotypeOneOf fara anyOf
            WizardParamsCollector.ValidationResult r4 =
                    WizardParamsCollector.validate(RuleType.REQUIRED_STEREOTYPE_ONE_OF, new HashMap<>());
            System.out.println("  [RequiredStereotypeOneOf - missing anyOf] valid=" + r4.isValid()
                    + " errors=" + r4.errors());

            // Caz VALID: OwnerStereotypeConstraint complet
            Map<String, Object> ownerParams = new LinkedHashMap<>();
            ownerParams.put("ownerStereotype", "Block");
            ownerParams.put("allowedKinds", Arrays.asList("PORT_FLOW", "PORT"));
            WizardParamsCollector.ValidationResult r5 =
                    WizardParamsCollector.validate(RuleType.OWNER_STEREOTYPE_CONSTRAINT, ownerParams);
            System.out.println("  [OwnerStereotypeConstraint - ok] valid=" + r5.isValid()
                    + (r5.isValid() ? "" : " errors=" + r5.errors()));

            // ── PASUL 10: ConfigBuilder — WizardState → RuleCheckerConfig ─────
            step("10. ConfigBuilder — WizardState → RuleCheckerConfig");

            WizardState state = new WizardState()
                    .addAlias(AliasDefinition.builder()
                            .id("ELEMENT_DESCRIPTION")
                            .kind(AliasKind.DESCRIPTION)
                            .title("Description")
                            .build())
                    .addSet(ElementSetDefinition.builder()
                            .id("AllBlocks")
                            .title("All Block elements")
                            .types(Arrays.asList("Class"))
                            .stereotypes(Arrays.asList("Block"))
                            .build())
                    .addRule(new WizardState.RuleRequest(
                            "BLOCK_MUST_HAVE_DESCRIPTION",
                            "Block must have description",
                            "RequiredValue",
                            "ELEMENT_DESCRIPTION",
                            "AllBlocks",
                            new LinkedHashMap<String, Object>() {{
                                put("nonEmpty", true);
                                put("minLength", 5);
                            }},
                            "Description missing or too short for {elementName}"
                    ));

            RuleCheckerConfig builtConfig = ConfigBuilder.build(state);
            System.out.println("  [OK] Config built successfully");
            System.out.println("       aliases:  " + builtConfig.aliases().size());
            System.out.println("       sets:     " + builtConfig.elementSets().size());
            System.out.println("       rules:    " + builtConfig.rules().size());
            System.out.println("       enabled:  " + builtConfig.enabledRules().size());
            builtConfig.enabledRules().forEach(spec ->
                    System.out.println("       → " + spec.id()
                            + " | type=" + spec.type()
                            + " | target=" + spec.target().orElse("<none>")
                            + " | set=" + spec.appliesToSet().orElse("<none>")
                            + " | params=" + spec.params()));

            sep("Smoke Test COMPLETE");

        } catch (Throwable t) {
            System.err.println("[FAIL] " + t.getMessage());
            t.printStackTrace();
        } finally {
            conn.shutdown();
        }
    }

    // ── Helpers de formatare ──────────────────────────────────────────────────

    private static String repeatChar(char c, int count) {
        char[] arr = new char[Math.max(0, count)];
        java.util.Arrays.fill(arr, c);
        return new String(arr);
    }

    private static void sep(String title) {
        System.out.println("\n" + repeatChar('=', 70));
        System.out.println("  " + title);
        System.out.println(repeatChar('=', 70));
    }

    private static void step(String title) {
        System.out.println("\n── " + title + " " + repeatChar('─', Math.max(0, 60 - title.length())));
    }
}
