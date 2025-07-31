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
 * SystemTest: Re-orders a previously purchased product for the logged-in user.
 * <p>
 * Steps:
 * <ol>
 *     <li>Login and prepare account context</li>
 *     <li>Navigate to previous orders, click 'Reorder'</li>
 *     <li>Complete checkout and payment workflow</li>
 *     <li>Verify order success and capture order details in Excel</li>
 * </ol>
 * <p>
 * All test evidence (Order ID/Date) is persisted for traceability.
 */
// End to End Test
public class ReOrderTest extends BaseTest {

    /**
     * Executes the full re-order workflow and persists results.
     *
     * @param context TestNG context for reporting/excel
     */
    @Test(description = "Re-order the product", priority = 1)
    public void reOrderAProduct(ITestContext context) throws InterruptedException {
        // 1. Prepare context, login
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        // 2. Navigate to previous order and trigger reorder flow
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();
        addProductsToCartAndPlaceOrderPage.clickOnReorderButton();

        // 3. Complete checkout using COD or default payment method
        addProductsToCartAndPlaceOrderPage.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrderPage.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrderPage.clickCheckoutButton();
        addProductsToCartAndPlaceOrderPage.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrderPage.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrderPage.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();

        // 4. Persist the new order details for audit/evidence
        orderInformationPage.clickOrderDetailsLink();
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex); // Store for later retrieval
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }
}
