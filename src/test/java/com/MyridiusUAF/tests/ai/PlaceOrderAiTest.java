package com.MyridiusUAF.tests.ai;

import com.MyridiusUAF.base.BaseTest;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class PlaceOrderAiTest extends BaseTest {

    @Test
    public void orderAiTest(final ITestContext context) {
        initializeTestContext("1", "Ip1", context);
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
        addProductsToCartAndPlaceOrderPage.selectPaymentMethod("Credit Card");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.fillPaymentInformation("4485564059489345", "123");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();
        orderInformationPage.clickOrderDetailsLink();

        String orderId = orderInformationPage.getOrderId();
        Assert.assertTrue(orderId != null && !orderId.isBlank(), "Order ID should be present after placing an order.");
    }
}