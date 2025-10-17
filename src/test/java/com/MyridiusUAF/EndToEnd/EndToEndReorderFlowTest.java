package com.MyridiusUAF.EndToEnd;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.flows.ReorderFlow;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.data.ExecutionDataUtil;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.io.FileInputStream;

/**
 * End-to-end test that authenticates a user and executes a Reorder flow
 * using the reusable {@link ReorderFlow}. All execution evidence is
 * persisted to Excel for auditability without overwriting existing rows.
 * <p>
 * <b>Preconditions:</b> Test data and configuration are available; application is reachable.<br>
 * <b>Postconditions:</b> Order details are written to the configured Excel sheet.
 *
 * @see ReorderFlow
 * @since 1.0
 */
@TestType(TestType.Kind.E2E)
public class EndToEndReorderFlowTest extends BaseTest {

    // ---------------------------------------------------------------------
    // Configuration & Data Bindings
    // ---------------------------------------------------------------------
    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String E2E_SHEET = ConfigReader.getProperty("End_To_End_Sheet_Name");
    private static final String TEST_ID = "1";
    private static final String INPUT_ID = "Ip1";

    /**
     * End-to-End: Login and execute Reorder via {@link ReorderFlow}; then persist
     * order evidence to Excel (idempotent write to the next available row).
     *
     * <p><b>Steps:</b>
     * <ol>
     *   <li>Initialize test context and allocate a new Excel row</li>
     *   <li>Authenticate (login)</li>
     *   <li>Execute the reusable Reorder business flow</li>
     *   <li>Persist order details to Excel for traceability</li>
     * </ol>
     *
     * @param context TestNG context for sharing the allocated Excel row index
     * @throws InterruptedException if the underlying flow uses waits/sleeps
     */
    @Test(description = "End-to-End: Authenticate and execute Reorder via ReorderFlow; persist order evidence to Excel")
    public void executeAuthenticatedReorderFlow(final ITestContext context) throws InterruptedException {
        // Initialize test context & allocate a unique Excel row
        initializeTestContext(TEST_ID, INPUT_ID, context);
        allocateE2EExcelRowIfNeeded(context);

        // --- Test steps (same for both modes) ---
        logStep("Authenticating test user");
        performLogin(TEST_ID);

        logStep("Executing ReorderFlow");
        new ReorderFlow(addProductsToCartAndPlaceOrderPage, orderInformationPage).perform();

        // Persist order details:
        // - In DB mode, OrderInformationPage/OrderDataUtil will write to DB and skip Excel.
        // - In Excel mode, it will append to the allocated row.
        logStep("Persisting order details");
        orderInformationPage.writeDataToFile(context);

        logStep("Completed E2E Reorder scenario");
    }

//    @AfterMethod(alwaysRun = true)
//    public void recordExecutionData(final ITestResult result) {
//        if (DataMode.isDb()) {
//            // Let the DB writer handle it (row index not needed)
//            ExecutionDataUtil.writeExecutionData(-1, result);
//            return;
//        }
//
//        // Excel mode: write to the row we allocated earlier
//        final Object idx = result.getTestContext().getAttribute("ExcelRowIndex");
//        final int rowIndex = (idx instanceof Integer) ? (Integer) idx : -1;
//        if (rowIndex != -1) {
//            ExecutionDataUtil.writeExecutionData(rowIndex, result);
//        }
//    }

    private void logStep(final String message) {
        System.out.println("[EndToEndReorderFlowTest] " + message);
    }
}