package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
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

    private OrderDataUtil() { /* utility class */ }

    /**
     * Writes the given order number and date to the provided row index on the active sheet.
     * <p>Row creation is performed if it does not exist. Cell values are written with a thin
     * border style, consistent with other writers.</p>
     *
     * @param orderNum order identifier (e.g., "12345")
     * @param orderDate date string in the expected external format (e.g., "MM/dd/yyyy")
     * @param rowIndex zero-based row index to write to (caller decides which row)
     */
    public static void writeOrderData(String orderNum, String orderDate, int rowIndex) {
        // Resolve target sheet based on package/type, preserving existing behavior.
        final boolean isSystem = TestTypeUtil.isFromSystemTestPackage();
        final boolean isE2E    = TestTypeUtil.isFromE2ETestPackage();
        final String  sheetName = isSystem ? TXN_SHEET : (isE2E ? E2E_SHEET : null);

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

            if (isE2E) {
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_ID,   orderNum,  bordered);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_DATE, orderDate, bordered);
            } else {
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_ID,   orderNum,  bordered);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_DATE, orderDate, bordered);
            }

            try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
                wb.write(out);
            }
        } catch (IOException e) {
            LogUtil.error(OrderDataUtil.class, "Failed to write order data", e);
        }
    }

    /** Creates a thin-bordered cell style (kept consistent with other writers). */
    private static CellStyle createBorderStyle(Workbook wb) {
        final CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
