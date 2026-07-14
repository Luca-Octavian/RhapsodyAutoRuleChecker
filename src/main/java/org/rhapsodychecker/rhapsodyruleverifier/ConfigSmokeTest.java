package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.config.AliasDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader.ConfigLoadException;
import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class ConfigSmokeTest {

    public static void main(String[] args) {
        String configPath = args.length > 0 ? args[0] : "src/main/resources/wrong.yaml";

        System.out.println("=== Config Smoke Test ===");
        System.out.println("Loading config from: " + configPath);
        System.out.println();

        try {
            Path path = Paths.get(configPath);
            RuleCheckerConfig config = ConfigLoader.load(path);

            // 1) Top-level info
            System.out.println("[PASS] Config loaded successfully");
            System.out.println("  schemaVersion: " + config.schemaVersion());
            System.out.println("  mode: " + config.mode());
            System.out.println("  aliases: " + config.aliases().size());
            System.out.println("  elementSets: " + config.elementSets().size());
            System.out.println("  rules (total): " + config.rules().size());
            System.out.println("  rules (enabled): " + config.enabledRules().size());

            // 2) Aliases
            System.out.println("\n--- Aliases ---");
            for (Map.Entry<String, AliasDefinition> entry : config.aliases().entrySet()) {
                AliasDefinition a = entry.getValue();
                System.out.println("  " + a.id()
                        + " | kind=" + a.kind()
                        + " | type=" + a.valueType()
                        + " | title=" + a.title().orElse("<none>"));
                a.profileName().ifPresent(p -> System.out.println("    profileName=" + p));
                a.tagName().ifPresent(t -> System.out.println("    tagName=" + t));
                a.stereotypeName().ifPresent(s -> System.out.println("    stereotypeName=" + s));
                if (!a.stereotypeNames().isEmpty()) {
                    System.out.println("    stereotypeNames=" + a.stereotypeNames());
                }
                if (!a.values().isEmpty()) {
                    System.out.println("    values=" + a.values());
                }
            }

            // 3) Element Sets
            System.out.println("\n--- Element Sets ---");
            for (Map.Entry<String, ElementSetDefinition> entry : config.elementSets().entrySet()) {
                ElementSetDefinition s = entry.getValue();
                System.out.println("  " + s.id()
                        + " | title=" + s.title().orElse("<none>"));
                if (!s.types().isEmpty()) System.out.println("    types=" + s.types());
                if (!s.stereotypes().isEmpty()) System.out.println("    stereotypes=" + s.stereotypes());
                if (!s.includePackages().isEmpty()) System.out.println("    includePackages=" + s.includePackages());
                if (!s.excludePackages().isEmpty()) System.out.println("    excludePackages=" + s.excludePackages());
            }

            // 4) Rules
            System.out.println("\n--- Rules ---");
            for (RuleSpec r : config.rules()) {
                System.out.println("  " + r.id()
                        + " | type=" + r.type()
                        + " | enabled=" + r.enabled()
                        + " | title=" + r.title().orElse("<none>"));
                r.appliesToSet().ifPresent(s -> System.out.println("    appliesTo set=" + s));
                if (!r.appliesToTypes().isEmpty()) System.out.println("    appliesTo types=" + r.appliesToTypes());
                if (!r.appliesToStereotypes().isEmpty()) System.out.println("    appliesTo stereotypes=" + r.appliesToStereotypes());
                r.target().ifPresent(t -> System.out.println("    target=" + t));
                if (!r.params().isEmpty()) System.out.println("    params=" + r.params());
                r.message().ifPresent(m -> System.out.println("    message=" + m));
                if (!r.conditions().isEmpty()) System.out.println("    conditions=" + r.conditions());
            }

            // 5) Cross-reference checks
            System.out.println("\n--- Cross-reference validation ---");
            boolean allOk = true;
            for (RuleSpec r : config.rules()) {
                if (r.target().isPresent() && !config.aliases().containsKey(r.target().get())) {
                    System.out.println("  [FAIL] Rule '" + r.id() + "' references unknown alias: '" + r.target().get() + "'");
                    allOk = false;
                }
                if (r.appliesToSet().isPresent() && !config.elementSets().containsKey(r.appliesToSet().get())) {
                    System.out.println("  [FAIL] Rule '" + r.id() + "' references unknown elementSet: '" + r.appliesToSet().get() + "'");
                    allOk = false;
                }
            }
            if (allOk) {
                System.out.println("  [PASS] All cross-references are valid");
            }

            // 6) Summary
            System.out.println("\n--- Summary ---");
            System.out.println("  Config: " + config);
            System.out.println("\n[DONE] Config smoke test complete.");

        } catch (ConfigLoadException e) {
            System.err.println("[FAIL] Config load error: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("  Caused by: " + e.getCause().getMessage());
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[FAIL] Validation error: " + e.getMessage());
        } catch (Throwable t) {
            System.err.println("[FAIL] Unexpected error: " + t.getMessage());
            t.printStackTrace();
        }
    }
}