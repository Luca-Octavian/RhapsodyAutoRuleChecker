package org.rhapsodychecker.rhapsodyruleverifier.fix;

import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.EvaluationContext;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.Rule;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;

import java.util.*;

/**
 * Post-evaluation pass: collects {@link FixAction}s from rules that failed
 * and support auto-fix via {@link Rule#suggestFix}.
 *
 * Produces a {@link FixPlan} ready for simulation.
 */
public final class FixCollector {

    /**
     * Collect fix suggestions from failed results.
     *
     * @param failedResults list of FAIL results from evaluation
     * @param ruleMap       map of ruleId -> configured Rule instance
     * @param index         element index for looking up records
     * @param context       evaluation context for suggestFix calls
     * @param modelGuid     model GUID for the fix plan
     * @param configPath    config path for the fix plan (nullable)
     * @return a FixPlan with one entry per suggested fix
     */
    public FixPlan collect(List<RuleResult> failedResults,
                           Map<String, Rule> ruleMap,
                           ElementIndex index,
                           EvaluationContext context,
                           String modelGuid,
                           String configPath) {

        FixPlan plan = new FixPlan(modelGuid, configPath);

        for (RuleResult result : failedResults) {
            if (result.status() != RuleStatus.FAIL) continue;

            Rule rule = ruleMap.get(result.ruleId());
            if (rule == null) continue;

            Optional<ElementRecord> opt = index.repository().get(result.elementGuid());
            if (!opt.isPresent()) continue;

            ElementRecord element = opt.get();
            Optional<FixAction> fix = rule.suggestFix(element, context);
            if (fix.isPresent()) {
                plan.addAction(fix.get());
            }
        }

        return plan;
    }
}