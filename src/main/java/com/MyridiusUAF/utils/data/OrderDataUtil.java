package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to log Order ID and Order Date into Excel for each execution.
 * <p>
 * Writes data to Transactional or End-to-End sheets, supporting both system and E2E test flows.
 * <p>
 * <b>Enterprise Best Practices:</b>
 * <ul>
 *     <li>Non-instantiable static utility</li>
 *     <li>Clean, defensive file handling</li>
 *     <li>Full JavaDoc and inline comments</li>
 *     <li>Single responsibility, strong typing, config-driven</li>
 * </ul>
 */
public final class OrderDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");

    // Prevent instantiation
    private OrderDataUtil() {}

    /**
     * Writes the given order number and order date into specified row in Excel.
     *
     * @param orderNum  Order ID string
     * @param orderDate Order Date string (formatted)
     * @param rowIndex  Target row index (zero-based)
     */
    public static void writeOrderData(String orderNum, String orderDate, int rowIndex) {
        try (FileInputStream fis = new FileInputStream(FILE_PATH);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet;
            // Select correct sheet depending on the current test package
            if (TestTypeUtil.isFromSystemTestPackage()) {
                sheet = wb.getSheet(ConfigReader.getProperty("Transactional_Data_Sheet_Name"));
            } else if (TestTypeUtil.isFromE2ETestPackage()) {
                sheet = wb.getSheet(ConfigReader.getProperty("End_To_End_Sheet_Name"));
            } else {
                LogUtil.log(OrderDataUtil.class, "Skipping order data write: Test is not from a supported package.");
                return;
            }

            if (sheet == null) {
                LogUtil.warn(OrderDataUtil.class, "Sheet not found, cannot write order data.");
                return;
            }

            // Prepare row
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            CellStyle style = createBorderStyle(wb);

            // Write data to right columns for System or E2E sheet
            if (TestTypeUtil.isFromE2ETestPackage()) {
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_ID, orderNum, style);
                ExcelUtil.setCellValue(row, E2EBindingColumnIndex.ORDER_DATE, orderDate, style);
            } else {
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_ID, orderNum, style);
                ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_DATE, orderDate, style);
            }

            // Write changes to disk
            try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
                wb.write(out);
            }

        } catch (IOException e) {
            LogUtil.error(OrderDataUtil.class, "Failed to write order data", e);
        }
    }

    /**
     * Creates a reusable cell style with borders for formatting Excel cells.
     *
     * @param wb Workbook context
     * @return new bordered CellStyle
     */
    private static CellStyle createBorderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
