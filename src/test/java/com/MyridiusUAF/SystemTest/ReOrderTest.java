package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.flows.ReorderFlow;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * System test for the Reorder workflow (authenticated user).
 * Signs in, runs {@link ReorderFlow}, and appends order details to the transactional Excel sheet.
 * Preconditions: AUT reachable; config keys present (Test_Data_File_Path, Transactional_Data_Sheet_Name).
 * Postconditions: Reorder confirmed; order details appended (no overwrite).
 */
@TestType(TestType.Kind.SYSTEM)
public class ReOrderTest extends BaseTest {

    /**
     * Executes the Reorder scenario end-to-end:
     * <ol>
     *   <li>Initialize TestNG context and authenticate.</li>
     *   <li>Run the reusable {@link ReorderFlow}.</li>
     *   <li>Persist order details to Excel (append-only) and expose the used row index via context.</li>
     * </ol>
     *
     * @param context TestNG context used to store shared data (e.g., <code>ExcelRowIndex</code>)
     * @throws InterruptedException if underlying waits/sleeps are used inside the flow
     */
    @Test(description = "Re-order the product", priority = 1)
    public void reOrderAProduct(final ITestContext context) throws InterruptedException {
        // 1) Prepare context & authenticate
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        // 2) Execute the reusable Reorder business flow (no duplication of steps here)
        new ReorderFlow(addProductsToCartAndPlaceOrderPage, orderInformationPage).perform();

        // 3) Persist evidence (append-only) to the configured Excel sheet
        final Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        final int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);

        // Expose the row index for any downstream consumers in the same TestNG context
        context.setAttribute("ExcelRowIndex", rowIndex);

        // Write captured order details
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }
}
