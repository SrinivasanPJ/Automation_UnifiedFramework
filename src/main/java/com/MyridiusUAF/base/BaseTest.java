package com.MyridiusUAF.base;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.pages.*;
import com.MyridiusUAF.utils.context.*;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.core.ScreenshotUtil;
import com.MyridiusUAF.utils.data.*;
import com.MyridiusUAF.utils.excel.*;
import com.MyridiusUAF.utils.reporting.*;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.annotations.*;

import java.lang.reflect.Method;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.MyridiusUAF.utils.annotations.Jira;
import org.testng.ITestResult;

/**
 * Abstract base class for all TestNG test classes.
 * <p>
 * Responsibilities:
 * <ul>
 *     <li>WebDriver lifecycle management</li>
 *     <li>Data-driven test execution (via Excel or other sources)</li>
 *     <li>ExtentReport integration and logging</li>
 *     <li>Failure handling & screenshot capture</li>
 *     <li>Dynamic test context setup and clearing</li>
 * </ul>
 * Extend this class for any test that requires browser interaction or reporting.
 */
public abstract class BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(BaseTest.class);
    private static Instant startTime;

    // Page objects
    protected WebDriver driver;
    protected LoginPage loginPage;
    protected AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage;
    protected OrderInformationPage orderInformationPage;
    protected RegisterPage registerPage;

    // ========== Suite Lifecycle ==========

    /** Runs once before the test suite. Initializes reports and logs the start. */
    @BeforeSuite
    public void suiteSetup() {
        startTime = Instant.now();
        logger.info("Test execution started at: {}", getCurrentTime());
        ExtentReportManager.INSTANCE.initReport();
    }

    /** Runs once after the suite. Flushes reports and sends results summary via email. */
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

    // ========== Per-Test Lifecycle ==========

    /** Prepares ExtentReport node for every test method. */
    @BeforeMethod
    public void setUp(Method method) {
        ExtentReportManager.INSTANCE.createTest(method.getName());
        logger.info("Test setup initialized for method: {}", method.getName());
    }

    /**
     * Main driver/test context initializer.
     * Reads browser, launches WebDriver, and initializes all page objects.
     *
     * @param testID  Unique Test ID from data source (e.g., Excel)
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

        context.setAttribute("TestID", testID);
        context.setAttribute("Browser", browser);

        try {
            DriverFactory.initializeDriver(browser);
            driver = DriverFactory.getDriver();
            driver.get(testURL);
            context.setAttribute("WebDriver", driver); // Make WebDriver accessible for Listeners etc.
            logger.info("Navigated to: {}", testURL);
        } catch (Exception e) {
            logger.error("Failed to initialize WebDriver.", e);
            throw new RuntimeException("Driver initialization failed", e);
        }

        // Initialize page objects AFTER driver is ready
        loginPage = new LoginPage();
        addProductsToCartAndPlaceOrderPage = new AddProductsToCartAndPlaceOrderPage();
        orderInformationPage = new OrderInformationPage();
        registerPage = new RegisterPage();
    }

    /**
     * Utility method for login using current test data.
     * @param testID Test data ID (maps to data source)
     */
    protected void performLogin(String testID) {
        Map<String, String> testData = TestDataUtil.getTestCaseByTestID(testID);
        String username = testData.get(TestDataKeys.USERNAME);
        String password = testData.get(TestDataKeys.PASSWORD);
        try {
            loginPage.login(username, password);
            // Optionally: PopupHandler.dismissSavePasswordPopup();
        } catch (Exception e) {
            logger.error("Login failed for user: {}", username, e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // ========== Reporting, Teardown & Clean-up ==========

    /**
     * Records execution results, writes to Excel, and attaches screenshots on failure.
     * Also stores screenshot path in test context for later JIRA upload.
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
                    result.getTestContext().setAttribute("LastScreenshotPath", screenshotPath); // Store for JIRA
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

    /** Quits WebDriver after each test. */
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            DriverFactory.quitDriver();
        } catch (Exception e) {
            logger.error("Error occurred while quitting WebDriver.", e);
        }
    }

    /**
     * Logs JIRA link to the report, adds automated comments, and attaches screenshots on failure.
     */
    @AfterMethod(alwaysRun = true)
    public void logJiraLinkAndComment(ITestResult result) {
        try {
            Method method = result.getMethod().getConstructorOrMethod().getMethod();
            if (method.isAnnotationPresent(Jira.class)) {
                String jiraId = method.getAnnotation(Jira.class).value();
                String jiraUrl = ConfigReader.getProperty("jira.url") + "/browse/" + jiraId;
                String jiraLinkHtml = "<a href=\"" + jiraUrl + "\" target=\"_blank\">JIRA: " + jiraId + "</a>";
                ExtentReportManager.INSTANCE.logInfo("JIRA Link: " + jiraLinkHtml, getClass());

                // ------ Enterprise-grade Detailed Comment ------
                String statusStr = switch (result.getStatus()) {
                    case ITestResult.SUCCESS -> "✅ PASSED";
                    case ITestResult.FAILURE -> "❌ FAILED";
                    case ITestResult.SKIP -> "⏭️ SKIPPED";
                    default -> "UNKNOWN";
                };

                StringBuilder comment = new StringBuilder();
                comment.append("### 🔹 Automated Test Execution Summary\n")
                        .append("- **Test Name:** ").append(result.getName()).append("\n")
                        .append("- **Test Class:** ").append(result.getTestClass().getName()).append("\n")
                        .append("- **Status:** ").append(statusStr).append("\n")
                        .append("- **Executed On:** ").append(new java.util.Date()).append("\n")
                        .append("- **Environment:** ").append(System.getProperty("env.name", "Unknown")).append("\n")
                        .append("- **Browser:** ").append(result.getTestContext().getAttribute("Browser")).append("\n")
                        .append("- **Executed By:** ").append(System.getProperty("user.name")).append("\n");

                if (result.getStatus() == ITestResult.FAILURE && result.getThrowable() != null) {
                    comment.append("- **Failure Reason:**\n```java\n")
                            .append(result.getThrowable().toString()).append("\n```");
                }
                String reportUrl = ExtentReportManager.INSTANCE.getReportUrl();
                if (reportUrl != null && !reportUrl.isEmpty()) {
                    comment.append("\n- [View Full Automation Report](").append(reportUrl).append(")");
                }
                // JiraUtil.addComment(jiraId, comment.toString());
                JiraUtil.addADFComment(jiraId, result);

                // Attach screenshot if present (on failure)
                if (result.getStatus() == ITestResult.FAILURE) {
                    String screenshotPath = (String) result.getTestContext().getAttribute("LastScreenshotPath");
                    if (screenshotPath != null && new java.io.File(screenshotPath).exists()) {
                        JiraUtil.attachScreenshot(jiraId, screenshotPath);
                    }
                }
            }
        } catch (Exception e) {
            // Fail silently to not break test suite
            System.err.println("JIRA Link/Comment logging failed: " + e.getMessage());
        }
    }

    /** Clears the custom test context after each test. */
    @AfterMethod
    public void clearContext() {
        TestContextManager.clear();
    }

    // ========== Data Providers ==========

    /** Supplies all test IDs for data-driven test runs. */
    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        return TestDataUtil.getAllTestIDs();
    }

    /** Supplies all synthetic input IDs (if using synthetic data). */
    @DataProvider(name = "syntheticData")
    public Object[][] syntheticData() {
        return SyntheticDataUtil.getAllInputIDs();
    }

    // ========== Utility Methods ==========

    /** Gets the current time for logging/reporting. */
    private String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** Returns human-readable execution duration for the whole suite. */
    private String getExecutionDuration() {
        Duration duration = Duration.between(startTime, Instant.now());
        return String.format("%02d min, %02d sec", duration.toMinutes(), duration.getSeconds() % 60);
    }

    /**
     * Determines correct row index in Excel for result logging (supports both System and E2E tests).
     */
    private int getOrCreateExcelRowIndex(ITestResult result) {
        Object attr = result.getTestContext().getAttribute("ExcelRowIndex");
        if (attr instanceof Integer idx) return idx;

        String sheetName;
        int runIdCol;
        int startRowIndex;

        if (TestTypeUtil.isSystemTestClass(result.getTestClass().getRealClass().getName())) {
            sheetName = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
            runIdCol = ExcelColumnIndex.RUN_ID;
            startRowIndex = 2;
        } else if (TestTypeUtil.isFromE2ETestPackage()) {
            sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");
            runIdCol = com.MyridiusUAF.utils.excel.E2EBindingColumnIndex.RUN_ID;
            startRowIndex = 1;
        } else {
            LogUtil.log(BaseTest.class, "Skipping execution data write: Unknown test type.");
            return -1;
        }

        Sheet sheet = ExcelReaderUtil.getSheet(ConfigReader.getProperty("Test_Data_File_Path"), sheetName);
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRowIndex);

        result.getTestContext().setAttribute("ExcelRowIndex", rowIndex);
        result.getTestContext().setAttribute("TargetSheet", sheet);
        return rowIndex;
    }

    /**
     * Initializes test context for synthetic data-driven tests.
     * Use for hybrid/manual-data+synthetic scenarios.
     */
    protected void initializeTestContext(String testID, String inputID, ITestContext context) {
        executeTestForTestID(testID, context);
        Map<String, String> inputData = SyntheticDataUtil.getInputDataById(inputID);
        TestContextManager.setInputData(inputData);
    }
}
