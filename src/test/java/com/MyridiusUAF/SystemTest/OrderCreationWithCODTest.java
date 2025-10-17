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
 * SystemTest: Verifies the order creation workflow with "Cash on Delivery" (COD) payment option.
 * <p>
 * This test:
 * <ul>
 *   <li>Logs in with user credentials from Excel (TestID: "1", InputID: "Ip1")</li>
 *   <li>Deletes existing addresses to ensure a clean state</li>
 *   <li>Selects product, adds to cart, proceeds through checkout using COD</li>
 *   <li>Validates success message after order is placed</li>
 * </ul>
 * Result and test evidence are automatically tracked in reporting and Excel.
 */
@TestType(TestType.Kind.SYSTEM)
public class OrderCreationWithCODTest extends BaseTest {

    /**
     * Executes the end-to-end order creation flow with Cash on Delivery.
     *
     * @param context TestNG test context
     */
    @Test(description = "Add products to cart with COD order", priority = 1)
    public void addProductsToCartWithCOD(ITestContext context) throws InterruptedException {
        initializeTestContext("3", "Ip1", context);    // TestID=1, InputID=Ip1 (DB or Excel per config)
        performLogin("3");
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.selectProductIfNoAddressesExist();
        addProductsToCartAndPlaceOrderPage.addToCartAndGoToCart();
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

        // Go to details page and persist order evidence.
        orderInformationPage.clickOrderDetailsLink();
        orderInformationPage.saveDetails();  // → DB or Excel automatically
    }
}
