package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Validates end-to-end order placement using product search
 * and payment via Credit Card for an authenticated user.
 * <p>
 * Workflow:
 * <ol>
 *   <li>Login and prepare account context</li>
 *   <li>Clear existing addresses and prepare cart</li>
 *   <li>Product selection and add to cart (via search)</li>
 *   <li>Checkout flow with full billing details</li>
 *   <li>Credit Card payment and order confirmation</li>
 * </ol>
 */
// System test
public class SearchAndPlaceOrderWithCreditCardTest extends BaseTest {

    /**
     * Executes the E2E test: product search, add to cart, checkout, credit card payment.
     *
     * @param context TestNG context for tracking/excel
     */
    @Test(description = "Validates end-to-end order placement using product search and Credit Card payment.", priority = 1)
    public void placeOrderWithProductSearchAndCreditCard(ITestContext context) throws InterruptedException {
        // 1. Prepare user context and login
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        // 2. Prepare account state and cart
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.selectProductIfNoAddressesExist();

        // 3. Add searched product to cart
        addProductsToCartAndPlaceOrderPage.addToCartAndGoToCart();

        // 4. Proceed through checkout workflow
        addProductsToCartAndPlaceOrderPage.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrderPage.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrderPage.clickCheckoutButton();
        addProductsToCartAndPlaceOrderPage.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrderPage.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrderPage.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingMethodContinueButton();

        // 5. Select Credit Card payment and fill payment details
        addProductsToCartAndPlaceOrderPage.selectPaymentMethod("Credit Card");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.fillPaymentInformation("4485564059489345", "123");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();

        // 6. Complete checkout and verify success
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();
    }
}
