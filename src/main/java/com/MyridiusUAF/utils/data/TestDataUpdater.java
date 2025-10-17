package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Utility for updating username/email and password for a given TestID in Excel,
 * along with register date/time. Follows enterprise-level standards for reliability
 * and reuse across frameworks.
 */
public class TestDataUpdater {

    public static void updateUsernameAndPassword(String testID, String newUsername, String newPassword) {
        // If DB mode is ON, persist to DB and return
        if (com.MyridiusUAF.utils.db.DataMode.isDb()) {
            var nowDate = java.time.LocalDate.now();
            var nowTime = java.time.LocalTime.now();
            new com.MyridiusUAF.utils.db.dao.LoginDataDao()
                    .upsertCredentials(testID, newUsername, newPassword, nowDate, nowTime);

            com.MyridiusUAF.utils.reporting.LogUtil.info(
                    TestDataUpdater.class,
                    String.format("DB mode: upserted credentials for TestID=%s (Register %s %s)",
                            testID, nowDate, nowTime.withNano(0))
            );
            return; // keep Excel untouched in DB mode
        }

        // ---------- Legacy Excel path (unchanged) ----------
        String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        String sheetName = ConfigReader.getProperty("Login_Data_Sheet_Name");

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) throw new RuntimeException("Sheet not found: " + sheetName);

            Row headerRow = sheet.getRow(0);
            int testIdCol = ExcelUtil.getColumnIndex(headerRow, "TestID");
            int usernameCol = ExcelUtil.getColumnIndex(headerRow, "Username");
            int passwordCol = ExcelUtil.getColumnIndex(headerRow, "Password");
            int registerDateCol = ExcelUtil.getColumnIndex(headerRow, "Register Date");
            int registerTimeCol = ExcelUtil.getColumnIndex(headerRow, "Register Time");

            int targetRowIdx = -1;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String val = row.getCell(testIdCol, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)
                        .toString().trim();
                if (val.equalsIgnoreCase(testID.trim())) {
                    targetRowIdx = r;
                    break;
                }
            }
            if (targetRowIdx == -1) throw new RuntimeException("TestID not found: " + testID);

            Row row = sheet.getRow(targetRowIdx);
            CellStyle style = workbook.createCellStyle();

            ExcelUtil.setCellValue(row, usernameCol, newUsername, style);
            ExcelUtil.setCellValue(row, passwordCol, newPassword, style);

            var now = java.time.LocalDateTime.now();
            String dateStr = now.toLocalDate().toString();                     // yyyy-MM-dd
            String timeStr = now.toLocalTime().withNano(0).toString();         // HH:mm:ss

            ExcelUtil.setCellValue(row, registerDateCol, dateStr, style);
            ExcelUtil.setCellValue(row, registerTimeCol, timeStr, style);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

            LogUtil.info(TestDataUpdater.class, String.format(
                    "Excel mode: Updated creds & Register Date/Time for TestID=%s (Date=%s, Time=%s)",
                    testID, dateStr, timeStr
            ));

        } catch (Exception e) {
            LogUtil.error(TestDataUpdater.class, "Failed to update credentials: " + e.getMessage(), e);
            throw new RuntimeException("Failed to update credentials in Excel", e);
        }
    }
}

