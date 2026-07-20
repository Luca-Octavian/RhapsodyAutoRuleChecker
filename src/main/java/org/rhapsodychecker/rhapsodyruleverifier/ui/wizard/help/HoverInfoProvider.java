// ui/wizard/help/HoverInfoProvider.java
package org.rhapsodychecker.rhapsodyruleverifier.ui.wizard.help;

import java.util.HashMap;
import java.util.Map;

/**
 * Dictionar central: cheie logica (nu numele intern din YAML/Rhapsody)
 * -> descriere prietenoasa pentru utilizator.
 *
 * Cheile sunt grupate pe entitate: "alias.*", "elementSet.*", "rule.*",
 * "rule.params.<ruleType>.*". Un singur loc de editat descrierile,
 * indiferent in cate dialoguri apare campul.
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
                "Versiunea schemei de configurare. Lasa 1 daca nu esti sigur.");
        put("top.mode", "Mode",
                "Comportamentul la erori: 'lenient' doar raporteaza problemele, "
                        + "'strict' opreste evaluarea la prima eroare.");

        // ── Alias ──────────────────────────────────────────────────────────
        put("alias.id", "Alias ID",
                "Identificatorul folosit de reguli pentru a referi acest alias.",
                "ex: ELEMENT_DESCRIPTION");
        put("alias.kind", "Kind",
                "Sursa valorii: description, name, tagged value sau stereotip.");
        put("alias.title", "Title",
                "Nume prietenos afisat in interfata (optional).");
        put("alias.help", "Help",
                "Descriere scurta a acestui alias (optional).");
        put("alias.valueType", "Value Type",
                "Tipul asteptat al valorii (string/int/enum/bool). "
                        + "Relevant doar pentru taggedValue, implicit string.");
        put("alias.profileName", "Profile Name",
                "Profilul Rhapsody care detine tag-ul sau stereotipurile.");
        put("alias.tagName", "Tag Name",
                "Numele tag-ului definit in profil.");
        put("alias.stereotypeOwner", "Stereotype Owner",
                "Stereotipul care detine acest tag (optional, doar pentru taggedValue).");
        put("alias.stereotypeName", "Stereotype Name",
                "Numele stereotipului cautat.");
        put("alias.stereotypeNames", "Stereotype Names",
                "Lista de stereotipuri permise pentru acest alias.");
        put("alias.values", "Allowed Values",
                "Lista valorilor permise, daca value type-ul este enum.");

        // ── Element Set ────────────────────────────────────────────────────
        put("elementSet.id", "Element Set ID",
                "Identificatorul folosit de reguli pentru a referi acest set.",
                "ex: ArchitectureBlocks");
        put("elementSet.title", "Title",
                "Nume prietenos afisat in interfata (optional).");
        put("elementSet.kinds", "Kinds (advanced)",
                "Categorii interne suplimentare de filtrare (avansat).");
        put("elementSet.types", "Element Type",
                "In Rhapsody, ce vezi in browser ca 'Block' este stocat intern "
                        + "sub numele de metaclasa 'Class'. Selecteaza tipurile de elemente incluse.",
                "ex: Class (= Block), Port, Requirement");
        put("elementSet.stereotypes", "Stereotype",
                "Eticheta suplimentara pusa pe un element in model.",
                "ex: Block");
        put("elementSet.includePackages", "Include Packages",
                "Limiteaza setul la elementele din pachetele ce se potrivesc cu aceste regex-uri. "
                        + "Lasa gol pentru a include tot modelul.",
                "ex: com\\.acme\\..*");
        put("elementSet.excludePackages", "Exclude Packages",
                "Elimina din set elementele din pachetele ce se potrivesc cu aceste regex-uri, "
                        + "chiar daca s-ar potrivi cu Include Packages.",
                "ex: .*\\.test\\..*");

        // ── Rule (comun) ───────────────────────────────────────────────────
        put("rule.id", "Rule ID",
                "Identificatorul unic al regulii.");
        put("rule.type", "Rule Type",
                "Tipul de logica de verificare aplicata.");
        put("rule.enabled", "Enabled",
                "Activeaza sau dezactiveaza aceasta regula (implicit activa).");
        put("rule.title", "Title",
                "Titlu scurt afisat in rapoarte (optional).");
        put("rule.message", "Message",
                "Mesaj custom afisat la esec. Suporta placeholder-e precum "
                        + "{elementName}, {value}.");
        put("rule.appliesToSet", "Applies To Set",
                "Element Set-ul caruia i se aplica aceasta regula.");
        put("rule.target", "Target Alias",
                "Alias-ul verificat de aceasta regula (ex: Description, un Tag).");
        put("rule.conditions", "Conditions (advanced)",
                "Filtre suplimentare pe langa Element Set (avansat, optional).");

        // ── Rule params: RequiredValue ─────────────────────────────────────
        put("rule.params.requiredValue.nonEmpty", "Non-empty",
                "Valoarea nu trebuie sa fie goala.");
        put("rule.params.requiredValue.minLength", "Min Length",
                "Lungimea minima acceptata a valorii.");
        put("rule.params.requiredValue.maxLength", "Max Length",
                "Lungimea maxima acceptata a valorii.");
        put("rule.params.requiredValue.operator", "Operator",
                "Operatorul de comparatie: eq, in, gte sau lte.");
        put("rule.params.requiredValue.value", "Value",
                "Valoarea asteptata (pentru eq/gte/lte).");
        put("rule.params.requiredValue.values", "Values",
                "Lista de valori acceptate (pentru operatorul 'in').");

        // ── Rule params: NamingPattern ──────────────────────────────────────
        put("rule.params.namingPattern.pattern", "Pattern",
                "Expresie regulata pe care numele trebuie sa o respecte.",
                "ex: ^[A-Z][a-zA-Z0-9]*$");

        // ── Rule params: RelationExists ─────────────────────────────────────
        put("rule.params.relationExists.relationKind", "Relation Kind",
                "Tipul de relatie numarat (dependency, association, ...).");
        put("rule.params.relationExists.direction", "Direction",
                "Directia relatiei: any, outgoing sau incoming.");
        put("rule.params.relationExists.relationStereotypes", "Relation Stereotypes",
                "Stereotipurile necesare pe relatie.");
        put("rule.params.relationExists.operator", "Operator",
                "Operatorul de comparatie a numarului de relatii: gte, lte sau eq.");
        put("rule.params.relationExists.value", "Value",
                "Numarul de relatii necesar.");
        put("rule.params.relationExists.targetSet", "Target Set",
                "Element Set-ul tinta al relatiei (optional).");

        // ── Rule params: RequiredStereotype ──────────────────────────────────
        put("rule.params.requiredStereotype.stereotype", "Stereotype",
                "Stereotipul necesar pe element.");

        // ── Rule params: RequiredStereotypeOneOf ─────────────────────────────
        put("rule.params.requiredStereotypeOneOf.stereotypes", "Stereotypes",
                "Cel putin unul dintre aceste stereotipuri trebuie sa fie prezent.");
    }

    public static FieldHelp get(String key) {
        return REGISTRY.getOrDefault(key,
                new FieldHelp(key, "No description available.", null));
    }
}