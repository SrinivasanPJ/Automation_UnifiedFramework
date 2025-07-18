package com.AutoPOC.utils.data;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.reporting.LogUtil;
import com.AutoPOC.utils.excel.ExcelReaderUtil;

import java.util.List;
import java.util.Map;

/**
 * Utility for reading synthetic input data from Excel.
 * Supports fetching by unique Input ID or listing all Input IDs for TestNG data providers.
 */
public class SyntheticDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Synthetic_Data_Sheet_Name");
    private static final int INPUT_ID_COLUMN_IDX = 5; // Column F (zero-based)

    /**
     * Retrieves a row of synthetic input data by matching the Input ID.
     *
     * @param inputId Input ID value to look up (e.g., "Ip1")
     * @return Map of column name to cell value for the matched row
     */
    public static Map<String, String> getInputDataById(String inputId) {
        //LogUtil.log(SyntheticDataUtil.class, "Fetching input data for ID: " + inputId);
        Map<String, String> inputData = ExcelReaderUtil.getRowByKey(
                FILE_PATH,
                SHEET_NAME,
                INPUT_ID_COLUMN_IDX,
                inputId,
                1 // Header row is at index 1 (Excel row 2)
        );
        //LogUtil.log(SyntheticDataUtil.class, "Input data retrieved successfully for ID: " + inputId);
        return inputData;
    }

    /**
     * Returns all Input IDs in the sheet as a 1D TestNG-compatible data provider array.
     * Example output: { {"Ip1"}, {"Ip2"}, ... }
     *
     * @return Object[][] containing all Input IDs in the sheet
     */
    public static Object[][] getAllInputIDs() {
        LogUtil.log(SyntheticDataUtil.class, "Fetching all Input IDs from synthetic sheet...");
        List<Map<String, String>> rows = ExcelReaderUtil.getAllRows(FILE_PATH, SHEET_NAME);
        Object[][] out = new Object[rows.size()][1];

        for (int i = 0; i < rows.size(); i++) {
            out[i][0] = rows.get(i).get("Input ID");
        }

        LogUtil.log(SyntheticDataUtil.class, "Total Input IDs found: " + rows.size());
        return out;
    }
}
