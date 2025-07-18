package com.AutoPOC.tests.SystemTest;

import com.AutoPOC.base.BaseTest;
import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.excel.ExcelColumnIndex;
import com.AutoPOC.utils.excel.ExcelReaderUtil;
import com.AutoPOC.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class OrderTest extends BaseTest {

    @Test(description = "Add products to cart with COD order", priority = 1)
    public void addProductsToCartWithCOD(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.deleteAddress();
        addProductsToCartAndPlaceOrder.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrder.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrder.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrder.clickCheckoutButton();
        addProductsToCartAndPlaceOrder.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrder.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrder.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrder.checkoutConfirmation();
        addProductsToCartAndPlaceOrder.verifyOrderSuccessMessage();
    }

    @Test(description = "verify the order details", priority = 2)
    public void orderDetails(ITestContext context) {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.clickOnAccountLink();
        addProductsToCartAndPlaceOrder.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }

    @Test(description = "Place order for a specific synthetic data row", priority = 3)
    public void addProductsToCartWithCreditCard(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.deleteAddress();
        addProductsToCartAndPlaceOrder.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrder.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrder.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrder.clickCheckoutButton();
        addProductsToCartAndPlaceOrder.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrder.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrder.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrder.selectPaymentMethod("Credit Card");
        addProductsToCartAndPlaceOrder.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrder.fillPaymentInformation("4485564059489345","123");
        addProductsToCartAndPlaceOrder.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrder.checkoutConfirmation();
        addProductsToCartAndPlaceOrder.verifyOrderSuccessMessage();
        orderInformationPage.clickOrderDetailsLink();
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }

    @Test(description = "Search and Add products to cart with COD order", priority = 4)
    public void searchAndAddProductsToCartWithCOD(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.deleteAddress();
        addProductsToCartAndPlaceOrder.searchAndSelectSuggestion("Blue Jeans");
        addProductsToCartAndPlaceOrder.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrder.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrder.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrder.clickCheckoutButton();
        addProductsToCartAndPlaceOrder.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrder.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrder.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrder.checkoutConfirmation();
        addProductsToCartAndPlaceOrder.verifyOrderSuccessMessage();
        orderInformationPage.clickOrderDetailsLink();
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }

    @Test(description = "Re-order the product", priority = 5)
    public void reOrderAProduct(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.deleteAddress();
        addProductsToCartAndPlaceOrder.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();
        addProductsToCartAndPlaceOrder.clickOnReorderButton();
        addProductsToCartAndPlaceOrder.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrder.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrder.clickCheckoutButton();
        addProductsToCartAndPlaceOrder.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrder.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrder.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrder.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrder.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrder.checkoutConfirmation();
        addProductsToCartAndPlaceOrder.verifyOrderSuccessMessage();
        addProductsToCartAndPlaceOrder.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();
        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        orderInformationPage.saveDetailsToExcel(rowIndex);
    }

}
