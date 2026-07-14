package org.rhapsodychecker.rhapsodyruleverifier.core.selector;

import org.rhapsodychecker.rhapsodyruleverifier.config.ElementSetDefinition;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.SetOps;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
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

    private final Map<String, Set<String>> resolvedSetCache = new HashMap<>();

    public ElementSelector(ElementIndex index, RuleCheckerConfig config) {
        this.index = Objects.requireNonNull(index, "index");
        this.config = Objects.requireNonNull(config, "config");
    }

    public List<ElementRecord> selectCandidates(RuleSpec rule) {
        Set<String> guids;

        if (rule.appliesToSet().isPresent()) {
            guids = resolveElementSet(rule.appliesToSet().get());
        } else {
            guids = resolveInlineFilters(
                    rule.appliesToTypes(),
                    rule.appliesToStereotypes(),
                    Collections.emptyList(),
                    rule.appliesToIncludePackages(),
                    rule.appliesToExcludePackages()
            );
        }

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
                setDef.kinds(),
                setDef.includePackages(),
                setDef.excludePackages()
        );
    }

    private Set<String> resolveInlineFilters(
            List<String> types,
            List<String> stereotypes,
            List<String> kinds,
            List<String> includePackages,
            List<String> excludePackages) {

        Set<String> result = null;

        // Kinds filter (union across specified kinds)
        if (kinds != null && !kinds.isEmpty()) {
            List<Set<String>> kindSets = new ArrayList<>();
            for (String k : kinds) {
                try {
                    ElementKind ek = ElementKind.valueOf(k.toUpperCase());
                    kindSets.add(index.guidsByKind(ek));
                } catch (IllegalArgumentException e) { /* skip invalid kind */ }
            }
            result = SetOps.union(kindSets);
        }

        // Types filter (union across all specified types)
        if (types != null && !types.isEmpty()) {
            List<Set<String>> typeSets = new ArrayList<>();
            for (String t : types) {
                typeSets.add(index.guidsByMetaClass(t, true));
            }
            Set<String> typeUnion = SetOps.union(typeSets);

            if (result != null) {
                result = SetOps.intersect(result, typeUnion);
            } else {
                result = typeUnion;
            }
        }

        // Stereotypes filter (union across all specified stereotypes)
        if (stereotypes != null && !stereotypes.isEmpty()) {
            List<Set<String>> stereoSets = new ArrayList<>();
            for (String s : stereotypes) {
                stereoSets.add(index.guidsByStereotype(s, true));
            }
            Set<String> stereoUnion = SetOps.union(stereoSets);

            if (result != null) {
                result = SetOps.intersect(result, stereoUnion);
            } else {
                result = stereoUnion;
            }
        }

        // If nothing specified, start with all elements
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
                // Skip invalid regex
            }
        }
        return out;
    }
}
