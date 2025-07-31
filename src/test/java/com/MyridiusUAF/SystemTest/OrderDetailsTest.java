package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Verifies order details retrieval and records the information in the transactional Excel sheet.
 * <p>
 * Steps:
 * <ul>
 *   <li>Initializes test context and logs in with test user</li>
 *   <li>Navigates to My Account > Orders > Order Details</li>
 *   <li>Finds the next available row in the Excel transaction sheet</li>
 *   <li>Writes order details (Order ID, Order Date) to Excel</li>
 * </ul>
 * The row index is tracked in TestNG context for post-test processing.
 */
 // System Test
public class OrderDetailsTest extends BaseTest {

    /**
     * Executes the test for order detail verification and persists the result to Excel.
     *
     * @param context TestNG context (used for storing row index)
     */
    @Test(description = "Verify the order details", priority = 1)
    public void orderDetails(ITestContext context) {
        initializeTestContext("1", "Ip1", context);
        performLogin("1");
        addProductsToCartAndPlaceOrderPage.clickOnAccountLink();
        addProductsToCartAndPlaceOrderPage.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();

        // Prepare Excel for evidence
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex);

        orderInformationPage.saveDetailsToExcel(rowIndex);
    }
}
