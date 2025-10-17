// com/MyridiusUAF/utils/data/OrderDataUtil.java
package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.TransactionalDao;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Writes Order ID and Order Date into the active results sheet:
 * <ul>
 *   <li>System tests → {@code Transactional_Data} sheet</li>
 *   <li>E2E tests → {@code E2E Binding} sheet</li>
 * </ul>
 * <p>
 * <b>Note:</b> E2E-Binding sync is <i>not</i> performed here; it runs after execution
 * data is written in {@link ExecutionDataUtil#writeExecutionData(int, org.testng.ITestResult)}.
 * </p>
 */
public final class OrderDataUtil {

    // ---- Config constants (resolved once) ---------------------------------------
    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String TXN_SHEET = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
    private static final String E2E_SHEET = ConfigReader.getProperty("End_To_End_Sheet_Name");

    // ---- Date parsing (tolerant) ------------------------------------------------
    // Accepted formats (add more if your site/localization changes)
    private static final DateTimeFormatter[] DATE_PATTERNS = new DateTimeFormatter[] {
            DateTimeFormatter.ISO_LOCAL_DATE,                           // 2025-10-14
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),       // 10/14/2025
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US),       // 14/10/2025
            DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.US),      // Oct 14, 2025
            DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)      // October 14, 2025
    };

    private OrderDataUtil() { /* utility class */ }

    /** Tolerant date parser; returns null if no pattern matches. */
    public static LocalDate parseOrderDate(String raw) {
        if (raw == null) return null;
        // normalize NBSP and trim
        String s = raw.replace('\u00A0', ' ').trim();
        for (DateTimeFormatter fmt : DATE_PATTERNS) {
            try {
                return LocalDate.parse(s, fmt);
            } catch (DateTimeParseException ignore) { /* try next */ }
        }
        LogUtil.warn(OrderDataUtil.class, "Order date parsing failed for '" + raw + "'; storing NULL in DB");
        return null;
    }

    /** Keep only digits from a UI label like "Order #2112962" → "2112962". */
    private static String normalizeOrderId(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D+", "");
        return digits.isEmpty() ? null : digits;
    }

    /**
     * Writes the given order number and date to the provided row index on the active sheet.
     * <p>Row creation is performed if it does not exist. Cell values are written with a thin
     * border style, consistent with other writers.</p>
     *
     * @param orderNum  order identifier (as shown by UI, e.g., "Order #2112962" or "2112962")
     * @param orderDate date string from UI (any of the accepted formats)
     * @param rowIndex  zero-based row index to write to (caller decides which row)
     */
    public static void writeOrderData(String orderNum, String orderDate, int rowIndex) {
        if (DataMode.isDb()) {
            // Support new and legacy keys
            Object ridObj = org.testng.Reporter.getCurrentTestResult()
                    .getTestContext().getAttribute("RunId");
            if (ridObj == null) {
                ridObj = org.testng.Reporter.getCurrentTestResult()
                        .getTestContext().getAttribute("CURRENT_RUN_ID");
            }
            final String runId = (ridObj == null ? null : String.valueOf(ridObj));

            if (runId == null || runId.isBlank()) {
                LogUtil.warn(OrderDataUtil.class, "No RunId/CURRENT_RUN_ID found; skipping DB order write.");
                return;
            }

            final String normalizedOrderId = normalizeOrderId(orderNum);
            final LocalDate parsedDate = parseOrderDate(orderDate);

            try {
                new TransactionalDao().updateOrderForRun(runId, normalizedOrderId, parsedDate);
                LogUtil.log(OrderDataUtil.class,
                        "DB order write OK (runId=" + runId + ", order_id=" + normalizedOrderId +
                                ", order_date=" + parsedDate + ")");
            } catch (Exception e) {
                LogUtil.error(OrderDataUtil.class, "DB order write failed", e);
            }
            return; // DB path stops here; Excel not touched
        }

        // ---- Excel path (unchanged behavior) -----------------------------------
        // Resolve target sheet based on package/type, preserving existing behavior.
        final boolean isSystem = TestTypeUtil.isFromSystemTestPackage();
        final boolean isE2E = TestTypeUtil.isFromE2ETestPackage();
        final String sheetName = isSystem ? TXN_SHEET : (isE2E ? E2E_SHEET : null);

        if (sheetName == null) {
            LogUtil.log(OrderDataUtil.class, "Skipping order data write: unsupported package.");
            return;
        }

        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook wb = new XSSFWorkbook(fis)) {

            final Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.warn(OrderDataUtil.class, "Sheet not found: " + sheetName + ". Cannot write order data.");
                return;
            }

            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);

            final CellStyle bordered = createBorderStyle(wb);

            // Keep original strings in Excel exactly as received (no normalization),
            // to avoid changing existing Excel workflows/formatting.
            if (isE2E) {
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_ID, orderNum, bordered);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_DATE, orderDate, bordered);
            } else {
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_ID, orderNum, bordered);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_DATE, orderDate, bordered);
            }

            try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
                wb.write(out);
            }
        } catch (IOException e) {
            LogUtil.error(OrderDataUtil.class, "Failed to write order data", e);
        }
    }

    /**
     * Creates a thin-bordered cell style (kept consistent with other writers).
     */
    private static CellStyle createBorderStyle(Workbook wb) {
        final CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
