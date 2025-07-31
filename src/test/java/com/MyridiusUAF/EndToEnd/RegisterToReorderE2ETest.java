package com.MyridiusUAF.EndToEnd;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.utils.data.ExecutionDataUtil;
import com.MyridiusUAF.utils.data.RandomDataGenerator;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.config.ConfigReader;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

/**
 * End-to-End scenario:
 * Register new user → Login → Place order → Verify order → Reorder.
 * Records detailed Excel evidence per execution.
 */
public class RegisterToReorderE2ETest extends BaseTest {

    private static final String FILE_PATH = ConfigReader.getProperty("Test_Data_File_Path");
    private static final String E2E_SHEET = ConfigReader.getProperty("End_To_End_Sheet_Name");

    /**
     * Main E2E test: covers registration, login, ordering, verification, and reorder flow.
     * @param context TestNG context for test-scoped data (row index, etc)
     */
    @Test(description = "E2E: Register user, login, place order with credit card, verify order, and reorder")
    public void executeRegisterToReorderFlow(ITestContext context) throws InterruptedException {
        // 1. Prepare test data and Excel row index (unique for this run)
        initializeTestContext("1", "Ip1", context);
        Sheet sheet = ExcelReaderUtil.getSheet(FILE_PATH, E2E_SHEET);
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, E2EBindingColumnIndex.RUN_ID, 2);
        context.setAttribute("ExcelRowIndex", rowIndex);

        // 2. Register a new user with random data
        logStep("Registering new user");
        String firstName = RandomDataGenerator.getRandomFirstName();
        String lastName = RandomDataGenerator.getRandomLastName();
        registerPage.register("Male", firstName, lastName, 15);
        loginPage.clickLogoutLink();

        // 3. Login as that registered user
        logStep("Logging in with registered user");
        performLogin("1");

        // 4. Place an order with credit card
        logStep("Placing order with Credit Card");
        clearAddressIfExistsAndSelectProduct("Blue Jeans");
        addProductsToCartAndPlaceOrderPage.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrderPage.addProductToCartAndCheckoutWithCC();

        // 5. Verify the order confirmation and details
        logStep("Verifying order details");
        orderInformationPage.verifyOrderDetails(context);

        // 6. Perform reorder scenario and persist results
        logStep("Reordering the product");
        performReorderFlow();
        orderInformationPage.writeDataToFile(context);

        logStep("E2E scenario completed: Register to Reorder flow");
    }

    /**
     * Clears saved address (if any) and performs product search/selection.
     */
    private void clearAddressIfExistsAndSelectProduct(String productName) throws InterruptedException {
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.selectProductIfNoAddressesExist();
        addProductsToCartAndPlaceOrderPage.searchAndSelectSuggestion(productName);
    }

    /**
     * End-to-end flow for reordering from past orders.
     */
    private void performReorderFlow() throws InterruptedException {
        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.clickOnOrdersLink();
        orderInformationPage.clickOrderDetailsLink();
        addProductsToCartAndPlaceOrderPage.clickOnReorderButton();
        addProductsToCartAndPlaceOrderPage.addProductToCartAndCheckoutWithCC();
        orderInformationPage.clickOrderDetailsLink();
        Thread.sleep(2000);
    }

    /**
     * Writes execution result to Excel after each test (never overwrites).
     * Ensures row index is not -1 (invalid).
     */
    @AfterMethod(alwaysRun = true)
    public void recordExecutionData(ITestResult result) {
        Object idx = result.getTestContext().getAttribute("ExcelRowIndex");
        int rowIndex = (idx instanceof Integer) ? (Integer) idx : -1;
        if (rowIndex != -1) {
            ExecutionDataUtil.writeExecutionData(rowIndex, result);
        }
    }

    /**
     * Console step logger for clear E2E trace.
     * You can extend to log to ExtentReport or custom logger if needed.
     */
    private void logStep(String message) {
        System.out.println("[E2E] " + message);
    }
}
