package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * Tests ConfigLoader YAML parsing and validation.
 */
public class ConfigLoaderTest {

    private RuleCheckerConfig load(String yaml) throws ConfigLoader.ConfigLoadException {
        InputStream is = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
        return ConfigLoader.load(is, "test");
    }

    @Test
    public void validMinimalConfig_loadsSuccessfully() throws Exception {
        String yaml = "schemaVersion: 1\nrules: []\n";
        RuleCheckerConfig cfg = load(yaml);
        assertEquals(1, cfg.schemaVersion());
        assertTrue(cfg.enabledRules().isEmpty());
    }

    @Test
    public void validRuleWithNamingPattern_loadsSuccessfully() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    type: naming_pattern\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n"
                + "    params:\n"
                + "      pattern: \"^[A-Z].*\"\n";
        RuleCheckerConfig cfg = load(yaml);
        assertEquals(1, cfg.enabledRules().size());
        assertEquals("r1", cfg.enabledRules().get(0).id());
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void missingSchemaVersion_throws() throws Exception {
        load("rules: []\n");
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void nonMapRoot_throws() throws Exception {
        load("- just a list\n");
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void emptyDocument_throws() throws Exception {
        load("");
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void missingRuleId_throwsValidationError() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - type: naming_pattern\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        load(yaml);
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void missingRuleType_throwsValidationError() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        load(yaml);
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void unknownRuleType_throwsValidationError() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    type: nonexistent_rule_type\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        load(yaml);
    }

    @Test(expected = ConfigLoader.ConfigLoadException.class)
    public void invalidBooleanEnabled_throwsValidationError() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    type: naming_pattern\n"
                + "    enabled: maybe\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        load(yaml);
    }

    @Test
    public void disabledRule_notInEnabledRules() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    type: naming_pattern\n"
                + "    enabled: false\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        RuleCheckerConfig cfg = load(yaml);
        assertEquals(1, cfg.rules().size());
        assertTrue(cfg.enabledRules().isEmpty());
    }

    @Test
    public void legacyTargetDescription_parsed() throws Exception {
        String yaml = "schemaVersion: 1\nrules:\n"
                + "  - id: r1\n"
                + "    type: required_value\n"
                + "    target: description\n"
                + "    appliesTo:\n"
                + "      types: [Class]\n";
        RuleCheckerConfig cfg = load(yaml);
        assertEquals(1, cfg.enabledRules().size());
        assertNotNull(cfg.enabledRules().get(0).target());
    }
}