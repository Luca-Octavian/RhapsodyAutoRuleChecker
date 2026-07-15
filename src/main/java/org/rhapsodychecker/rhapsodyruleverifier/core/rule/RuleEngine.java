
package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.util.*;
import java.util.stream.Collectors;

public final class RuleEngine {

    private final RuleCheckerConfig config;
    private final ElementSelector selector;
    private final EvaluationContext context;
    private final String scopePath; // empty = full model

    public RuleEngine(RuleCheckerConfig config, ElementSelector selector, EvaluationContext context) {
        this(config, selector, context, "");
    }

    public RuleEngine(RuleCheckerConfig config, ElementSelector selector, EvaluationContext context, String scopePath) {
        this.config = Objects.requireNonNull(config, "config");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.context = Objects.requireNonNull(context, "context");
        this.scopePath = scopePath != null ? scopePath : "";
    }

    public List<RuleResult> evaluateAll() {
        List<RuleResult> allResults = new ArrayList<>();
        List<RuleSpec> enabledSpecs = config.enabledRules();

        for (RuleSpec spec : enabledSpecs) {
            try {
                Rule rule = RuleFactory.createRule(spec);
                List<ElementRecord> candidates = selector.selectCandidates(spec);

                // Filter candidates by scope BEFORE evaluation
                if (!scopePath.isEmpty()) {
                    candidates = candidates.stream()
                            .filter(c -> c.ownerPath()
                                    .map(p -> p.equals(scopePath) || p.startsWith(scopePath + "::"))
                                    .orElse(false))
                            .collect(Collectors.toList());
                }

                for (ElementRecord candidate : candidates) {
                    try {
                        if (rule.appliesTo(candidate, context)) {
                            RuleResult result = rule.evaluate(candidate, context);
                            allResults.add(result);
                        }
                    } catch (Throwable t) {
                        allResults.add(DefaultRuleResult.skipped(spec.id(), candidate.guid(),
                                "Unexpected error: " + t.getMessage()));
                    }
                }
            } catch (Throwable t) {
                allResults.add(DefaultRuleResult.skipped(spec.id(), "N/A",
                        "Rule could not be created: " + t.getMessage()));
            }
        }

        return Collections.unmodifiableList(allResults);
    }

    public EvaluationSummary evaluateWithSummary() {
        List<RuleResult> results = evaluateAll();
        return new EvaluationSummary(results);
    }

    public static final class EvaluationSummary {
        private final List<RuleResult> allResults;
        private final List<RuleResult> passed;
        private final List<RuleResult> failed;
        private final List<RuleResult> skipped;

        public EvaluationSummary(List<RuleResult> allResults) {
            this.allResults = Collections.unmodifiableList(new ArrayList<>(allResults));
            List<RuleResult> p = new ArrayList<>();
            List<RuleResult> f = new ArrayList<>();
            List<RuleResult> s = new ArrayList<>();
            for (RuleResult r : allResults) {
                switch (r.status()) {
                    case PASS: p.add(r); break;
                    case FAIL: f.add(r); break;
                    case SKIPPED: s.add(r); break;
                }
            }
            this.passed = Collections.unmodifiableList(p);
            this.failed = Collections.unmodifiableList(f);
            this.skipped = Collections.unmodifiableList(s);
        }

        public List<RuleResult> allResults() { return allResults; }
        public List<RuleResult> passed() { return passed; }
        public List<RuleResult> failed() { return failed; }
        public List<RuleResult> skipped() { return skipped; }
        public int totalCount() { return allResults.size(); }
        public int passCount() { return passed.size(); }
        public int failCount() { return failed.size(); }
        public int skipCount() { return skipped.size(); }

        @Override
        public String toString() {
            return "EvaluationSummary{total=" + totalCount()
                    + ", pass=" + passCount()
                    + ", fail=" + failCount()
                    + ", skipped=" + skipCount() + "}";
        }
    }
}
