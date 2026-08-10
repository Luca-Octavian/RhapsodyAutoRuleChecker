package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel.RuleParamPanel;
import org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.rule.panel.RuleParamPanelFactory;

import java.util.*;

/**
 * Headless smoke test for RuleParamPanel round-trip (prefill → buildParams).
 * Run with: java -Djava.awt.headless=true -cp ... RuleParamPanelSmokeTest
 */
public class RuleParamPanelSmokeTest {

    private static final List<String> STEREOTYPES = Arrays.asList("Block", "Port");
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== RuleParamPanel Smoke Test ===\n");

        test(RuleType.REQUIRED_VALUE, requiredValueFixture());
        test(RuleType.REQUIRED_STEREOTYPE, requiredStereotypeFixture());
        test(RuleType.REQUIRED_STEREOTYPE_ONE_OF, requiredStereotypeOneOfFixture());
        test(RuleType.NAMING_PATTERN, namingPatternFixture());
        test(RuleType.RELATION_EXISTS, relationExistsFixture());
        test(RuleType.OWNER_STEREOTYPE_CONSTRAINT, ownerStereotypeConstraintFixture());
        test(RuleType.FLOW_PROPERTY_CONSTRAINT, flowPropertyConstraintFixture());

        System.out.println("\n=== Summary: " + passed + " PASS, " + failed + " FAIL ===");
        if (failed > 0) System.exit(1);
    }

    private static void test(RuleType type, Map<String, Object> fixture) {
        try {
            RuleParamPanel panel = RuleParamPanelFactory.create(type, STEREOTYPES, () -> {});
            panel.prefill(fixture);
            Map<String, Object> result = panel.buildParams();

            String validation = panel.validationMessage();
            boolean validOk = validation == null;

            boolean match = fixture.equals(result);
            if (match && validOk) {
                System.out.println("[PASS] " + type);
                passed++;
            } else {
                System.out.println("[FAIL] " + type);
                if (!match) {
                    System.out.println("  Expected: " + fixture);
                    System.out.println("  Got:      " + result);
                }
                if (!validOk) {
                    System.out.println("  Validation (expected null): " + validation);
                }
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] " + type + " — exception: " + e.getMessage());
            e.printStackTrace(System.out);
            failed++;
        }
    }

    private static Map<String, Object> requiredValueFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nonEmpty", true);
        return m;
    }

    private static Map<String, Object> requiredStereotypeFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("requiredStereotypes", "Block");
        return m;
    }

    private static Map<String, Object> requiredStereotypeOneOfFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("anyOf", Arrays.asList("Block", "Port"));
        return m;
    }

    private static Map<String, Object> namingPatternFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("startsWith", "Sys");
        m.put("caseSensitive", true);
        return m;
    }

    private static Map<String, Object> relationExistsFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relationKind", "dependency");
        m.put("direction", "any");
        m.put("operator", "gte");
        m.put("value", 1);
        return m;
    }

    private static Map<String, Object> ownerStereotypeConstraintFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ownerStereotype", "Block");
        m.put("allowedKinds", Arrays.asList("CLASS", "OBJECT"));
        return m;
    }

    private static Map<String, Object> flowPropertyConstraintFixture() {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> typeBlock = new LinkedHashMap<>();
        typeBlock.put("required", true);
        m.put("type", typeBlock);
        return m;
    }
}