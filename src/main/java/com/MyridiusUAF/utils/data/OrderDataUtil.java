package com.AutoPOC.utils.data;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.reporting.LogUtil;
import com.AutoPOC.utils.excel.ExcelColumnIndex;
import com.AutoPOC.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Utility class to log Order ID and Order Date into Excel for each execution.
 * Fields are recorded under 'Transactional_Data_Sheet_Name'.
 */
public class OrderDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Transactional_Data_Sheet_Name");

    // Column indexes (zero-based)
    private static final int ORDER_ID_COL = 9;   // Column J
    private static final int ORDER_DATE_COL = 10; // Column K

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

            Sheet sheet = wb.getSheet(SHEET_NAME);
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);

            CellStyle style = createBorderStyle(wb);
            ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_ID, orderNum, style);
            ExcelUtil.setCellValue(row, ExcelColumnIndex.ORDER_DATE, orderDate, style);

            try (FileOutputStream out = new FileOutputStream(FILE_PATH)) {
                wb.write(out);
            }

            //LogUtil.log(OrderDataUtil.class, String.format("Order data written to row %d: [ID=%s, Date=%s]", rowIndex + 1, orderNum, orderDate));
            //ExtentReportManager.logInfoSimple("Order data written to Excel: ID=" + orderNum + ", Date=" + orderDate);


        } catch (IOException e) {
            LogUtil.error(OrderDataUtil.class, "Failed to write order data", e);
        }
    }

    /**
     * Creates a reusable cell style with borders for formatting.
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
