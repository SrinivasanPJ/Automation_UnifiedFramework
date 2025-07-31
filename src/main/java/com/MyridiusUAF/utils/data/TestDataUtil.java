package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;

import java.util.List;
import java.util.Map;

/**
 * Utility class for fetching login test data from Excel.
 * <p>
 * Supports row-level lookup by TestID and bulk retrieval for DataProvider use.
 */
public class TestDataUtil {

    private static final String FILE_PATH   = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME  = ConfigReader.getProperty("Login_Data_Sheet_Name");

    /**
     * Returns a map of column names to values for a specific TestID row in the login data sheet.
     *
     * @param testID The test case identifier (must match exactly in Excel).
     * @return Map<String, String> representing all fields in that row, or empty map if not found.
     */
    public static Map<String, String> getTestCaseByTestID(String testID) {
        return ExcelReaderUtil.getRowByKey(
                FILE_PATH,
                SHEET_NAME,
                /* keyColumnIndex */ 0,
                testID,
                /* headerRowIndex */ 0
        );
    }

    /**
     * Fetches all TestIDs from the login data sheet for use in TestNG data providers.
     * Each returned array contains a single TestID, e.g., { {"1"}, {"2"} }.
     *
     * @return 2D Object array suitable for TestNG DataProvider.
     */
    public static Object[][] getAllTestIDs() {
        List<Map<String, String>> rows = ExcelReaderUtil.getAllRows(FILE_PATH, SHEET_NAME);
        Object[][] out = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) {
            out[i][0] = rows.get(i).get("TestID");
        }
        return out;
    }
}
