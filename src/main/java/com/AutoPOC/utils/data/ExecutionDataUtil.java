package com.AutoPOC.utils.data;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.excel.ExcelColumnIndex;
import com.AutoPOC.utils.excel.ExcelUtil;
import com.AutoPOC.utils.reporting.ExtentReportManager;
import com.AutoPOC.utils.reporting.LogUtil;
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
 */
public class ExecutionDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Transactional_Data_Sheet_Name");

    /** -------------------------------------------------
     *  Public Methods
     *  ------------------------------------------------- */

    /**
     * Writes execution metadata for a test case into the transactional Excel sheet.
     *
     * @param rowIndex The row number where data should be written
     * @param result   TestNG ITestResult containing the execution outcome
     */
    public static void writeExecutionData(int rowIndex, ITestResult result) {
        String execDate = getCurrentDate("MM/dd/yyyy");
        String execTime = getCurrentDate("HH:mm:ss");
        String status = getStatus(result);

        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(SHEET_NAME);
            if (sheet == null) {
                LogUtil.warn(ExecutionDataUtil.class, "Transactional sheet not found, skipping execution data write.");
                return;
            }

            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            String runId = "R" + (countExistingRunIds(sheet) + 1);
            CellStyle style = createBorderStyle(workbook);

            // Populate basic execution metadata
            ExcelUtil.setCellValue(row, ExcelColumnIndex.RUN_ID, runId, style);
            ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_DATE, execDate, style);
            ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_TIME, execTime, style);
            ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_STATUS, status, style);

            // Populate failure reason if applicable
            if ("Fail".equalsIgnoreCase(status)) {
                writeFailureReason(sheet, row, result, workbook);
            }

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

    /** -------------------------------------------------
     *  Private Helper Methods
     *  ------------------------------------------------- */

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
     * Counts the number of existing Run IDs (non-empty) in the sheet starting from row 2.
     *
     * @param sheet The sheet to inspect
     * @return Count of Run IDs
     */
    private static int countExistingRunIds(Sheet sheet) {
        int count = 0;
        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row != null) {
                Cell cell = row.getCell(ExcelColumnIndex.RUN_ID, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null && !cell.toString().trim().isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Maps TestNG test result status to human-readable status.
     *
     * @param result TestNG ITestResult
     * @return "Pass", "Fail", "Skipped", or "Unknown"
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
     *
     * @param pattern DateTimeFormatter pattern
     * @return Formatted current date/time
     */
    private static String getCurrentDate(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Creates a standard bordered cell style for Excel.
     *
     * @param workbook Workbook instance
     * @return Bordered CellStyle
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
