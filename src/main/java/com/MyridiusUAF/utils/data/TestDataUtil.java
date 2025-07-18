package com.AutoPOC.utils.data;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.excel.ExcelReaderUtil;

import java.util.List;
import java.util.Map;

public class TestDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Login_Data_Sheet_Name");

    /**
     * Lookup a single row by TestID in Common_TestData
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
     * Returns every TestID (first column) as a DataProvider array.
     * e.g. { { "1" }, { "2" }, … }
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
