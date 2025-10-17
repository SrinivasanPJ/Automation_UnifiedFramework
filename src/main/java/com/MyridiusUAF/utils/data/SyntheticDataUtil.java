package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.SyntheticDataDao;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;

import java.util.List;
import java.util.Map;

/**
 * Utility for reading synthetic input data from Excel.
 * Supports fetching by unique Input ID or listing all Input IDs for TestNG data providers.
 */
public class SyntheticDataUtil {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String SHEET_NAME = ConfigReader.getProperty("Synthetic_Data_Sheet_Name");

    public static Map<String, String> getInputDataById(String inputId) {
        if (DataMode.isDb()) return new SyntheticDataDao().byInputId(inputId);
        return ExcelReaderUtil.getRowByKey(FILE_PATH, SHEET_NAME, 5, inputId, 1); // header row index 1
    }

    public static Object[][] getAllInputIDs() {
        if (DataMode.isDb()) return new SyntheticDataDao().allInputIdsForDataProvider();
        LogUtil.log(SyntheticDataUtil.class, "Fetching all Input IDs from synthetic sheet...");
        List<Map<String, String>> rows = ExcelReaderUtil.getAllRows(FILE_PATH, SHEET_NAME);
        Object[][] out = new Object[rows.size()][1];
        for (int i = 0; i < rows.size(); i++) out[i][0] = rows.get(i).get("Input ID");
        return out;
    }
}