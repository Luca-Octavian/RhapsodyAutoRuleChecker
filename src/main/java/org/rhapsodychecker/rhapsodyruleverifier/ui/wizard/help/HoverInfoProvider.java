// ui/wizard/help/HoverInfoProvider.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import java.util.HashMap;
import java.util.Map;

/**
 * Central dictionary: logical key (not the internal name from YAML/Rhapsody)
 * -> user-friendly description.
 *
 * Keys are grouped by entity: "alias.*", "elementSet.*", "rule.*",
 * "rule.params.<ruleType>.*". A single place to edit descriptions,
 * no matter how many dialogs the field appears in.
 */
public final class HoverInfoProvider {

    public static final class FieldHelp {
        private final String friendlyLabel;
        private final String description;
        private final String example;

        public FieldHelp(String friendlyLabel, String description, String example) {
            this.friendlyLabel = friendlyLabel;
            this.description   = description;
            this.example       = example;
        }

        public String friendlyLabel() { return friendlyLabel; }
        public String description()   { return description; }
        public String example()       { return example; }
    }

    private static final Map<String, FieldHelp> REGISTRY = new HashMap<>();

    private static void put(String key, String label, String desc) {
        put(key, label, desc, null);
    }

    private static void put(String key, String label, String desc, String example) {
        REGISTRY.put(key, new FieldHelp(label, desc, example));
    }

    static {
        // ── Top-level ──────────────────────────────────────────────────────
        put("top.schemaVersion", "Schema Version",
                "The version of the configuration schema. Leave at 1 if unsure.");
        put("top.mode", "Mode",
                "Behavior on errors: 'lenient' only reports issues, "
                        + "'strict' stops evaluation at the first error.");

        // ── Alias ──────────────────────────────────────────────────────────
        put("alias.id", "Alias ID",
                "The identifier used by rules to reference this alias.",
                "e.g. ELEMENT_DESCRIPTION");
        put("alias.kind", "Kind",
                "The source of the value: description, name, or tagged value.");
        put("alias.title", "Title",
                "Friendly name shown in the interface (optional).");
        put("alias.help", "Help",
                "Short description of this alias (optional).");
        put("alias.valueType", "Value Type",
                "The expected type of the value (string/int/enum/bool). "
                        + "Only relevant for taggedValue, defaults to string.");
        put("alias.profileName", "Profile Name",
                "The Rhapsody profile that owns the tag or stereotypes.");
        put("alias.tagName", "Tag Name",
                "The name of the tag defined in the profile.");
        put("alias.stereotypeOwner", "Stereotype Owner",
                "The stereotype that owns this tag (optional, only for taggedValue).");
        put("alias.stereotypeName", "Stereotype Name",
                "The name of the stereotype being searched for.");
        put("alias.stereotypeNames", "Stereotype Names",
                "The list of stereotypes allowed for this alias.");
        put("alias.values", "Allowed Values",
                "The list of allowed values, if the value type is enum.");

        // ── Element Set ────────────────────────────────────────────────────
        put("elementSet.id", "Element Set ID",
                "The identifier used by rules to reference this set.",
                "e.g. ArchitectureBlocks");
        put("elementSet.title", "Title",
                "Friendly name shown in the interface (optional).");
        put("elementSet.kinds", "Kinds (advanced)",
                "Additional internal categories for filtering (advanced).");
        put("elementSet.types", "Element Type",
                "Filters elements based on internal Rhapsody type classifications; "
                        + "some model elements might not be internally named the same as "
                        + "their commonly referred to name. e.g. a \"part\" is under the "
                        + "umbrella \"Object\", while a block can be represented as a "
                        + "\"Class\" with stereotype block. Please use Kinds wherever "
                        + "possible to avoid confusion.",
                "e.g. Class (= Block), Port, Requirement");
        put("elementSet.stereotypes", "Stereotype",
                "An additional label placed on an element in the model.",
                "e.g. Block");
        put("elementSet.includePackages", "Include Packages",
                "Limits the set to elements from packages matching these regular expressions. "
                        + "Leave empty to include the whole model.",
                "e.g. com\\.acme\\..*");
        put("elementSet.excludePackages", "Exclude Packages",
                "Removes from the set elements from packages matching these regular expressions, "
                        + "even if they would match Include Packages.",
                "e.g. .*\\.test\\..*");

        // ── Rule (common) ──────────────────────────────────────────────────
        put("rule.id", "Rule ID",
                "The unique identifier of the rule.");
        put("rule.type", "Rule Type",
                "The type of verification logic applied.");
        put("rule.enabled", "Enabled",
                "Enables or disables this rule (enabled by default).");
        put("rule.title", "Title",
                "Short title shown in reports (optional).");
        put("rule.message", "Message",
                "Custom message shown on failure. Supports placeholders such as "
                        + "{elementName}, {value}.");
        put("rule.appliesToSet", "Applies To Set",
                "The Element Set that this rule applies to.");
        put("rule.target", "Target Alias",
                "The alias checked by this rule (e.g. Description, a Tag).");
        put("rule.conditions", "Conditions (advanced)",
                "Additional filters on top of the Element Set (advanced, optional).");

        // ── Rule params: RequiredValue ─────────────────────────────────────
        put("rule.params.requiredValue.checkMode", "Check Mode",
                "How should the value from the Target Alias be validated?\n\n"
                + "• Must not be empty — fails if the value is missing or blank.\n"
                + "• Must match specific value(s) — fails if the value is not one of the listed values.\n"
                + "• Length constraint — fails if the value's character count is outside the min/max range.\n"
                + "• Regex pattern — fails if the value doesn't match the given regular expression.\n"
                + "• Numeric comparison — treats the value as a number and compares it.");
        put("rule.params.requiredValue.values", "Allowed Values",
                "List the values that are considered valid, separated by commas.\n"
                + "The element passes if its value matches any one of these.\n\n"
                + "Example: ASIL_A, ASIL_B, ASIL_C, ASIL_D");
        put("rule.params.requiredValue.minLength", "Min Length",
                "The minimum number of characters the value must have. "
                + "Leave empty for no minimum.");
        put("rule.params.requiredValue.maxLength", "Max Length",
                "The maximum number of characters the value is allowed to have. "
                + "Leave empty for no maximum.");
        put("rule.params.requiredValue.pattern", "Regex Pattern",
                "A regular expression that the value must match.\n\n"
                + "Example: ^[A-Z][a-zA-Z0-9_]*$ (must start with uppercase letter)");
        put("rule.params.requiredValue.numericComp", "Numeric Comparison",
                "How to compare the value as a number. Pick a comparison type from the dropdown.");
        put("rule.params.requiredValue.numericValue", "Comparison Value",
                "The number to compare against.");
        put("rule.params.requiredValue.rangeMin", "Range Min",
                "The lower bound of the accepted range (inclusive).");
        put("rule.params.requiredValue.rangeMax", "Range Max",
                "The upper bound of the accepted range (inclusive).");

        // ── Rule: Group ─────────────────────────────────────────────────────
        put("rule.group", "Group",
                "Optional label that groups rules together. Rules sharing the same "
                        + "group are evaluated individually as normal. After evaluation, "
                        + "results are conjoined per element: an element passes the group "
                        + "only if ALL rules in the group pass for it. Leave empty for "
                        + "ungrouped rules.");

        // ── Rule params: NamingPattern ──────────────────────────────────────
        put("rule.params.namingPattern.pattern", "Pattern",
                "Regular expression that the name must match.",
                "e.g. ^[A-Z][a-zA-Z0-9]*$");

        // ── Rule params: RelationExists ─────────────────────────────────────
        put("rule.params.relationExists.relationKind", "Relation Kind",
                "What type of relation to look for.\n\n"
                + "• any — matches all relation types\n"
                + "• dependency — a uses/depends-on link\n"
                + "• association — a structural link\n"
                + "• generalization — an inheritance link\n\n"
                + "Leave at 'any' if you don't need to filter by relation type.");
        put("rule.params.relationExists.direction", "Direction",
                "Which direction of the relation to count.\n\n"
                + "• any — both outgoing and incoming\n"
                + "• outgoing — only relations FROM this element TO another\n"
                + "• incoming — only relations FROM another element TO this one");
        put("rule.params.relationExists.relationStereotypes", "Relation Stereotypes",
                "Only count relations that have one of these stereotypes.\n"
                + "For example, selecting 'satisfy' counts only traceability links "
                + "stereotyped as <<satisfy>>. Leave empty to match any stereotype.");
        put("rule.params.relationExists.count", "Required Count",
                "How many matching relations the element must have.\n\n"
                + "Example: 'At least 1' means every element needs at least one "
                + "matching relation. 'Exactly 2' means it must have precisely two.");

        // ── Rule params: RequiredStereotype ──────────────────────────────────
        put("rule.params.requiredStereotype.stereotype", "Stereotype",
                "The stereotype required on the element.");

        // ── Rule params: RequiredStereotypeOneOf ─────────────────────────────
        put("rule.params.requiredStereotypeOneOf.stereotypes", "Stereotypes",
                "At least one of these stereotypes must be present.");

        // ── Rule params: OwnerStereotypeConstraint ───────────────────────────
        put("rule.params.ownerConstraint.ownerStereotype", "Owner Stereotype",
                "The stereotype that the element's OWNER must have for this rule to apply. "
                + "If the owner does not have this stereotype, the rule is skipped for that element.",
                "e.g. Logical-Element, Technical-Element");
        put("rule.params.ownerConstraint.allowedKinds", "Allowed Kinds",
                "The ElementKind values that the element is allowed to have when its owner "
                + "has the specified stereotype. At least one must be selected.",
                "e.g. PORT_FLOW for Logical blocks, PORT for Technical blocks");
    }

    public static FieldHelp get(String key) {
        return REGISTRY.getOrDefault(key,
                new FieldHelp(key, "No description available.", null));
    }
}