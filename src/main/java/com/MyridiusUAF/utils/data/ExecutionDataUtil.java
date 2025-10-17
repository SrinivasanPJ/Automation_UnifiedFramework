package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.TransactionalDao;
import java.sql.SQLException;
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
 * Writes execution metadata to Excel for both Transactional (System) and E2E sheets:
 * <ul>
 *   <li>Appends a row at the next available position (never overwrites).</li>
 *   <li>Generates the next Run ID in its series (System: {@code R#}, E2E: {@code E2E#}).</li>
 *   <li>Writes Exec Date/Time/Status; writes Failure Reason on failure.</li>
 *   <li>For System tests only, triggers an E2E Binding sync after writing.</li>
 * </ul>
 *
 * <p><b>Behavioral notes (unchanged):</b>
 * <br/>• System tests write to {@code Transactional_Data} starting from row index 2.<br/>
 * • E2E tests write to {@code E2E Binding} starting from row index 1.<br/>
 * • Run IDs are strictly monotonic within each sheet/series.</p>
 */
public final class ExecutionDataUtil {

    // ---- Configuration constants ------------------------------------------------

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String TXN_SHEET = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
    private static final String E2E_SHEET = ConfigReader.getProperty("End_To_End_Sheet_Name");

    private static final int START_ROW_SYSTEM = 2; // Transactional data after header
    private static final int START_ROW_E2E = 1; // E2E Binding data after header

    private static final String DATE_FMT = "MM/dd/yyyy";
    private static final String TIME_FMT = "HH:mm:ss";

    private ExecutionDataUtil() { /* utility class */ }

    // ---- Public API -------------------------------------------------------------

    public static void writeExecutionData(@SuppressWarnings("unused") int rowIndex, ITestResult result) {
        final String execDate = nowAs(DATE_FMT);
        final String execTime = nowAs(TIME_FMT);
        final String status   = statusLabel(result);

        final boolean isSystem = TestTypeUtil.isSystem(result);
        final boolean isE2E    = TestTypeUtil.isE2E(result);

        // =========================
        // DB MODE (early return)
        // =========================
        if (DataMode.isDb()) {
            // Derive/collect fields (kept simple & safe defaults)
            String application  = result.getTestContext().getAttribute("Application") != null
                    ? result.getTestContext().getAttribute("Application").toString()
                    : "demowebshop";

            String testCaseName = result.getName();     // or pull from your own context
            String func         = "Order Creation";     // keep parity with Excel naming if you prefer
            // String scenario     = testCaseName;

            TransactionalDao dao = new TransactionalDao();
            try {
                // Generate next run id (R# for System, E2E# for E2E)
                String runId = dao.nextRunId(isE2E);

                // Keep it in context so OrderDataUtil can update by runId later
                result.getTestContext().setAttribute("CURRENT_RUN_ID", runId);

                dao.insertExecutionRow(
                        application,
                        isE2E ? "E2E" : "System",
                        func,
                        /* scenario */ testCaseName,
                        testCaseName,
                        runId,
                        status,
                        "Fail".equalsIgnoreCase(status) && result.getThrowable() != null
                                ? String.valueOf(result.getThrowable())
                                : null
                );

                ExtentReportManager.INSTANCE.logInfoSimple(
                        "DB Execution Row -> RunID: " + runId + ", Status: " + status
                                + ", Date: " + execDate + ", Time: " + execTime
                );

            } catch (SQLException e) {
                throw new RuntimeException("DB writeExecutionData failed", e);
            }

            // Mirror System → E2E binding (same behavior as Excel path)
            try {
                if (isSystem) {
                    E2EBindingSyncUtil.appendLatestTransactionalOrderToE2EBinding();
                }
            } catch (Exception syncEx) {
                LogUtil.warn(ExecutionDataUtil.class, "E2E Binding sync skipped: " + syncEx.getMessage());
            }
            return; // IMPORTANT: skip Excel path in DB mode
        }

        // =========================
        // EXCEL MODE (existing code)
        // =========================
        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook workbook = new XSSFWorkbook(fis)) {

            final String sheetName = isSystem ? TXN_SHEET : (isE2E ? E2E_SHEET : null);
            if (sheetName == null) {
                LogUtil.log(ExecutionDataUtil.class, "Skipping execution data write: unsupported package.");
                return;
            }

            final Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.warn(ExecutionDataUtil.class, "Sheet not found: " + sheetName + ". Skipping.");
                return;
            }

            final int startRow   = isE2E ? START_ROW_E2E : START_ROW_SYSTEM;
            final int runIdCol   = isE2E ? E2EBindingColumnIndex.RUN_ID : ExcelColumnIndex.RUN_ID;
            final String prefix  = isE2E ? "E2E" : "R";

            final int appendRowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRow);
            Row row = sheet.getRow(appendRowIndex);
            if (row == null) row = sheet.createRow(appendRowIndex);

            final int maxRunId = ExcelUtil.getMaxRunIdWithPrefix(sheet, runIdCol, startRow, prefix);
            final String runId = prefix + (maxRunId + 1);

            final CellStyle bordered = createBorderStyle(workbook);

            if (isE2E) {
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.RUN_ID, runId, bordered);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_DATE, execDate, bordered);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_TIME, execTime, bordered);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.EXEC_STATUS, status, bordered);

                if ("Fail".equalsIgnoreCase(status)) {
                    final String reason = firstLineOf(result);
                    final CellStyle wrap = createBorderStyle(workbook);
                    wrap.setWrapText(true);
                    sheet.setColumnWidth(E2EBindingColumnIndex.FAILURE_REASON, 50 * 256);
                    ExcelUtil.setCellValue(row, E2EBindingColumnIndex.FAILURE_REASON, reason, wrap);
                }
            } else {
                ExcelUtil.setCellValue(row, ExcelColumnIndex.RUN_ID, runId, bordered);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_DATE, execDate, bordered);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_TIME, execTime, bordered);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.EXEC_STATUS, status, bordered);

                if ("Fail".equalsIgnoreCase(status)) {
                    writeFailureReason(sheet, row, result, workbook);
                }
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

        // Keep System → E2E sync (no-op for E2E tests)
        try {
            if (isSystem) {
                E2EBindingSyncUtil.appendLatestTransactionalOrderToE2EBinding();
            }
        } catch (Exception syncEx) {
            LogUtil.warn(ExecutionDataUtil.class, "E2E Binding sync skipped: " + syncEx.getMessage());
        }
    }

    // ---- Private helpers --------------------------------------------------------

    /**
     * Returns the first line of the throwable message (truncated at 100 chars).
     */
    private static String firstLineOf(ITestResult result) {
        final Throwable t = result.getThrowable();
        final String msg = (t != null && t.getMessage() != null) ? t.getMessage() : "No exception message";
        final String first = msg.split("\\r?\\n")[0];
        return (first.length() > 100) ? first.substring(0, 100) + "..." : first;
    }

    /**
     * Maps TestNG status to a label used in Excel.
     */
    private static String statusLabel(ITestResult result) {
        return switch (result.getStatus()) {
            case ITestResult.SUCCESS -> "Pass";
            case ITestResult.FAILURE -> "Fail";
            case ITestResult.SKIP -> "Skipped";
            default -> "Unknown";
        };
    }

    /**
     * Formats current time with the given pattern.
     */
    private static String nowAs(String pattern) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    private static void writeFailureReason(Sheet sheet, Row row, ITestResult result, Workbook workbook) {
        final Throwable t = result.getThrowable();
        final String message = (t != null && t.getMessage() != null)
                ? t.getMessage().split("\\r?\\n")[0]
                : "No exception message";

        final String truncated = message.length() > 100 ? message.substring(0, 100) + "..." : message;

        final CellStyle wrapStyle = createBorderStyle(workbook);
        wrapStyle.setWrapText(true);

        sheet.setColumnWidth(ExcelColumnIndex.FAILURE_REASON, 50 * 256);
        ExcelUtil.setCellValue(row, ExcelColumnIndex.FAILURE_REASON, truncated, wrapStyle);
    }

    /**
     * Thin-borders cell style used across writers.
     */
    private static CellStyle createBorderStyle(Workbook workbook) {
        final CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
