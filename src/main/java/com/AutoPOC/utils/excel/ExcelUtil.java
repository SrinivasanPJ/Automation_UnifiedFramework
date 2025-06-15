package com.AutoPOC.utils.excel;

import com.AutoPOC.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

public class ExcelUtil {

    /**
     * Finds the next empty row in the sheet from the specified start row,
     * checking if the given column index is blank.
     *
     * @param sheet       The Excel sheet
     * @param columnIndex The column index to check for blank cell
     * @param startRow    Row index to start searching
     * @return index of the next available row
     */
    public static int findNextAvailableRow(Sheet sheet, int columnIndex, int startRow) {
        for (int i = startRow; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            Cell cell = row.getCell(columnIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || cell.toString().trim().isEmpty()) {
                //LogUtil.log(ExcelUtil.class, "Next available row found at index: " + i);
                return i;
            }
        }

        int next = sheet.getLastRowNum() + 1;
        //LogUtil.log(ExcelUtil.class, "Appending new row at index: " + next);
        return next;
    }

    /**
     * Sets value in the given cell and applies style.
     *
     * @param row      Target row
     * @param colIndex Column index
     * @param value    Value to write
     * @param style    Cell style
     */
    public static void setCellValue(Row row, int colIndex, String value, CellStyle style) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(value);
        cell.setCellStyle(style);

        //LogUtil.log(ExcelUtil.class, "Set cell value: Row=" + row.getRowNum() + ", Col=" + colIndex + ", Value=" + value);
    }
}
