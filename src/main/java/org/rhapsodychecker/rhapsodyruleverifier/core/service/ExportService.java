// File: src/main/java/org/rhapsodychecker/rhapsodyruleverifier/core/service/ExportService.java
package org.rhapsodychecker.rhapsodyruleverifier.core.service;

import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.export.ExcelReportExporter;

import java.util.List;

/**
 * Business logic for exporting evaluation results — no Swing dependency.
 */
public final class ExportService {

    private ExportService() {}

    /**
     * Export failure results to an Excel file.
     */
    public static void exportToExcel(List<RuleResult> results,
                                      ElementIndex index,
                                      String path) throws Exception {
        ExcelReportExporter.exportFailures(results, index, path);
    }
}