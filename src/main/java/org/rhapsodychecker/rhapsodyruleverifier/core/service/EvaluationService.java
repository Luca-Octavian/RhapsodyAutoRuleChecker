// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/EvaluationService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyAliasResolver;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyEvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.adapter.rhapsody.RhapsodyModelSnapshot;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.List;

/**
 * Business logic for config loading and rule evaluation — no Swing dependency.
 */
public final class EvaluationService {

    private EvaluationService() {}

    /**
     * Immutable result of a rule evaluation run.
     */
    public static final class EvalResult {
        private final RuleCheckerConfig config;
        private final List<RuleResult> results;
        private final String statusMessage;

        public EvalResult(RuleCheckerConfig config, List<RuleResult> results,
                          String statusMessage) {
            this.config = config;
            this.results = results;
            this.statusMessage = statusMessage;
        }

        public RuleCheckerConfig config() { return config; }
        public List<RuleResult> results() { return results; }
        public String statusMessage() { return statusMessage; }
    }

    /**
     * Load config and evaluate all rules against the model.
     */
    public static EvalResult evaluate(String configPath,
                                       RhapsodyModelSnapshot snapshot,
                                       ElementIndex index,
                                       String scopePath,
                                       boolean fromCache,
                                       ProgressReporter reporter) throws Exception {
        reporter.onStepStarted(LoadingStep.LOADING_CONFIG);
        RuleCheckerConfig config = ConfigLoader.load(Paths.get(configPath));
        reporter.onStepCompleted(LoadingStep.LOADING_CONFIG);

        reporter.onStepStarted(LoadingStep.SELECTING_ELEMENTS);
        RhapsodyAliasResolver aliasResolver = new RhapsodyAliasResolver(config, snapshot);
        ElementSelector selector = new ElementSelector(index, config);
        RhapsodyEvaluationContext context = new RhapsodyEvaluationContext(
                aliasResolver, snapshot, config, index, selector);
        reporter.onStepCompleted(LoadingStep.SELECTING_ELEMENTS);

        RuleEngine engine = new RuleEngine(config, selector, context, scopePath, reporter);
        List<RuleResult> results = engine.evaluateWithSummary().allResults();

        long failCount = 0;
        for (RuleResult r : results) {
            if (r.status() == RuleStatus.FAIL) failCount++;
        }

        String source = fromCache ? " (from cache)" : "";
        String status = "Evaluation complete" + source + ": " + failCount + " failures"
                + (scopePath.isEmpty() ? "" : " (scope: " + scopePath + ")");

        AppLogger.logEvaluation(config.enabledRules().size(), results.size(),
                (int) (results.size() - failCount), (int) failCount, 0, 0);

        return new EvalResult(config, results, status);
    }
}