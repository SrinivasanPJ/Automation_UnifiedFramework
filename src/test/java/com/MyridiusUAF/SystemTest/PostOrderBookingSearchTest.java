package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.annotations.TestType;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Post-order booking search
 *
 * Goal: After an order is placed (R-series transactional run), locate that exact order
 *       in the "My account > Orders" area and open its details page for verification.
 *
 * Steps:
 *  1) Bootstrap the driver and inject InputID-based synthetic data (TestID=1, InputID=Ip1).
 *  2) Login using TestID=1 credentials (from Login sheet / DB).
 *  3) Go to "My account > Orders".
 *  4) Fetch the latest R-series Order ID (via page helper).
 *  5) Navigate to /orderdetails/{OrderID} and validate the ID on page.
 *  6) Persist evidence (Excel or DB) via a single call.
 *
 * Notes:
 *  - If running in DB mode, the evidence is written into transactional_data / e2e_binding.
 *  - If running in Excel mode, the evidence is appended to the Transactional sheet.
 */
@TestType(TestType.Kind.SYSTEM)
public class PostOrderBookingSearchTest extends BaseTest {

    @Test(description = "Search previously-booked order and open details by order id", groups = {"SystemTest"})
    public void postOrderBookingSearch(ITestContext context) {
        // 1) Initialize context and driver (TestID=1, InputID=Ip1)
        initializeTestContext("1", "Ip1", context);

        // 2) Login
        performLogin("1");

        // 3) Navigate to My Account > Orders
        addProductsToCartAndPlaceOrderPage.clickOnAccountLink();
        addProductsToCartAndPlaceOrderPage.clickOnOrdersLink();

        // 4) Get latest R-series order id from E2E Binding (helper reads from Excel;
        //    if you later want DB-native lookup, add a DAO and gate by DataMode.isDb()).
        String expectedOrderId = orderInformationPage.findLatestRSeriesOrderId();
        Assert.assertNotNull(expectedOrderId, "No R-series Order ID found. Place an order first.");

        // 5) Jump directly to /orderdetails/{id} and verify
        orderInformationPage.openOrderDetailsForLatestRSeriesOrder();
        String actualOrderId = orderInformationPage.getOrderId();
        Assert.assertEquals(actualOrderId, expectedOrderId,
                "Order ID on details page should match the latest R-series entry.");

        // 6) Persist evidence according to data mode:
        //    - Excel mode: appends to Transactional sheet (computes next free row)
        //    - DB mode: updates transactional_data and upserts e2e_binding
        orderInformationPage.writeDataToFile(context);
    }
}
