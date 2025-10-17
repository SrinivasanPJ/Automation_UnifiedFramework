package com.MyridiusUAF.utils.excel;

/**
 * Column index constants for End-to-End test binding sheet.
 * <p>
 * Used to avoid magic numbers in code that reads/writes execution metadata
 * (Run ID, status, dates, order info, etc.) to Excel.
 * <br>
 * <b>Note:</b> All indexes are zero-based (first column = 0).
 */
public final class E2EBindingColumnIndex {

    /**
     * Column index for the unique run identifier ("RunID").
     */
    public static final int RUN_ID = 5;

    /**
     * Column index for execution date ("Exec Date").
     */
    public static final int EXEC_DATE = 6;

    /**
     * Column index for execution time ("Exec Time").
     */
    public static final int EXEC_TIME = 7;

    /**
     * Column index for execution status ("Exec Status").
     */
    public static final int EXEC_STATUS = 8;

    /**
     * Column index for order ID (if generated during the test).
     */
    public static final int ORDER_ID = 9;

    /**
     * Column index for order date.
     */
    public static final int ORDER_DATE = 10;

    /**
     * Column index for failure reason.
     */
    public static final int FAILURE_REASON = 11;  // L

    public static final int FIRST_DATA_ROW_E2E = 1; // row 0 is header

    /**
     * Private constructor to prevent instantiation.
     */
    private E2EBindingColumnIndex() {
    }
}
