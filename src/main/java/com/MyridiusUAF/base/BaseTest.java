package com.AutoPOC.base;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.pages.AddProductsToCartAndPlaceOrder;
import com.AutoPOC.pages.LoginPage;
import com.AutoPOC.pages.OrderInformationPage;
import com.AutoPOC.utils.context.TestContextManager;
import com.AutoPOC.utils.context.TestDataKeys;
import com.AutoPOC.utils.core.DriverFactory;
import com.AutoPOC.utils.core.ScreenshotUtil;
import com.AutoPOC.utils.data.ExecutionDataUtil;
import com.AutoPOC.utils.data.SyntheticDataUtil;
import com.AutoPOC.utils.data.TestDataUtil;
import com.AutoPOC.utils.excel.ExcelColumnIndex;
import com.AutoPOC.utils.excel.ExcelReaderUtil;
import com.AutoPOC.utils.excel.ExcelUtil;
import com.AutoPOC.utils.reporting.EmailSenderUtil;
import com.AutoPOC.utils.reporting.ExtentReportManager;
import org.apache.poi.ss.usermodel.Sheet;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.annotations.*;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Abstract base class for all TestNG test classes.
 * <p>
 * Responsibilities include:
 * <ul>
 *     <li>WebDriver lifecycle management</li>
 *     <li>Data-driven test execution</li>
 *     <li>ExtentReport integration</li>
 *     <li>Failure handling & screenshot capture</li>
 *     <li>Dynamic test context setup</li>
 * </ul>
 */
public abstract class BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    private static Instant startTime;

    protected WebDriver driver;
    protected LoginPage loginPage;
    protected AddProductsToCartAndPlaceOrder addProductsToCartAndPlaceOrder;
    protected OrderInformationPage orderInformationPage;

    // ─────────────────────────────────────────────────────────────────────
    // Suite Setup & Teardown
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Executes once before the entire test suite.
     * Initializes reporting and logs execution start time.
     */
    @BeforeSuite
    public void suiteSetup() {
        startTime = Instant.now();
        logger.info("Test execution started at: {}", getCurrentTime());
        ExtentReportManager.INSTANCE.initReport();
    }

    /**
     * Executes once after the entire test suite.
     * Sends report summary via email and logs execution duration.
     */
    @AfterSuite
    public void suiteTearDown() {
        ExtentReportManager.INSTANCE.flushReport();

        EmailSenderUtil.sendTestResultEmail(
                ExtentReportManager.INSTANCE.getTotalTests(),
                ExtentReportManager.INSTANCE.getTestsPassed(),
                ExtentReportManager.INSTANCE.getTestsFailed()
        );

        logger.info("Test execution ended at: {}", getCurrentTime());
        logger.info("Total execution time: {}", getExecutionDuration());
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test Setup & Execution
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Executed before each test method.
     * Creates an ExtentReport node for the current test.
     *
     * @param method the test method being run
     */
    @BeforeMethod
    public void setUp(Method method) {
        ExtentReportManager.INSTANCE.createTest(method.getName());
        logger.info("Test setup initialized for method: {}", method.getName());
    }

    /**
     * Core driver/test context initializer. Launches browser, logs in, and initializes page objects.
     *
     * @param testID  unique Test ID identifier from test data
     * @param context TestNG context
     */
    @Test(dataProvider = "testData")
    public void executeTestForTestID(String testID, ITestContext context) {
        logger.info("Preparing to execute test for TestID: {}", testID);

        Map<String, String> testData = TestDataUtil.getTestCaseByTestID(testID);
        if (testData.isEmpty()) {
            throw new IllegalArgumentException("No test data found for TestID: " + testID);
        }

        String browser = testData.getOrDefault(TestDataKeys.BROWSER, "chrome");
        String testURL = testData.getOrDefault(TestDataKeys.URL, "about:blank");
        String username = testData.get(TestDataKeys.USERNAME);
        String password = testData.get(TestDataKeys.PASSWORD);

        context.setAttribute("TestID", testID);
        context.setAttribute("Browser", browser);

        try {
            DriverFactory.initializeDriver(browser);
            driver = DriverFactory.getDriver();
            driver.get(testURL);
            logger.info("Navigated to: {}", testURL);
        } catch (Exception e) {
            logger.error("Failed to initialize WebDriver.", e);
            throw new RuntimeException("Driver initialization failed", e);
        }

        loginPage = new LoginPage();
        addProductsToCartAndPlaceOrder = new AddProductsToCartAndPlaceOrder();
        orderInformationPage = new OrderInformationPage();

        try {
            loginPage.login(username, password);
            logger.info("Login successful for user: {}", username);
            //PopupHandler.dismissSavePasswordPopup();
        } catch (Exception e) {
            logger.error("Login failed for user: {}", username, e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Test Teardown & Reporting
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Captures execution result and logs reporting details.
     * Captures screenshots on failure.
     *
     * @param result result of the executed test method
     */
    @AfterMethod(alwaysRun = true)
    public void recordExecutionData(ITestResult result) {
        try {
            int rowIndex = getOrCreateExcelRowIndex(result);
            ExecutionDataUtil.writeExecutionData(rowIndex, result);

            switch (result.getStatus()) {
                case ITestResult.SUCCESS -> ExtentReportManager.INSTANCE.logPass("Test Passed: " + result.getName());

                case ITestResult.FAILURE -> {
                    ExtentReportManager.INSTANCE.logFail("Test Failed: " + result.getName(), result.getThrowable());
                    String screenshotPath = ScreenshotUtil.saveScreenshotAsPNG(driver, result.getName());
                    ExtentReportManager.INSTANCE.attachScreenshotFromPath(screenshotPath, "Failure Screenshot");
                    logger.error("Failure details captured.", result.getThrowable());
                }

                case ITestResult.SKIP -> ExtentReportManager.INSTANCE.logSkip("Test Skipped: " + result.getName());
            }

        } catch (Exception e) {
            logger.error("Error during execution result recording.", e);
        } finally {
            ExtentReportManager.INSTANCE.removeTest();
        }
    }

    /**
     * Quits the WebDriver instance after test method execution.
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            DriverFactory.quitDriver();
        } catch (Exception e) {
            logger.error("Error occurred while quitting WebDriver.", e);
        }
    }

    /**
     * Clears the test context to prevent data leakage between test runs.
     */
    @AfterMethod
    public void clearContext() {
        TestContextManager.clear();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Data Providers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Supplies test cases based on static test data.
     *
     * @return array of test IDs
     */
    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        return TestDataUtil.getAllTestIDs();
    }

    /**
     * Supplies synthetic input IDs for parameterized test execution.
     *
     * @return array of input IDs
     */
    @DataProvider(name = "syntheticData")
    public Object[][] syntheticData() {
        return SyntheticDataUtil.getAllInputIDs();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility Methods
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gets the current time formatted for logging purposes.
     *
     * @return formatted current time string
     */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Calculates and returns execution duration from suite start to current time.
     *
     * @return formatted duration string
     */
    private String getExecutionDuration() {
        Duration duration = Duration.between(startTime, Instant.now());
        return String.format("%02d min, %02d sec", duration.toMinutes(), duration.getSeconds() % 60);
    }

    /**
     * Finds or creates the appropriate Excel row index to write test execution data.
     *
     * @param result the current test result
     * @return valid Excel row index
     */
    private int getOrCreateExcelRowIndex(ITestResult result) {
        Object attr = result.getTestContext().getAttribute("ExcelRowIndex");

        if (attr instanceof Integer idx) {
            return idx;
        }

        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );

        int newRow = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        result.getTestContext().setAttribute("ExcelRowIndex", newRow);
        return newRow;
    }

    /**
     * Initializes test context for synthetic data-driven tests.
     *
     * @param testID  test case identifier
     * @param inputID synthetic input identifier
     * @param context TestNG context
     */
    protected void initializeTestContext(String testID, String inputID, ITestContext context) {
        executeTestForTestID(testID, context);
        Map<String, String> inputData = SyntheticDataUtil.getInputDataById(inputID);
        TestContextManager.setInputData(inputData);
    }
}
