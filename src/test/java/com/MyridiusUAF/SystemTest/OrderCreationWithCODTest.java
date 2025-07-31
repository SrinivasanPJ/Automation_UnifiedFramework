package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import org.testng.ITestContext;
import org.testng.annotations.Test;

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
// System Test
public class OrderCreationWithCODTest extends BaseTest {

    /**
     * Executes the end-to-end order creation flow with Cash on Delivery.
     *
     * @param context TestNG test context
     */
    @Test(description = "Add products to cart with COD order", priority = 1)
    public void addProductsToCartWithCOD(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);    // Data-driven: TestID=1, InputID=Ip1
        performLogin("1");
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
        // Payment method step for COD is skipped or handled inside clickOnPaymentMethodContinueButton()
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();
    }
}
