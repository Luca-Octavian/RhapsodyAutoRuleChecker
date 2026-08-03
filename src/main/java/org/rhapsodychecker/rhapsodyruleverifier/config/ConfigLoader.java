package org.rhapsodychecker.rhapsodyruleverifier.config;

import org.rhapsodychecker.rhapsodyruleverifier.core.config.AliasKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.RuleType;
import org.rhapsodychecker.rhapsodyruleverifier.core.config.ValueType;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses a YAML config file into an immutable RuleCheckerConfig.
 * Validates structure, types, and cross-references.
 */
public final class ConfigLoader {

    private ConfigLoader() {}

    /**
     * Load config from a file path.
     */
    public static RuleCheckerConfig load(Path path) throws ConfigLoadException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is, path.toString());
        } catch (IOException e) {
            throw new ConfigLoadException("Cannot read config file: " + path, e);
        }
    }

    /**
     * Load config from an InputStream.
     */
    public static RuleCheckerConfig load(InputStream is, String sourceName) throws ConfigLoadException {
        Yaml yaml = new Yaml();
        Map<String, Object> root;
        try {
            Object raw = yaml.load(is);
            if (!(raw instanceof Map)) {
                throw new ConfigLoadException("Config root must be a YAML mapping, got: "
                        + (raw == null ? "null" : raw.getClass().getSimpleName()));
            }
            root = castMap(raw);
        } catch (ConfigLoadException e) {
            throw e;
        } catch (Throwable t) {
            throw new ConfigLoadException("Failed to parse YAML from '" + sourceName + "': " + t.getMessage(), t);
        }

        try {
            int schemaVersion = requireInt(root, "schemaVersion");

            Map<String, ElementSetDefinition> elementSets = parseElementSets(optMap(root, "elementSets"));

            // Collect parse errors but don't throw yet
            List<String> parseErrors = new ArrayList<>();
            List<RuleSpec> rules = parseRules(optList(root, "rules"), parseErrors);

            return RuleCheckerConfig.builder()
                    .schemaVersion(schemaVersion)
                    .elementSets(elementSets)
                    .rules(rules)
                    .parseErrors(parseErrors)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new ConfigLoadException("Config validation error: " + e.getMessage(), e);
        }
    }

    // ---- Element Sets ----

    private static Map<String, ElementSetDefinition> parseElementSets(Map<String, Object> raw) {
        if (raw == null || raw.isEmpty()) return Collections.emptyMap();
        Map<String, ElementSetDefinition> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> fields = castMap(entry.getValue());
            ElementSetDefinition set = ElementSetDefinition.builder()
                    .id(id)
                    .title(optString(fields, "title", null))
                    .types(optStringList(fields, "types"))
                    .kinds(optStringList(fields, "kinds"))
                    .stereotypes(optStringList(fields, "stereotypes"))
                    .includePackages(optStringList(fields, "includePackages"))
                    .excludePackages(optStringList(fields, "excludePackages"))
                    .build();
            out.put(id, set);
        }
        return out;
    }

    // ---- Rules ----

    private static List<RuleSpec> parseRules(List<Object> raw, List<String> errors) {
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<RuleSpec> out = new ArrayList<>();

        for (int idx = 0; idx < raw.size(); idx++) {
            try {
                Map<String, Object> fields = castMap(raw.get(idx));
                String id = optString(fields, "id", null);
                String label = id != null ? "rule '" + id + "'" : "rule at index " + idx;

                if (id == null || id.trim().isEmpty()) {
                    errors.add(label + ": missing required field 'id'");
                    continue;
                }

                String typeStr = optString(fields, "type", null);
                if (typeStr == null) {
                    errors.add(label + ": missing required field 'type'");
                    continue;
                }

                RuleType type;
                try {
                    type = RuleType.fromString(typeStr);
                } catch (IllegalArgumentException e) {
                    errors.add(label + ": " + e.getMessage());
                    continue;
                }

                Map<String, Object> appliesTo = optMap(fields, "appliesTo");
                String appliesToSet = null;
                List<String> appliesToTypes = Collections.emptyList();
                List<String> appliesToStereotypes = Collections.emptyList();
                List<String> appliesToIncludePkgs = Collections.emptyList();
                List<String> appliesToExcludePkgs = Collections.emptyList();
                if (appliesTo != null) {
                    appliesToSet = optString(appliesTo, "set", null);
                    appliesToTypes = optStringList(appliesTo, "types");
                    appliesToStereotypes = optStringList(appliesTo, "stereotypes");
                    appliesToIncludePkgs = optStringList(appliesTo, "includePackages");
                    appliesToExcludePkgs = optStringList(appliesTo, "excludePackages");
                }

                if (appliesToSet == null && appliesToTypes.isEmpty() && appliesToStereotypes.isEmpty()) {
                    errors.add(label + ": appliesTo must specify a set or at least types/stereotypes");
                    continue;
                }

                // Parse target: can be a map (new format) or a string (legacy alias ID)
                TargetSpec target = parseTarget(fields, label, errors);
                switch (type) {
                    case REQUIRED_VALUE:
                    case NAMING_PATTERN:
                        if (target == null) {
                            errors.add(label + ": " + type + " requires a target");
                            continue;
                        }
                        break;
                    default:
                        break;
                }

                List<Map<String, Object>> conditions = null;
                Object rawCond = fields.get("conditions");
                if (rawCond instanceof List) {
                    conditions = new ArrayList<>();
                    for (Object c : (List<?>) rawCond) {
                        if (c instanceof Map) {
                            conditions.add(castMap(c));
                        }
                    }
                }

                Map<String, Object> params = optMap(fields, "params");
                String group = optString(fields, "group", null);

                RuleSpec rule = RuleSpec.builder()
                        .id(id)
                        .type(type)
                        .enabled(optBool(fields, "enabled", true))
                        .title(optString(fields, "title", null))
                        .message(optString(fields, "message", null))
                        .group(group)
                        .appliesToSet(appliesToSet)
                        .appliesToTypes(appliesToTypes)
                        .appliesToStereotypes(appliesToStereotypes)
                        .appliesToIncludePackages(appliesToIncludePkgs)
                        .appliesToExcludePackages(appliesToExcludePkgs)
                        .conditions(conditions)
                        .target(target)
                        .params(params != null ? params : Collections.emptyMap())
                        .build();
                out.add(rule);
            } catch (IllegalArgumentException e) {
                errors.add("rule at index " + idx + ": " + e.getMessage());
            }
        }

        return out;
    }

    /**
     * Parses the target field from a rule. Supports two formats:
     * - New format (map): { kind: description } or { kind: taggedValue, profile: X, tag: Y }
     * - Legacy format (string): an alias ID like "ELEMENT_DESCRIPTION" — treated as DESCRIPTION kind
     *   for simple cases, or fails with an error for complex aliases that need migration.
     */
    private static TargetSpec parseTarget(Map<String, Object> fields, String label, List<String> errors) {
        Object rawTarget = fields.get("target");
        if (rawTarget == null) return null;

        if (rawTarget instanceof Map) {
            // New format: target is a map with kind + optional fields
            Map<String, Object> targetMap = castMap(rawTarget);
            String kindStr = optString(targetMap, "kind", null);
            if (kindStr == null || kindStr.trim().isEmpty()) {
                errors.add(label + ": target map missing required field 'kind'");
                return null;
            }
            try {
                AliasKind kind = AliasKind.fromString(kindStr);
                TargetSpec.Builder b = TargetSpec.builder().kind(kind);
                b.profileName(optString(targetMap, "profile", optString(targetMap, "profileName", null)));
                b.tagName(optString(targetMap, "tag", optString(targetMap, "tagName", null)));
                b.stereotypeName(optString(targetMap, "stereotypeName", null));
                String typeStr = optString(targetMap, "type", null);
                if (typeStr != null) {
                    b.valueType(ValueType.fromString(typeStr));
                }
                b.values(optStringList(targetMap, "values"));
                return b.build();
            } catch (IllegalArgumentException e) {
                errors.add(label + ": invalid target: " + e.getMessage());
                return null;
            }
        }

        // Legacy format: target is a plain string (old alias ID reference)
        String targetStr = rawTarget.toString().trim();
        if (targetStr.isEmpty()) return null;

        // Try to interpret common legacy alias IDs as simple kinds
        String lower = targetStr.toLowerCase();
        switch (lower) {
            case "description":
            case "element_description":
                return TargetSpec.builder().kind(AliasKind.DESCRIPTION).build();
            case "name":
            case "element_name":
                return TargetSpec.builder().kind(AliasKind.NAME).build();
            case "port_type":
            case "porttype":
                return TargetSpec.builder().kind(AliasKind.PORT_TYPE).build();
            case "port_direction":
            case "portdirection":
                return TargetSpec.builder().kind(AliasKind.PORT_DIRECTION).build();
            case "port_multiplicity":
            case "portmultiplicity":
                return TargetSpec.builder().kind(AliasKind.PORT_MULTIPLICITY).build();
            default:
                // Unknown legacy alias ID — cannot auto-migrate complex aliases (taggedValue etc.)
                errors.add(label + ": target '" + targetStr + "' is a legacy alias ID that cannot be "
                        + "auto-resolved. Please update the config to use the new target format: "
                        + "target: { kind: taggedValue, profile: ..., tag: ... }");
                return null;
        }
    }


    // ---- YAML extraction helpers ----

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map) return (Map<String, Object>) o;
        throw new IllegalArgumentException("Expected a mapping, got: " + (o == null ? "null" : o.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> optMap(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        if (v == null) return null;
        if (v instanceof Map) return (Map<String, Object>) v;
        throw new IllegalArgumentException("'" + key + "' must be a mapping, got: " + v.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static List<Object> optList(Map<String, Object> parent, String key) {
        Object v = parent.get(key);
        if (v == null) return null;
        if (v instanceof List) return (List<Object>) v;
        throw new IllegalArgumentException("'" + key + "' must be a list, got: " + v.getClass().getSimpleName());
    }

    private static String requireString(Map<String, Object> map, String key, String context) {
        Object v = map.get(key);
        if (v == null || v.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required field '" + key + "' in " + context);
        }
        return v.toString().trim();
    }

    private static int requireInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required field '" + key + "'");
        if (v instanceof Integer) return (Integer) v;
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + key + "' must be an integer, got: " + v);
        }
    }

    private static String optString(Map<String, Object> map, String key, String defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        String s = v.toString().trim();
        return s.isEmpty() ? defaultVal : s;
    }

    private static boolean optBool(Map<String, Object> map, String key, boolean defaultVal) {
        Object v = map.get(key);
        if (v == null) return defaultVal;
        if (v instanceof Boolean) return (Boolean) v;
        String s = v.toString().trim().toLowerCase();
        if ("true".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "no".equals(s)) return false;
        return defaultVal;
    }

    private static List<String> optStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return Collections.emptyList();
        if (v instanceof List) {
            List<String> out = new ArrayList<>();
            for (Object item : (List<?>) v) {
                if (item != null) {
                    String s = item.toString().trim();
                    if (!s.isEmpty()) out.add(s);
                }
            }
            return out;
        }
        // Single scalar -> list of one
        String s = v.toString().trim();
        return s.isEmpty() ? Collections.emptyList() : Collections.singletonList(s);
    }

    // ---- Exception ----

    public static class ConfigLoadException extends Exception {
        public ConfigLoadException(String message) { super(message); }
        public ConfigLoadException(String message, Throwable cause) { super(message, cause); }
    }
}