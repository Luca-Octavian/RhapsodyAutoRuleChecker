package org.rhapsodychecker.rhapsodyruleverifier.core.selector;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.SetOps;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Resolves elementSets and appliesTo into concrete sets of ElementRecord candidates
 * using the pre-built ElementIndex.
 */
public final class ElementSelector {

    private final ElementIndex index;
    private final RuleCheckerConfig config;

    // Cache resolved element sets by set id
    private final Map<String, Set<String>> resolvedSetCache = new HashMap<>();

    public ElementSelector(ElementIndex index, RuleCheckerConfig config) {
        this.index = Objects.requireNonNull(index, "index");
        this.config = Objects.requireNonNull(config, "config");
    }

    /**
     * Resolve candidates for a rule based on its appliesTo configuration.
     * Uses set reference, inline types/stereotypes, and package filters.
     */
    public List<ElementRecord> selectCandidates(RuleSpec rule) {
        Set<String> guids;

        if (rule.appliesToSet().isPresent()) {
            // Start from the named element set
            guids = resolveElementSet(rule.appliesToSet().get());
        } else {
            // Start from inline filters
            guids = resolveInlineFilters(
                    rule.appliesToTypes(),
                    rule.appliesToStereotypes(),
                    rule.appliesToIncludePackages(),
                    rule.appliesToExcludePackages()
            );
        }

        // Apply additional inline filters on top of set (if both set and inline filters are present)
        if (rule.appliesToSet().isPresent()) {
            if (!rule.appliesToIncludePackages().isEmpty()) {
                Set<String> pkgFiltered = filterByIncludePackages(guids, rule.appliesToIncludePackages());
                guids = SetOps.intersect(guids, pkgFiltered);
            }
            if (!rule.appliesToExcludePackages().isEmpty()) {
                guids = filterByExcludePackages(guids, rule.appliesToExcludePackages());
            }
        }

        return index.toRecords(guids);
    }

    /**
     * Resolve a named element set from config into a set of GUIDs.
     * Results are cached per set id for the lifetime of this selector.
     */
    public Set<String> resolveElementSet(String setId) {
        return resolvedSetCache.computeIfAbsent(setId, this::computeElementSet);
    }

    private Set<String> computeElementSet(String setId) {
        Optional<ElementSetDefinition> optSet = config.elementSet(setId);
        if (!optSet.isPresent()) {
            return Collections.emptySet();
        }
        ElementSetDefinition setDef = optSet.get();

        return resolveInlineFilters(
                setDef.types(),
                setDef.stereotypes(),
                setDef.includePackages(),
                setDef.excludePackages()
        );
    }

    /**
     * Resolve inline type/stereotype/package filters into a GUID set.
     *
     * Logic:
     * - Types: union of all matching metaClasses
     * - Stereotypes: union of all matching stereotypes
     * - If both types and stereotypes specified: intersect (element must match both)
     * - Include packages: keep only elements whose ownerPath matches any pattern
     * - Exclude packages: remove elements whose ownerPath matches any pattern
     */
    private Set<String> resolveInlineFilters(
            List<String> types,
            List<String> stereotypes,
            List<String> includePackages,
            List<String> excludePackages) {

        Set<String> result = null;

        // Types filter (union across all specified types)
        if (types != null && !types.isEmpty()) {
            List<Set<String>> typeSets = new ArrayList<>();
            for (String t : types) {
                typeSets.add(index.guidsByMetaClass(t, true));
            }
            result = SetOps.union(typeSets);
        }

        // Stereotypes filter (union across all specified stereotypes)
        if (stereotypes != null && !stereotypes.isEmpty()) {
            List<Set<String>> stereoSets = new ArrayList<>();
            for (String s : stereotypes) {
                stereoSets.add(index.guidsByStereotype(s, true));
            }
            Set<String> stereoUnion = SetOps.union(stereoSets);

            if (result != null) {
                // Both types and stereotypes specified: intersect
                result = SetOps.intersect(result, stereoUnion);
            } else {
                result = stereoUnion;
            }
        }

        // If no types or stereotypes specified, start with all elements
        if (result == null) {
            result = new LinkedHashSet<>();
            for (ElementRecord r : index.repository().allRecords()) {
                result.add(r.guid());
            }
        }

        // Include packages filter
        if (includePackages != null && !includePackages.isEmpty()) {
            result = filterByIncludePackages(result, includePackages);
        }

        // Exclude packages filter
        if (excludePackages != null && !excludePackages.isEmpty()) {
            result = filterByExcludePackages(result, excludePackages);
        }

        return Collections.unmodifiableSet(result);
    }

    /**
     * Keep only GUIDs whose ownerPath matches at least one include pattern.
     */
    private Set<String> filterByIncludePackages(Set<String> guids, List<String> patterns) {
        List<Pattern> compiled = compilePatterns(patterns);
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String guid : guids) {
            index.repository().get(guid).ifPresent(r -> {
                String path = r.ownerPath().orElse("");
                for (Pattern p : compiled) {
                    if (p.matcher(path).find()) {
                        out.add(guid);
                        break;
                    }
                }
            });
        }
        return out;
    }

    /**
     * Remove GUIDs whose ownerPath matches any exclude pattern.
     */
    private Set<String> filterByExcludePackages(Set<String> guids, List<String> patterns) {
        List<Pattern> compiled = compilePatterns(patterns);
        LinkedHashSet<String> out = new LinkedHashSet<>(guids);
        Iterator<String> it = out.iterator();
        while (it.hasNext()) {
            String guid = it.next();
            index.repository().get(guid).ifPresent(r -> {
                String path = r.ownerPath().orElse("");
                for (Pattern p : compiled) {
                    if (p.matcher(path).find()) {
                        it.remove();
                        break;
                    }
                }
            });
        }
        return out;
    }

    private static List<Pattern> compilePatterns(List<String> regexes) {
        List<Pattern> out = new ArrayList<>(regexes.size());
        for (String r : regexes) {
            try {
                out.add(Pattern.compile(r));
            } catch (Throwable t) {
                // Skip invalid regex; could log a warning
            }
        }
        return out;
    }
}