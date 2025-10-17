package com.MyridiusUAF.utils.excel;

import com.MyridiusUAF.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Enterprise utility class for reading Excel sheets as maps and lists.
 * Provides helper methods for data-driven test frameworks using Apache POI.
 */
public class ExcelReaderUtil {

    // Formats cell values exactly as displayed in Excel (respects cell formatting)
    private static final DataFormatter DF = new DataFormatter();

    /**
     * Fetches a row as key-value pairs (header:value), searching for a row where the value
     * in keyColumnIndex equals key. Uses header row at index 1 by default.
     */
    public static Map<String, String> getRowByKey(String filePath, String sheetName, int keyColumnIndex, String key) {
        return getRowByKey(filePath, sheetName, keyColumnIndex, key, 1);
    }

    /**
     * Fetches a row as key-value pairs (header:value) using a custom header row index.
     *
     * @param filePath       Path to Excel file
     * @param sheetName      Name of the sheet
     * @param keyColumnIndex Index of the column to match (0-based)
     * @param key            Value to search for (compared as text)
     * @param headerRowIndex Row index containing headers (usually 0 or 1)
     */
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

    /** Reads all rows (header row @ index 0). */
    public static List<Map<String, String>> getAllRows(String filePath, String sheetName) {
        return getAllRows(filePath, sheetName, 0);
    }

    /**
     * Reads all rows as List<Map<header,value>> using the provided header row index.
     *
     * @param filePath        Path to Excel file
     * @param sheetName       Sheet name
     * @param headerRowIndex  Index of the header row (0-based)
     */
    public static List<Map<String, String>> getAllRows(String filePath, String sheetName, int headerRowIndex) {
        List<Map<String, String>> all = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {  // supports xls/xlsx

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.log(ExcelReaderUtil.class, "Sheet '" + sheetName + "' not found in " + filePath);
                return all;
            }

            Row header = sheet.getRow(headerRowIndex);
            if (header == null) {
                LogUtil.log(ExcelReaderUtil.class,
                        "Header row missing in sheet: " + sheetName + " at index " + headerRowIndex);
                return all;
            }

            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || rowIsEmpty(row)) continue;

                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int c = 0; c < header.getLastCellNum(); c++) {
                    String key = getCellValue(header.getCell(c)).trim();
                    String val = getCellValue(row.getCell(c)).trim();
                    rowMap.put(key, val);
                }
                all.add(rowMap);
            }
        } catch (Exception e) {
            LogUtil.log(ExcelReaderUtil.class, "Error reading rows from " + sheetName + ": " + e.getMessage());
        }
        return all;
    }

    /**
     * Finds the next available row index (first row after startRowIndex with empty RunID column).
     */
    public static int findNextAvailableRow(Sheet sheet, int runIdColIndex, int startRowIndex) {
        for (int i = startRowIndex; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell cell = row.getCell(runIdColIndex, MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || getCellValue(cell).trim().isEmpty()) return i;
        }
        return sheet.getLastRowNum() + 1;
    }

    /**
     * Returns the header cells (as text) from the given header row index.
     */
    public static List<String> getHeaders(String filePath, String sheetName, int headerRowIndex) {
        List<String> headers = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet sheet = wb.getSheet(sheetName);
            Row header = (sheet != null) ? sheet.getRow(headerRowIndex) : null;
            if (header == null) return headers;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                headers.add(getCellValue(header.getCell(c)).trim());
            }
        } catch (IOException e) {
            LogUtil.log(ExcelReaderUtil.class, "Error reading headers from " + sheetName + ": " + e.getMessage());
        }
        return headers;
    }

    /* ---------- helpers ---------- */

    /** Get cell text exactly as Excel displays it. */
    private static String getCellValue(Cell cell) {
        if (cell == null) return "";
        return DF.formatCellValue(cell);
    }

    /** Returns true if a row has no non-blank cells. */
    private static boolean rowIsEmpty(Row row) {
        if (row == null) return true;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c, MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && !getCellValue(cell).trim().isEmpty()) return false;
        }
        return true;
    }
}
