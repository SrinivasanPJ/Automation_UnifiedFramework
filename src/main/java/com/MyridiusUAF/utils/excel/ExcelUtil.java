package com.MyridiusUAF.utils.excel;

import org.apache.poi.ss.usermodel.*;

/**
 * Enterprise utility for Excel cell/row manipulation and searching using Apache POI.
 * Used for result tracking and data-driven test management.
 */
public class ExcelUtil {

    private ExcelUtil() { } // Prevent instantiation

    /**
     * Finds the next available (empty) row in the sheet, starting from startRow,
     * by looking for the last non-empty cell in the specified column (runIdCol).
     *
     * @param sheet     The Excel sheet
     * @param runIdCol  Column index to check for filled/blank (typically Run ID col)
     * @param startRow  Row index to start searching from (usually after header)
     * @return          Index of next available row to write to (appends if full)
     */
    public static int findNextAvailableRow(Sheet sheet, int runIdCol, int startRow) {
        int lastFilledRow = startRow - 1;
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell runIdCell = row.getCell(runIdCol, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (runIdCell != null && !runIdCell.toString().trim().isEmpty()) {
                lastFilledRow = i;
            }
        }
        return lastFilledRow + 1;
    }

    /**
     * Finds the maximum Run ID number present in the given column for all rows,
     * expecting Run IDs like "R123".
     *
     * @param sheet     Excel sheet
     * @param runIdCol  Run ID column index
     * @param startRow  Row to start (data rows, not header)
     * @return          Highest Run ID integer found, or 0 if none
     */
    public static int getMaxRunId(Sheet sheet, int runIdCol, int startRow) {
        int maxId = 0;
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell runIdCell = row.getCell(runIdCol, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (runIdCell != null) {
                String runIdVal = runIdCell.toString().trim();
                if (runIdVal.startsWith("R")) {
                    try {
                        int num = Integer.parseInt(runIdVal.substring(1));
                        if (num > maxId) maxId = num;
                    } catch (NumberFormatException ignored) {
                        // Ignore non-numeric Run IDs
                    }
                }
            }
        }
        return maxId;
    }

    /**
     * Like getMaxRunId but supports arbitrary prefixes, e.g. "R" or "E2E".
     * It extracts digits after the prefix and returns the maximum numeric part.
     */
    public static int getMaxRunIdWithPrefix(Sheet sheet, int runIdCol, int startRow, String prefix) {
        int max = 0;
        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String val = getCellString(row, runIdCol);
            if (!val.startsWith(prefix)) continue;
            String digits = val.substring(prefix.length()).replaceAll("[^0-9]", "");
            if (digits.isEmpty()) continue;
            try {
                int n = Integer.parseInt(digits);
                if (n > max) max = n;
            } catch (NumberFormatException ignored) { }
        }
        return max;
    }

    /**
     * Returns trimmed string value for a cell in the given row/column.
     * Safe for nulls and mixed cell types (uses DataFormatter).
     */
    public static String getCellString(Row row, int colIndex) {
        if (row == null) return "";
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return "";
        return new DataFormatter().formatCellValue(cell).trim();
    }

    /**
     * Scans downward and returns the last row index that has a non-blank value
     * in the given column, starting from startRow. Returns startRow-1 if none.
     */
    public static int getLastFilledRow(Sheet sheet, int colIndex, int startRow) {
        int last = startRow - 1;
        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String v = getCellString(row, colIndex);
            if (!v.isBlank()) last = r;
        }
        return last;
    }

    /**
     * Returns the column index for a header cell with the given name (case-insensitive).
     * Throws if header not found.
     *
     * @param headerRow  Row containing headers (usually first row)
     * @param headerName Header to find (case-insensitive)
     * @return           Column index (zero-based)
     */
    public static int getColumnIndex(Row headerRow, String headerName) {
        if (headerRow == null) throw new IllegalArgumentException("Header row cannot be null");
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String cellVal = headerRow.getCell(i, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
            if (cellVal.equalsIgnoreCase(headerName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Header not found: " + headerName);
    }

    /**
     * Sets a cell's value and style in the given row and column index.
     * Overwrites any existing cell content in that location.
     *
     * @param row      Row to update
     * @param colIndex Zero-based column index
     * @param value    String value to set
     * @param style    CellStyle to apply
     */
    public static void setCellValue(Row row, int colIndex, String value, CellStyle style) {
        if (row == null) throw new IllegalArgumentException("Target row cannot be null.");
        Cell cell = row.getCell(colIndex, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setCellValue(value != null ? value : "");
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    /**
     * Finds the first row (from startRow downward) where one column is NON-blank
     * and another column is BLANK. Useful to “finish” a partially written row.
     *
     * @param sheet          target sheet
     * @param mustHaveCol    column index that must be non-blank (e.g., ORDER_ID)
     * @param mustBeBlankCol column index that must be blank (e.g., RUN_ID)
     * @param startRow       first data row to check (0-based)
     * @return row index if found; -1 if none
     */
    public static int findRowWithNonBlankAndBlank(Sheet sheet, int mustHaveCol, int mustBeBlankCol, int startRow) {
        for (int r = sheet.getLastRowNum(); r >= startRow; r--) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String hasVal   = getCellString(row, mustHaveCol);
            String blankVal = getCellString(row, mustBeBlankCol);
            if (!hasVal.isBlank() && blankVal.isBlank()) {
                return r;
            }
        }
        return -1;
    }

    /** Case-insensitive match of two column values in any row (startRow..last). */
    public static boolean containsPairInColumns(
            Sheet sheet,
            int colA, String valA,
            int colB, String valB,
            int startRow) {

        if (sheet == null) return false;
        String aNeedle = valA == null ? "" : valA.trim();
        String bNeedle = valB == null ? "" : valB.trim();

        for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            String a = getCellString(row, colA);
            String b = getCellString(row, colB);

            if (a.equalsIgnoreCase(aNeedle) && b.equalsIgnoreCase(bNeedle)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the next row index to append new data (last non-blank row + 1), scanning any column. */
    public static int findAppendRow(Sheet sheet, int startRow) {
        // default to first data row if nothing is filled yet
        int lastWithData = startRow - 1;
        int physLast = sheet.getLastRowNum();
        DataFormatter fmt = new DataFormatter();

        for (int r = physLast; r >= startRow; r--) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            short lastCell = row.getLastCellNum();
            if (lastCell < 0) continue;

            for (int c = 0; c < lastCell; c++) {
                Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) continue;
                String text = fmt.formatCellValue(cell).trim();
                if (!text.isBlank()) {
                    lastWithData = r;
                    return lastWithData + 1; // first empty row after the last used one
                }
            }
        }
        return startRow; // nothing below header -> first data row
    }
}
