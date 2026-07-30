package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;

import java.util.*;

/**
 * Pure post-processing: given a flat list of individual rule results and the
 * corresponding rule specs, computes per-(group, element) conjunction verdicts.
 *
 * <p>A "group" is an optional label on a RuleSpec. Rules that share the same
 * group label are evaluated individually as normal rules. After evaluation,
 * this class groups the results by (group, elementGuid) and derives a single
 * boolean verdict: the element passes the group only if ALL rules in the group
 * passed for that element.
 *
 * <p>This replaces the old ALL_OF composite rule type with a simpler mechanism
 * that requires no special rule implementation — just a label.
 */
public final class RuleGrouping {

    private RuleGrouping() {}

    /**
     * A per-(group, element) conjunction verdict.
     */
    public static final class GroupVerdict {
        private final String group;
        private final String elementGuid;
        private final boolean allPassed;
        private final List<RuleResult> memberResults;

        GroupVerdict(String group, String elementGuid, boolean allPassed,
                     List<RuleResult> memberResults) {
            this.group = group;
            this.elementGuid = elementGuid;
            this.allPassed = allPassed;
            this.memberResults = Collections.unmodifiableList(memberResults);
        }

        /** The group label. */
        public String group() { return group; }

        /** The element GUID this verdict applies to. */
        public String elementGuid() { return elementGuid; }

        /** True if every rule in the group passed for this element. */
        public boolean allPassed() { return allPassed; }

        /** The individual results from each rule in the group for this element. */
        public List<RuleResult> memberResults() { return memberResults; }

        /** Returns the subset of member results that failed. */
        public List<RuleResult> failedMembers() {
            List<RuleResult> out = new ArrayList<>();
            for (RuleResult r : memberResults) {
                if (r.status() == RuleStatus.FAIL) {
                    out.add(r);
                }
            }
            return out;
        }
    }

    /**
     * Computes group verdicts from evaluation results.
     *
     * @param results all rule results (may include results from ungrouped rules)
     * @param specs   the rule specs (used to look up group labels by rule id)
     * @return list of group verdicts, one per (group, element) combination;
     *         empty if no rules have a group label
     */
    public static List<GroupVerdict> computeVerdicts(List<RuleResult> results,
                                                      List<RuleSpec> specs) {
        // Build ruleId -> group lookup
        Map<String, String> ruleIdToGroup = new LinkedHashMap<>();
        for (RuleSpec spec : specs) {
            if (spec.group().isPresent()) {
                ruleIdToGroup.put(spec.id(), spec.group().get());
            }
        }

        if (ruleIdToGroup.isEmpty()) {
            return Collections.emptyList();
        }

        // Bucket results by (group, elementGuid)
        // Key = group + "\0" + elementGuid
        Map<String, List<RuleResult>> buckets = new LinkedHashMap<>();
        for (RuleResult r : results) {
            String group = ruleIdToGroup.get(r.ruleId());
            if (group == null) continue;

            String key = group + "\0" + r.elementGuid();
            List<RuleResult> bucket = buckets.get(key);
            if (bucket == null) {
                bucket = new ArrayList<>();
                buckets.put(key, bucket);
            }
            bucket.add(r);
        }

        // Derive verdicts
        List<GroupVerdict> verdicts = new ArrayList<>();
        for (Map.Entry<String, List<RuleResult>> entry : buckets.entrySet()) {
            String[] parts = entry.getKey().split("\0", 2);
            String group = parts[0];
            String elementGuid = parts[1];
            List<RuleResult> members = entry.getValue();

            boolean allPassed = true;
            for (RuleResult r : members) {
                if (r.status() == RuleStatus.FAIL) {
                    allPassed = false;
                    break;
                }
            }

            verdicts.add(new GroupVerdict(group, elementGuid, allPassed, members));
        }

        return verdicts;
    }

    /**
     * Collects all distinct group labels from the given specs.
     */
    public static Set<String> collectGroups(List<RuleSpec> specs) {
        Set<String> groups = new LinkedHashSet<>();
        for (RuleSpec spec : specs) {
            if (spec.group().isPresent()) {
                groups.add(spec.group().get());
            }
        }
        return groups;
    }
}