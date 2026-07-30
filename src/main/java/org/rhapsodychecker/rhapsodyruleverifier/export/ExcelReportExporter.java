package org.rhapsodychecker.rhapsodyruleverifier.export;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.rhapsodychecker.rhapsodyruleverifier.core.index.ElementIndex;
import org.rhapsodychecker.rhapsodyruleverifier.core.model.ElementRecord;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleResult;
import org.rhapsodychecker.rhapsodyruleverifier.core.rule.RuleStatus;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Exports rule check failures to an Excel (.xlsx) report.
 * Only FAIL results are written; one row per (rule, element) failure.
 */
public final class ExcelReportExporter {

    private ExcelReportExporter() {}

    private static final String[] HEADERS = {
            "Rule", "Arch Element ID", "Arch Element Name", "Path", "Reason"
    };

    /**
     * Writes only the FAIL results from the given list to an .xlsx file at outputPath.
     * Element name/path are resolved via the given ElementIndex, keyed by RuleResult.elementGuid().
     */
    public static void exportFailures(List<RuleResult> results, ElementIndex index, String outputPath) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Failures");

            CellStyle headerStyle = buildHeaderStyle(workbook);
            CellStyle failStyle = buildFailRowStyle(workbook);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < HEADERS.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows: only FAIL
            int rowIndex = 1;
            for (RuleResult r : results) {
                if (r.status() != RuleStatus.FAIL) continue;

                String guid = r.elementGuid();
                Optional<ElementRecord> record = index.repository().get(guid);
                String name = record.map(ElementRecord::name).orElse("<unknown>");
                String path = record.flatMap(ElementRecord::ownerPath).orElse("<root>");

                Row row = sheet.createRow(rowIndex++);

                Cell ruleCell = row.createCell(0);
                ruleCell.setCellValue(r.ruleId());
                ruleCell.setCellStyle(failStyle);

                Cell idCell = row.createCell(1);
                idCell.setCellValue(guid);
                idCell.setCellStyle(failStyle);

                Cell nameCell = row.createCell(2);
                nameCell.setCellValue(name);
                nameCell.setCellStyle(failStyle);

                Cell pathCell = row.createCell(3);
                pathCell.setCellValue(path);
                pathCell.setCellStyle(failStyle);

                Cell reasonCell = row.createCell(4);
                reasonCell.setCellValue(r.message());
                reasonCell.setCellStyle(failStyle);
            }

            // Auto-size columns for readability
            for (int col = 0; col < HEADERS.length; col++) {
                sheet.autoSizeColumn(col);
            }

            // Freeze header row so it stays visible when scrolling
            sheet.createFreezePane(0, 1);

            try (FileOutputStream out = new FileOutputStream(outputPath)) {
                workbook.write(out);
            }
        }
    }

    private static CellStyle buildHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        style.setFont(boldFont);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle buildFailRowStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setWrapText(true);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        return style;
    }
}