package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.testng.ITestContext;
import org.testng.annotations.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

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
@TestType(TestType.Kind.SYSTEM)
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

        // One call that handles both backends:
        // - DB mode: writes to DB (skips Excel)
        // - Excel mode: computes next row & writes to Excel
        orderInformationPage.writeDataToFile(context);
    }
}
