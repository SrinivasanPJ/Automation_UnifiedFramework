package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.flows.ReorderFlow;
import com.MyridiusUAF.utils.annotations.TestType;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * System test for the Reorder workflow (authenticated user).
 * Uses OrderInformationPage.writeDataToFile(context) to persist evidence
 * in either Excel or DB, depending on DataMode.
 */
@TestType(TestType.Kind.SYSTEM)
public class ReOrderTest extends BaseTest {

    @Test(description = "Re-order the product", priority = 1)
    public void reOrderAProduct(final ITestContext context) throws InterruptedException {
        // 1) Prepare context & authenticate
        initializeTestContext("3", "Ip1", context);
        performLogin("3");
        // 2) Execute the reusable Reorder flow
        new ReorderFlow(addProductsToCartAndPlaceOrderPage, orderInformationPage).perform();
        // 3) Persist evidence (DB or Excel handled internally; no row math in test)
        orderInformationPage.writeDataToFile(context);
    }
}
