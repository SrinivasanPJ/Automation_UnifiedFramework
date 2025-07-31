package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.testng.ITestResult;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class responsible for recording automation test execution metadata into an Excel file.
 * <p>
 * Captures details like Run ID, execution timestamp, status (Pass/Fail/Skipped), and failure reasons.
 * <b>Enterprise best practices:</b>
 * <ul>
 *   <li>Non-instantiable static utility pattern</li>
 *   <li>Detailed JavaDoc and inline comments</li>
 *   <li>Strong config-driven design</li>
 *   <li>Clear Excel column mapping</li>
 *   <li>Defensive coding with logging</li>
 * </ul>
 */
public final class ExecutionDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Transactional_Data_Sheet_Name");

    /** Utility class; prevent instantiation. */
    private ExecutionDataUtil() { }

    /**
     * Writes execution data for the current test result into the Excel results sheet.
     * Handles both system and E2E test packages, recording run metadata and any failure reason.
     *
     * @param rowIndex Excel row index where data should be written
     * @param result   TestNG test result
     */
    public static void writeExecutionData(int rowIndex, ITestResult result) {
        String execDate = getCurrentDate("MM/dd/yyyy");
        String execTime = getCurrentDate("HH:mm:ss");
        String status = getStatus(result);

        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            String sheetToUse;

            if (TestTypeUtil.isSystemTestClass(result.getTestClass().getRealClass().getName())) {
                sheetToUse = SHEET_NAME;
            } else if (TestTypeUtil.isFromE2ETestPackage()) {
                sheetToUse = ConfigReader.getProperty("End_To_End_Sheet_Name");
            } else {
                LogUtil.log(ExecutionDataUtil.class, "Skipping execution data write: Test is not from a supported package.");
                return;
            }

            Sheet sheet = workbook.getSheet(sheetToUse);
            if (sheet == null) {
                LogUtil.warn(ExecutionDataUtil.class, "Sheet not found: " + sheetToUse + ". Skipping execution data write.");
                return;
            }

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            // Get next run ID (simple increment by max found)
            int maxRunId = ExcelUtil.getMaxRunId(sheet, E2EBindingColumnIndex.RUN_ID, 2);
            String runId = "R" + (maxRunId + 1);

            CellStyle style = createBorderStyle(workbook);

            // Write execution details (column index depends on test type)
            if (ConfigReader.getProperty("End_To_End_Sheet_Name").equals(sheetToUse)) {
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.RUN_ID, runId, style);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_DATE, execDate, style);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_TIME, execTime, style);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_STATUS, status, style);
            } else {
                ExcelUtil.setCellValue(row, ExcelColumnIndex.RUN_ID, runId, style);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_DATE, execDate, style);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_TIME, execTime, style);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_STATUS, status, style);
            }

            // If failed, log first-line failure reason (for system tests only)
            if ("Fail".equalsIgnoreCase(status)
                    && ConfigReader.getProperty("Transactional_Data_Sheet_Name").equals(sheetToUse)) {
                writeFailureReason(sheet, row, result, workbook);
            }

            // Save to disk (flush)
            try (FileOutputStream fos = new FileOutputStream(FILE_PATH)) {
                workbook.write(fos);
            }

            ExtentReportManager.INSTANCE.logInfoSimple(
                    "Execution Data Updated -> RunID: " + runId + ", Status: " + status
            );

        } catch (IOException e) {
            LogUtil.error(ExecutionDataUtil.class, "Failed to write execution data to Excel.", e);
        }
    }

    /**
     * Writes the failure reason (first line of exception) into Excel if a test fails.
     *
     * @param sheet   The Excel sheet
     * @param row     The row to update
     * @param result  TestNG test result
     * @param workbook The current workbook instance
     */
    private static void writeFailureReason(Sheet sheet, Row row, ITestResult result, Workbook workbook) {
        Throwable throwable = result.getThrowable();
        String message = throwable != null && throwable.getMessage() != null
                ? throwable.getMessage().split("\\r?\\n")[0]
                : "No exception message";

        String truncatedMessage = message.length() > 100 ? message.substring(0, 100) + "..." : message;

        CellStyle wrapStyle = createBorderStyle(workbook);
        wrapStyle.setWrapText(true);

        sheet.setColumnWidth(ExcelColumnIndex.FAILURE_REASON, 50 * 256); // Make failure reason column wider

        ExcelUtil.setCellValue(row, ExcelColumnIndex.FAILURE_REASON, truncatedMessage, wrapStyle);
    }

    /**
     * Maps TestNG test result status to human-readable status.
     */
    private static String getStatus(ITestResult result) {
        return switch (result.getStatus()) {
            case ITestResult.SUCCESS -> "Pass";
            case ITestResult.FAILURE -> "Fail";
            case ITestResult.SKIP    -> "Skipped";
            default                  -> "Unknown";
        };
    }

    /**
     * Returns the current date/time in the specified format.
     */
    private static String getCurrentDate(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Creates a standard bordered cell style for Excel.
     */
    private static CellStyle createBorderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
