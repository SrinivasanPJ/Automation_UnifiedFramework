package com.MyridiusUAF.utils.excel;

/**
 * Column index constants for transactional/system test Excel sheets.
 * <p>
 * Provides zero-based column indexes for writing automation results
 * and order/exception metadata.
 * <br>
 * <b>Note:</b> All indexes are zero-based (e.g., F = 5).
 */
public final class ExcelColumnIndex {

    /** Column index for unique run identifier ("RunID"). */
    public static final int RUN_ID = 5; // F

    /** Column index for execution date ("Exec Date"). */
    public static final int EXEC_DATE = 6; // G

    /** Column index for execution time ("Exec Time"). */
    public static final int EXEC_TIME = 7; // H

    /** Column index for execution status ("Exec Status"). */
    public static final int EXEC_STATUS = 8; // I

    /** Column index for order ID ("Order ID"). */
    public static final int ORDER_ID = 9; // J

    /** Column index for order date ("Order Date"). */
    public static final int ORDER_DATE = 10; // K

    /** Column index for failure reason or exception message ("Failure Reason"). */
    public static final int FAILURE_REASON = 11; // L

    /** First data row index (0-based) for Transactional_Data sheet. */
    public static final int FIRST_DATA_ROW_SYSTEM = 2; // rows 0..1 are headers

    /**
     * Private constructor to prevent instantiation.
     */
    private ExcelColumnIndex() { }
}
