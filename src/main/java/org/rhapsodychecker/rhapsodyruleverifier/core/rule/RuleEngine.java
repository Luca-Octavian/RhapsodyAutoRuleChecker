package org.rhapsodychecker.rhapsodyruleverifier.core.rule;

import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleSpec;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.util.*;

public final class RuleEngine {

    private final RuleCheckerConfig  config;
    private final ElementSelector    selector;
    private final EvaluationContext  context;
    private final String             scopePath;
    private final ProgressReporter   reporter;

    public RuleEngine(RuleCheckerConfig config, ElementSelector selector,
                      EvaluationContext context) {
        this(config, selector, context, "", ProgressReporter.NOOP);
    }

    public RuleEngine(RuleCheckerConfig config, ElementSelector selector,
                      EvaluationContext context, String scopePath) {
        this(config, selector, context, scopePath, ProgressReporter.NOOP);
    }

    public RuleEngine(RuleCheckerConfig config, ElementSelector selector,
                      EvaluationContext context, String scopePath,
                      ProgressReporter reporter) {
        this.config    = Objects.requireNonNull(config,   "config");
        this.selector  = Objects.requireNonNull(selector, "selector");
        this.context   = Objects.requireNonNull(context,  "context");
        this.scopePath = scopePath != null ? scopePath : "";
        this.reporter  = reporter  != null ? reporter  : ProgressReporter.NOOP;
    }

    public List<RuleResult> evaluateAll() {
        List<RuleResult> allResults  = new ArrayList<>();
        List<RuleSpec>   enabledSpecs = config.enabledRules();

        // ── Single pass: select candidates once per rule, count total, then evaluate ──
        // This eliminates the old double-call pattern (countTotalCandidates + evaluateAll
        // both calling selectCandidates for every rule).

        // Phase 1: collect all candidate lists and count total
        List<List<ElementRecord>> allCandidates = new ArrayList<>(enabledSpecs.size());
        int totalCandidates = 0;

        for (RuleSpec spec : enabledSpecs) {
            try {
                List<ElementRecord> candidates = selector.selectCandidates(spec);

                if (!scopePath.isEmpty()) {
                    List<ElementRecord> filtered = new ArrayList<>();
                    for (ElementRecord c : candidates) {
                        String p = c.ownerPath().orElse(null);
                        if (p != null && (p.equals(scopePath) || p.startsWith(scopePath + "::"))) {
                            filtered.add(c);
                        }
                    }
                    candidates = filtered;
                }

                allCandidates.add(candidates);
                totalCandidates += candidates.size();
            } catch (Throwable t) {
                allCandidates.add(Collections.<ElementRecord>emptyList());
                allResults.add(DefaultRuleResult.skipped(spec.id(), "N/A",
                        "Rule could not select candidates: " + t.getMessage()));
            }
        }

        // Phase 2: evaluate using pre-collected candidate lists
        int processed = 0;
        reporter.onStepStarted(LoadingStep.EVALUATING_RULES);

        for (int i = 0; i < enabledSpecs.size(); i++) {
            RuleSpec spec = enabledSpecs.get(i);
            List<ElementRecord> candidates = allCandidates.get(i);

            if (candidates.isEmpty()) continue;

            try {
                Rule rule = RuleFactory.createRule(spec);

                for (ElementRecord candidate : candidates) {
                    try {
                        if (rule.appliesTo(candidate, context)) {
                            allResults.add(rule.evaluate(candidate, context));
                        }
                    } catch (Throwable t) {
                        allResults.add(DefaultRuleResult.skipped(spec.id(),
                                candidate.guid(), "Unexpected error: " + t.getMessage()));
                    }

                    processed++;
                    if (totalCandidates > 0 && (processed % 10 == 0 || processed == totalCandidates)) {
                        reporter.onProgress(processed, totalCandidates);
                    }
                }

            } catch (Throwable t) {
                allResults.add(DefaultRuleResult.skipped(spec.id(), "N/A",
                        "Rule could not be created: " + t.getMessage()));
            }
        }

        reporter.onStepCompleted(LoadingStep.EVALUATING_RULES);
        reporter.onDone();

        return Collections.unmodifiableList(allResults);
    }

    public EvaluationSummary evaluateWithSummary() {
        return new EvaluationSummary(evaluateAll());
    }

    // ── EvaluationSummary — unchanged ─────────────────────────────────────────

    public static final class EvaluationSummary {
        private final List<RuleResult> allResults;
        private final List<RuleResult> passed;
        private final List<RuleResult> failed;
        private final List<RuleResult> skipped;

        public EvaluationSummary(List<RuleResult> allResults) {
            this.allResults = Collections.unmodifiableList(new ArrayList<>(allResults));
            List<RuleResult> p = new ArrayList<>(), f = new ArrayList<>(), s = new ArrayList<>();
            for (RuleResult r : allResults) {
                switch (r.status()) {
                    case PASS:    p.add(r); break;
                    case FAIL:    f.add(r); break;
                    case SKIPPED: s.add(r); break;
                }
            }
            this.passed  = Collections.unmodifiableList(p);
            this.failed  = Collections.unmodifiableList(f);
            this.skipped = Collections.unmodifiableList(s);
        }

        public List<RuleResult> allResults() { return allResults; }
        public List<RuleResult> passed()     { return passed; }
        public List<RuleResult> failed()     { return failed; }
        public List<RuleResult> skipped()    { return skipped; }
        public int totalCount()  { return allResults.size(); }
        public int passCount()   { return passed.size(); }
        public int failCount()   { return failed.size(); }
        public int skipCount()   { return skipped.size(); }

        @Override
        public String toString() {
            return "EvaluationSummary{total=" + totalCount()
                    + ", pass=" + passCount()
                    + ", fail=" + failCount()
                    + ", skipped=" + skipCount() + "}";
        }
    }
}