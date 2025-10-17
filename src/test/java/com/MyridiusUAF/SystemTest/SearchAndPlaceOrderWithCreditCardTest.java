package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.annotations.TestType;
import com.MyridiusUAF.utils.db.DataMode;
import org.testng.ITestContext;
import org.testng.annotations.Test;

/**
 * SystemTest: Validates end-to-end order placement using product search
 * and payment via Credit Card for an authenticated user.
 */
@TestType(TestType.Kind.SYSTEM)
public class SearchAndPlaceOrderWithCreditCardTest extends BaseTest {

    @Test(description = "Validates end-to-end order placement using product search and Credit Card payment.", priority = 1)
    public void placeOrderWithProductSearchAndCreditCard(ITestContext context) {
        // 1) Prepare context and login
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        // 2) Prepare account/cart
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.selectProductIfNoAddressesExist();

        // 3) Product search -> add to cart
        addProductsToCartAndPlaceOrderPage.addToCartAndGoToCart();

        // 4) Checkout steps
        addProductsToCartAndPlaceOrderPage.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrderPage.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrderPage.clickCheckoutButton();
        addProductsToCartAndPlaceOrderPage.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrderPage.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrderPage.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingMethodContinueButton();

        // 5) Credit Card payment
        addProductsToCartAndPlaceOrderPage.selectPaymentMethod("Credit Card");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        // NOTE: Prefer pulling these from synthetic data instead of hardcoding
        addProductsToCartAndPlaceOrderPage.fillPaymentInformation("4485564059489345", "123");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();

        // 6) Confirm & verify
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();

        // 7) Open order details page to ensure Order Date element is present,
        // then persist (DB or Excel handled internally by the page object)
        orderInformationPage.clickOrderDetailsLink();
        orderInformationPage.writeDataToFile(context);
    }
}
