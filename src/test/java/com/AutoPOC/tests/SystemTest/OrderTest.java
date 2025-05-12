package com.AutoPOC.tests.SystemTest;

import com.AutoPOC.base.BaseTest;
import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.context.TestContextManager;
import com.AutoPOC.utils.data.SyntheticDataUtil;
import com.AutoPOC.utils.excel.ExcelColumnIndex;
import com.AutoPOC.utils.excel.ExcelReaderUtil;
import com.AutoPOC.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.util.Map;

public class OrderTest extends BaseTest {

    @Test(description = "Place order for a specific synthetic data row")
    public void addProductsToCart(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        addProductsToCartAndPlaceOrder.deleteAddress();
        addProductsToCartAndPlaceOrder.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrder.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrder.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrder.clickCheckoutButton();
        addProductsToCartAndPlaceOrder.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrder.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrder.proceedThroughCheckout();
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
}
