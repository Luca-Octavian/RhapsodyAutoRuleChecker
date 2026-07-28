// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/EvaluationService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import org.rhapsodychecker.rhapsodyruleverifier.core.AppLogger;
import org.rhapsodychecker.rhapsodyruleverifier.config.ConfigLoader;
import org.rhapsodychecker.rhapsodyruleverifier.config.RuleCheckerConfig;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.LoadingStep;
import org.rhapsodychecker.rhapsodyruleverifier.core.progress.ProgressReporter;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleEngine;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.selector.ElementSelector;

import java.nio.file.Paths;
import java.util.List;

/**
 * Business logic for config loading and rule evaluation — no Swing dependency,
 * no adapter dependency. Receives pre-built core abstractions (EvaluationContext,
 * ElementSelector) from the caller (composition root / controller).
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
     * Functional interface for building an EvaluationContext from a loaded config.
     * The caller (controller) provides the adapter-specific wiring through this factory,
     * keeping EvaluationService free of adapter imports.
     */
    public interface ContextFactory {
        /**
         * Build an EvaluationContext and ElementSelector for the given config and index.
         * Implementations will typically create adapter-specific objects
         * (e.g., RhapsodyAliasResolver, RhapsodyEvaluationContext).
         */
        ContextPair create(RuleCheckerConfig config, ElementIndex index);
    }

    /**
     * Pairs an EvaluationContext with its ElementSelector — both are needed by the RuleEngine.
     */
    public static final class ContextPair {
        private final EvaluationContext context;
        private final ElementSelector selector;

        public ContextPair(EvaluationContext context, ElementSelector selector) {
            this.context = context;
            this.selector = selector;
        }

        public EvaluationContext context() { return context; }
        public ElementSelector selector() { return selector; }
    }

    /**
     * Load config and evaluate all rules against the model.
     *
     * @param configPath     path to the YAML config file
     * @param contextFactory adapter-specific factory (provided by the controller)
     * @param index          pre-built element index
     * @param scopePath      optional package scope filter (empty string = all)
     * @param fromCache      whether the model was loaded from cache (for status message)
     * @param reporter       progress reporter
     */
    public static EvalResult evaluate(String configPath,
                                       ContextFactory contextFactory,
                                       ElementIndex index,
                                       String scopePath,
                                       boolean fromCache,
                                       ProgressReporter reporter) throws Exception {
        reporter.onStepStarted(LoadingStep.LOADING_CONFIG);
        RuleCheckerConfig config = ConfigLoader.load(Paths.get(configPath));
        reporter.onStepCompleted(LoadingStep.LOADING_CONFIG);

        reporter.onStepStarted(LoadingStep.SELECTING_ELEMENTS);
        ContextPair pair = contextFactory.create(config, index);
        reporter.onStepCompleted(LoadingStep.SELECTING_ELEMENTS);

        RuleEngine engine = new RuleEngine(config, pair.selector(), pair.context(), scopePath, reporter);
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