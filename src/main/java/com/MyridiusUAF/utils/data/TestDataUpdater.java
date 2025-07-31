package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility for updating username/email and password for a given TestID in Excel,
 * along with register date/time. Follows enterprise-level standards for reliability
 * and reuse across frameworks.
 */
public class TestDataUpdater {

    /**
     * Updates the username/email and password for a given TestID in the Login Excel sheet.
     * Also updates register date and time to current values.
     *
     * @param testID      TestID identifying the row.
     * @param newUsername The new username/email.
     * @param newPassword The new password.
     * @throws RuntimeException if sheet or TestID is not found, or IO error occurs.
     */
    public static void updateUsernameAndPassword(String testID, String newUsername, String newPassword) {
        String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        String sheetName = ConfigReader.getProperty("Login_Data_Sheet_Name");

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) throw new RuntimeException("Sheet not found: " + sheetName);

            Row headerRow = sheet.getRow(0);
            int testIdCol       = ExcelUtil.getColumnIndex(headerRow, "TestID");
            int usernameCol     = ExcelUtil.getColumnIndex(headerRow, "Username");
            int passwordCol     = ExcelUtil.getColumnIndex(headerRow, "Password");
            int registerDateCol = ExcelUtil.getColumnIndex(headerRow, "Register Date");
            int registerTimeCol = ExcelUtil.getColumnIndex(headerRow, "Register Time");

            int targetRowIdx = -1;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String val = row.getCell(testIdCol, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
                if (val.equalsIgnoreCase(testID.trim())) {
                    targetRowIdx = r;
                    break;
                }
            }
            if (targetRowIdx == -1) throw new RuntimeException("TestID not found: " + testID);

            Row row = sheet.getRow(targetRowIdx);

            // Create style once for all cells
            CellStyle style = workbook.createCellStyle();

            // Update username/email and password
            ExcelUtil.setCellValue(row, usernameCol, newUsername, style);
            ExcelUtil.setCellValue(row, passwordCol, newPassword, style);

            // Update register date/time to current system values
            LocalDateTime now = LocalDateTime.now();
            String dateStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String timeStr = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));

            ExcelUtil.setCellValue(row, registerDateCol, dateStr, style);
            ExcelUtil.setCellValue(row, registerTimeCol, timeStr, style);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

            LogUtil.info(TestDataUpdater.class, String.format(
                    "Updated Username/Password and Register Date/Time for TestID: %s (Date: %s, Time: %s)",
                    testID, dateStr, timeStr
            ));

        } catch (Exception e) {
            LogUtil.error(TestDataUpdater.class, "Failed to update credentials: " + e.getMessage(), e);
            throw new RuntimeException("Failed to update credentials in Excel", e);
        }
    }
}
