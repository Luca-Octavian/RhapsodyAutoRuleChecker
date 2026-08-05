package org.rhapsodychecker.rhapsodyruleverifier.core.profiler;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;

import java.util.*;

/**
 * Deep, order-independent comparison used to prove that a loader optimization
 * preserves every field currently captured by the application.
 *
 * <p>This class deliberately compares more than aggregate counts. Records are
 * keyed by GUID, duplicate GUIDs are reported, relation/reference lists are
 * compared as multisets, and rule results include status, message and details.
 */
public final class SnapshotEquivalence {

    private static final int DEFAULT_MAX_DETAILS = 40;

    private SnapshotEquivalence() {}

    public static Report compare(RhapsodyModelSnapshot baseline,
                                 RhapsodyModelSnapshot candidate) {
        return compare(baseline, candidate, DEFAULT_MAX_DETAILS);
    }

    public static Report compare(RhapsodyModelSnapshot baseline,
                                 RhapsodyModelSnapshot candidate,
                                 int maxDetails) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(candidate, "candidate");

        Collector out = new Collector(maxDetails);
        Map<String, List<ElementRecord>> left = recordsByGuid(baseline.records());
        Map<String, List<ElementRecord>> right = recordsByGuid(candidate.records());

        compareDuplicateCounts("record", left, right, out);

        Set<String> allGuids = new TreeSet<String>();
        allGuids.addAll(left.keySet());
        allGuids.addAll(right.keySet());

        for (String guid : allGuids) {
            List<ElementRecord> a = left.get(guid);
            List<ElementRecord> b = right.get(guid);
            if (a == null) {
                out.add("record missing from baseline: " + guid);
                continue;
            }
            if (b == null) {
                out.add("record missing from candidate: " + guid);
                continue;
            }
            if (a.size() == 1 && b.size() == 1) {
                compareRecord(guid, a.get(0), b.get(0), out);
            } else {
                compareMultiset("duplicate records " + guid,
                        canonicalRecords(a), canonicalRecords(b), out);
            }
        }

        compareNestedMultiset("relationsByOwner",
                canonicalRelations(baseline.relationsByOwner()),
                canonicalRelations(candidate.relationsByOwner()), out);
        compareNestedMultiset("referencesByElement",
                canonicalReferences(baseline.referencesByElement()),
                canonicalReferences(candidate.referencesByElement()), out);

        compareSet("handle GUIDs",
                baseline.handleByGuid().keySet(),
                candidate.handleByGuid().keySet(), out);

        return out.report();
    }

    public static Report compareRuleResults(List<RuleResult> baseline,
                                            List<RuleResult> candidate) {
        return compareRuleResults(baseline, candidate, DEFAULT_MAX_DETAILS);
    }

    public static Report compareRuleResults(List<RuleResult> baseline,
                                            List<RuleResult> candidate,
                                            int maxDetails) {
        Collector out = new Collector(maxDetails);
        compareMultiset("rule results",
                canonicalRuleResults(baseline),
                canonicalRuleResults(candidate), out);
        return out.report();
    }

    private static Map<String, List<ElementRecord>> recordsByGuid(List<ElementRecord> records) {
        Map<String, List<ElementRecord>> result =
                new LinkedHashMap<String, List<ElementRecord>>();
        for (ElementRecord record : records) {
            List<ElementRecord> values = result.get(record.guid());
            if (values == null) {
                values = new ArrayList<ElementRecord>();
                result.put(record.guid(), values);
            }
            values.add(record);
        }
        return result;
    }

    private static void compareDuplicateCounts(String label,
                                                Map<String, ? extends List<?>> a,
                                                Map<String, ? extends List<?>> b,
                                                Collector out) {
        Set<String> keys = new TreeSet<String>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            int ac = a.containsKey(key) ? a.get(key).size() : 0;
            int bc = b.containsKey(key) ? b.get(key).size() : 0;
            if (ac != bc) {
                out.add(label + " multiplicity differs for " + key
                        + ": baseline=" + ac + ", candidate=" + bc);
            }
        }
    }

    private static void compareRecord(String guid, ElementRecord a,
                                      ElementRecord b, Collector out) {
        compareValue(guid, "name", a.name(), b.name(), out);
        compareValue(guid, "metaClass", a.metaClass(), b.metaClass(), out);
        compareValue(guid, "kind", a.kind(), b.kind(), out);
        compareValue(guid, "ownerGuid", a.ownerGuid().orElse(null),
                b.ownerGuid().orElse(null), out);
        compareValue(guid, "ownerPath", a.ownerPath().orElse(null),
                b.ownerPath().orElse(null), out);
        compareValue(guid, "stereotypes", a.stereotypes(), b.stereotypes(), out);
        compareValue(guid, "typeGuid", a.typeGuid().orElse(null),
                b.typeGuid().orElse(null), out);
        compareValue(guid, "typeName", a.typeName().orElse(null),
                b.typeName().orElse(null), out);
        compareValue(guid, "description", a.description().orElse(null),
                b.description().orElse(null), out);
        compareValue(guid, "portDirection", a.portDirection().orElse(null),
                b.portDirection().orElse(null), out);
        compareValue(guid, "portMultiplicity", a.portMultiplicity().orElse(null),
                b.portMultiplicity().orElse(null), out);
        compareValue(guid, "initialValue", a.initialValue().orElse(null),
                b.initialValue().orElse(null), out);
        compareValue(guid, "tagValues", a.tagValues(), b.tagValues(), out);
    }

    private static void compareValue(String guid, String field,
                                     Object a, Object b, Collector out) {
        if (!Objects.equals(a, b)) {
            out.add("record " + guid + " field " + field + " differs: baseline="
                    + printable(a) + ", candidate=" + printable(b));
        }
    }

    private static List<String> canonicalRecords(List<ElementRecord> records) {
        List<String> result = new ArrayList<String>();
        if (records != null) {
            for (ElementRecord r : records) {
                result.add(r.guid() + "|" + r.name() + "|" + r.metaClass() + "|"
                        + r.kind() + "|" + r.ownerGuid().orElse(null) + "|"
                        + r.ownerPath().orElse(null) + "|" + sorted(r.stereotypes()) + "|"
                        + r.typeGuid().orElse(null) + "|" + r.typeName().orElse(null) + "|"
                        + r.description().orElse(null) + "|"
                        + r.portDirection().orElse(null) + "|"
                        + r.portMultiplicity().orElse(null) + "|"
                        + r.initialValue().orElse(null) + "|" + sortedMap(r.tagValues()));
            }
        }
        return result;
    }

    private static Map<String, List<String>> canonicalRelations(
            Map<String, List<RhapsodyModelSnapshot.RelationInfo>> source) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>();
        for (Map.Entry<String, List<RhapsodyModelSnapshot.RelationInfo>> entry
                : source.entrySet()) {
            List<String> values = new ArrayList<String>();
            for (RhapsodyModelSnapshot.RelationInfo r : entry.getValue()) {
                values.add(r.guid() + "|" + r.metaClass() + "|"
                        + sorted(r.stereotypes()) + "|" + r.otherEndGuid());
            }
            result.put(entry.getKey(), values);
        }
        return result;
    }

    private static Map<String, List<String>> canonicalReferences(
            Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> source) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>();
        for (Map.Entry<String, List<RhapsodyModelSnapshot.ReferenceInfo>> entry
                : source.entrySet()) {
            List<String> values = new ArrayList<String>();
            for (RhapsodyModelSnapshot.ReferenceInfo r : entry.getValue()) {
                values.add(r.guid() + "|" + r.metaClass() + "|"
                        + sorted(r.stereotypes()));
            }
            result.put(entry.getKey(), values);
        }
        return result;
    }

    private static List<String> canonicalRuleResults(List<RuleResult> source) {
        List<String> result = new ArrayList<String>();
        if (source == null) return result;
        for (RuleResult r : source) {
            result.add(printable(r.ruleId()) + "|" + printable(r.elementGuid()) + "|"
                    + printable(r.status()) + "|" + printable(r.message()) + "|"
                    + canonicalObject(r.details().orElse(null)));
        }
        return result;
    }

    private static void compareNestedMultiset(String label,
                                               Map<String, List<String>> a,
                                               Map<String, List<String>> b,
                                               Collector out) {
        Set<String> keys = new TreeSet<String>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        for (String key : keys) {
            compareMultiset(label + "[" + key + "]", a.get(key), b.get(key), out);
        }
    }

    private static void compareMultiset(String label, List<String> a,
                                        List<String> b, Collector out) {
        Map<String, Integer> ac = frequencies(a);
        Map<String, Integer> bc = frequencies(b);
        Set<String> values = new TreeSet<String>();
        values.addAll(ac.keySet());
        values.addAll(bc.keySet());
        for (String value : values) {
            int left = ac.containsKey(value) ? ac.get(value) : 0;
            int right = bc.containsKey(value) ? bc.get(value) : 0;
            if (left != right) {
                out.add(label + " multiplicity differs: baseline=" + left
                        + ", candidate=" + right + ", value=" + value);
            }
        }
    }

    private static Map<String, Integer> frequencies(List<String> values) {
        Map<String, Integer> result = new TreeMap<String, Integer>();
        if (values == null) return result;
        for (String value : values) {
            Integer count = result.get(value);
            result.put(value, count == null ? 1 : count + 1);
        }
        return result;
    }

    private static void compareSet(String label, Set<String> a,
                                   Set<String> b, Collector out) {
        Set<String> onlyA = new TreeSet<String>(a);
        onlyA.removeAll(b);
        Set<String> onlyB = new TreeSet<String>(b);
        onlyB.removeAll(a);
        for (String value : onlyA) out.add(label + " missing from candidate: " + value);
        for (String value : onlyB) out.add(label + " missing from baseline: " + value);
    }

    private static String sorted(Collection<?> values) {
        List<String> result = new ArrayList<String>();
        if (values != null) {
            for (Object value : values) result.add(printable(value));
        }
        Collections.sort(result);
        return result.toString();
    }

    private static String sortedMap(Map<?, ?> values) {
        if (values == null) return "null";
        Map<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            result.put(printable(entry.getKey()), canonicalObject(entry.getValue()));
        }
        return result.toString();
    }

    private static String canonicalObject(Object value) {
        if (value == null) return "null";
        if (value instanceof Map) return sortedMap((Map<?, ?>) value);
        if (value instanceof Collection) return sorted((Collection<?>) value);
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<String> items = new ArrayList<String>();
            for (int i = 0; i < length; i++) {
                items.add(canonicalObject(java.lang.reflect.Array.get(value, i)));
            }
            return items.toString();
        }
        return String.valueOf(value);
    }

    private static String printable(Object value) {
        return value == null ? "<null>" : String.valueOf(value);
    }

    public static final class Report {
        private final int differenceCount;
        private final List<String> details;

        private Report(int differenceCount, List<String> details) {
            this.differenceCount = differenceCount;
            this.details = Collections.unmodifiableList(
                    new ArrayList<String>(details));
        }

        public boolean isEquivalent() { return differenceCount == 0; }
        public int differenceCount() { return differenceCount; }
        public List<String> details() { return details; }

        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append("DATA EQUIVALENCE: ")
                    .append(isEquivalent() ? "PASS" : "FAIL")
                    .append(" — ").append(differenceCount).append(" difference(s)");
            for (String detail : details) {
                sb.append(System.lineSeparator()).append("  - ").append(detail);
            }
            if (differenceCount > details.size()) {
                sb.append(System.lineSeparator()).append("  ... ")
                        .append(differenceCount - details.size())
                        .append(" additional difference(s) omitted");
            }
            return sb.toString();
        }

        @Override
        public String toString() { return summary(); }
    }

    private static final class Collector {
        private final int maxDetails;
        private final List<String> details = new ArrayList<String>();
        private int count;

        private Collector(int maxDetails) {
            this.maxDetails = Math.max(0, maxDetails);
        }

        private void add(String detail) {
            count++;
            if (details.size() < maxDetails) details.add(detail);
        }

        private Report report() {
            return new Report(count, details);
        }
    }
}