package org.rhapsodychecker.rhapsodyruleverifier;

import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementKind;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.BenchmarkStatistics;
import org.rhapsodychecker.rhapsodyruleverifier.core.profiler.SnapshotEquivalence;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;

import java.util.*;

/**
 * Offline self-test for benchmark statistics and deep equivalence infrastructure.
 * Run directly from Eclipse; no Rhapsody process or native library calls are made.
 */
public final class ProfilerInfrastructureSmokeTest {

    private ProfilerInfrastructureSmokeTest() {}

    public static void main(String[] args) {
        testStatistics();
        testOrderIndependentSnapshotComparison();
        testFieldDifferenceDetection();
        testRuleResultMultisetComparison();
        System.out.println("ProfilerInfrastructureSmokeTest: PASS");
    }

    private static void testStatistics() {
        BenchmarkStatistics stats = BenchmarkStatistics.of(
                Arrays.asList(10.0, 20.0, 30.0, 40.0));
        require(stats.count() == 4, "statistics count");
        require(stats.min() == 10.0, "statistics min");
        require(stats.max() == 40.0, "statistics max");
        require(stats.mean() == 25.0, "statistics mean");
        require(stats.median() == 25.0, "statistics median");
        require(stats.p90() == 40.0, "statistics p90");
    }

    private static void testOrderIndependentSnapshotComparison() {
        ElementRecord first = record("g1", "First", "description one");
        ElementRecord second = record("g2", "Second", "description two");

        RhapsodyModelSnapshot.RelationInfo relation =
                new RhapsodyModelSnapshot.RelationInfo(
                        "rel1", "Dependency",
                        new LinkedHashSet<String>(Arrays.asList("B", "A")), "g2");
        RhapsodyModelSnapshot.ReferenceInfo reference =
                new RhapsodyModelSnapshot.ReferenceInfo(
                        "g2", "Class",
                        new LinkedHashSet<String>(Arrays.asList("Y", "X")));

        Map<String, List<RhapsodyModelSnapshot.RelationInfo>> relationsA =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.RelationInfo>>();
        relationsA.put("g1", Collections.singletonList(relation));
        Map<String, List<RhapsodyModelSnapshot.ReferenceInfo>> referencesA =
                new LinkedHashMap<String, List<RhapsodyModelSnapshot.ReferenceInfo>>();
        referencesA.put("g1", Collections.singletonList(reference));

        RhapsodyModelSnapshot a = new RhapsodyModelSnapshot(
                Arrays.asList(first, second),
                Collections.emptyMap(), relationsA, referencesA);
        RhapsodyModelSnapshot b = new RhapsodyModelSnapshot(
                Arrays.asList(second, first),
                Collections.emptyMap(), relationsA, referencesA);

        SnapshotEquivalence.Report report = SnapshotEquivalence.compare(a, b);
        require(report.isEquivalent(), "order-independent snapshot comparison: " + report);
    }

    private static void testFieldDifferenceDetection() {
        RhapsodyModelSnapshot a = snapshot(record("g1", "Element", "old"));
        RhapsodyModelSnapshot b = snapshot(record("g1", "Element", "new"));

        SnapshotEquivalence.Report report = SnapshotEquivalence.compare(a, b);
        require(!report.isEquivalent(), "description mismatch must be detected");
        require(report.differenceCount() == 1,
                "exact description mismatch count; actual=" + report.differenceCount());
    }

    private static void testRuleResultMultisetComparison() {
        RuleResult pass = result("rule", "g1", RuleStatus.PASS, "ok");
        RuleResult fail = result("rule", "g1", RuleStatus.FAIL, "failure");

        SnapshotEquivalence.Report same = SnapshotEquivalence.compareRuleResults(
                Arrays.asList(pass, fail), Arrays.asList(fail, pass));
        require(same.isEquivalent(), "rule result order independence");

        SnapshotEquivalence.Report duplicateMissing =
                SnapshotEquivalence.compareRuleResults(
                        Arrays.asList(pass, pass), Collections.singletonList(pass));
        require(!duplicateMissing.isEquivalent(),
                "duplicate rule-result multiplicity must be detected");
    }

    private static ElementRecord record(String guid, String name, String description) {
        Map<String, String> tags = new LinkedHashMap<String, String>();
        tags.put("priority", "high");
        return ElementRecord.builder()
                .guid(guid)
                .name(name)
                .metaClass("Class")
                .kind(ElementKind.BLOCK)
                .ownerGuid("owner")
                .ownerPath("Package")
                .stereotypes(new LinkedHashSet<String>(Arrays.asList("Block", "Custom")))
                .typeGuid("type-guid")
                .typeName("Type")
                .description(description)
                .portDirection("in")
                .portMultiplicity("1")
                .initialValue("0")
                .tagValues(tags)
                .build();
    }

    private static RhapsodyModelSnapshot snapshot(ElementRecord record) {
        return new RhapsodyModelSnapshot(
                Collections.singletonList(record),
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap());
    }

    private static RuleResult result(final String ruleId, final String elementGuid,
                                     final RuleStatus status, final String message) {
        return new RuleResult() {
            @Override public RuleStatus status() { return status; }
            @Override public String ruleId() { return ruleId; }
            @Override public String elementGuid() { return elementGuid; }
            @Override public String message() { return message; }
            @Override
            public Optional<Map<String, Object>> details() {
                Map<String, Object> values = new LinkedHashMap<String, Object>();
                values.put("source", "smoke");
                return Optional.of(values);
            }
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}