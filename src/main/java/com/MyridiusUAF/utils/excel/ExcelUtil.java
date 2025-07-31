package com.MyridiusUAF.utils.excel;

import org.apache.poi.ss.usermodel.*;

/**
 * Enterprise utility for Excel cell/row manipulation and searching using Apache POI.
 * Used for result tracking and data-driven test management.
 */
public class ExcelUtil {

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

    // Prevent instantiation
    private ExcelUtil() { }
}
