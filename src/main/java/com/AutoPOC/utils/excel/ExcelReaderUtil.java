package com.AutoPOC.utils.excel;

import com.AutoPOC.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelReaderUtil {

    /**
     * Fetches a row as key-value pairs where keys are headers.
     * Uses header row at index 1 by default.
     */
    public static Map<String, String> getRowByKey(String filePath, String sheetName, int keyColumnIndex, String key) {
        return getRowByKey(filePath, sheetName, keyColumnIndex, key, 1);
    }

    public static Map<String, String> getRowByKey(String filePath, String sheetName, int keyColumnIndex, String key, int headerRowIndex) {
        Map<String, String> rowData = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) throw new IllegalArgumentException("Sheet not found: " + sheetName);

            Row header = sheet.getRow(headerRowIndex);
            if (header == null) throw new IllegalArgumentException("Header row missing at index " + headerRowIndex);

            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String cellVal = getCellValue(row.getCell(keyColumnIndex)).trim();
                if (cellVal.equalsIgnoreCase(key.trim())) {
                    for (int c = 0; c < header.getLastCellNum(); c++) {
                        rowData.put(getCellValue(header.getCell(c)).trim(),
                                getCellValue(row.getCell(c)).trim());
                    }
                    break;
                }
            }

            if (rowData.isEmpty()) {
                throw new RuntimeException("No row found for key: " + key +
                        " in sheet: " + sheetName + " (col " + keyColumnIndex + ")");
            }

        } catch (IOException e) {
            throw new RuntimeException("Error reading Excel file: " + filePath, e);
        }

        return rowData;
    }

    public static List<Map<String, String>> getAllRows(String filePath, String sheetName) {
        List<Map<String, String>> all = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.log(ExcelReaderUtil.class, "Sheet '" + sheetName + "' not found in " + filePath);
                return all;
            }

            Row header = sheet.getRow(0);
            if (header == null) {
                LogUtil.log(ExcelReaderUtil.class, "Header row missing in sheet: " + sheetName);
                return all;
            }

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    rowMap.put(getCellValue(header.getCell(c)).trim(),
                            getCellValue(row.getCell(c)).trim());
                }
                all.add(rowMap);
            }

        } catch (Exception e) {
            LogUtil.log(ExcelReaderUtil.class, "Error reading rows from " + sheetName + ": " + e.getMessage());
        }

        return all;
    }

    public static int findNextAvailableRow(Sheet sheet) {
        final int RUN_ID_COL_INDEX = 5;

        for (int i = 2; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell runIdCell = row.getCell(RUN_ID_COL_INDEX, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (runIdCell == null || runIdCell.toString().trim().isEmpty()) {
                LogUtil.log(ExcelReaderUtil.class, "Next available row found at index " + i);
                return i;
            }
        }

        int nextRow = sheet.getLastRowNum() + 1;
        LogUtil.log(ExcelReaderUtil.class, "All Run ID rows filled. Appending new row at index " + nextRow);
        return nextRow;
    }

    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    double d = cell.getNumericCellValue();
                    long l = (long) d;
                    yield (d == l) ? String.valueOf(l) : String.valueOf(d);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }

    private static boolean rowIsEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !getCellValue(cell).trim().isEmpty()) return false;
        }
        return true;
    }

    public static Sheet getSheet(String filePath, String sheetName) {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            Workbook wb = WorkbookFactory.create(fis);
            return wb.getSheet(sheetName);
        } catch (IOException e) {
            throw new RuntimeException("Unable to get sheet: " + sheetName + " from file: " + filePath, e);
        }
    }
}
