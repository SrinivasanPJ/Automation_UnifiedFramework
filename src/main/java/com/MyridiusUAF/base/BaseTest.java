package com.MyridiusUAF.base;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.pages.AddProductsToCartAndPlaceOrderPage;
import com.MyridiusUAF.pages.LoginPage;
import com.MyridiusUAF.pages.OrderInformationPage;
import com.MyridiusUAF.pages.RegisterPage;
import com.MyridiusUAF.utils.annotations.Jira;
import com.MyridiusUAF.utils.context.TestContextManager;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.core.ScreenshotUtil;
import com.MyridiusUAF.utils.data.ExecutionDataUtil;
import com.MyridiusUAF.utils.data.SyntheticDataUtil;

import com.MyridiusUAF.utils.context.TestDataKeys;
import com.MyridiusUAF.utils.data.TestDataUtil;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.EmailSenderUtil;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import com.MyridiusUAF.utils.reporting.JiraListener;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
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
 * Base class for all TestNG UI tests.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>WebDriver lifecycle management</li>
 *   <li>Data-driven test setup</li>
 *   <li>Extent report wiring</li>
 *   <li>Failure handling & screenshot capture</li>
 *   <li>Test context management</li>
 * </ul>
 */
@Listeners(JiraListener.class)
public abstract class BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseTest.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static Instant suiteStart;

    // Page objects (initialized per test)
    protected WebDriver driver;
    protected LoginPage loginPage;
    protected AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage;
    protected OrderInformationPage orderInformationPage;
    protected RegisterPage registerPage;

    // ---------- Suite lifecycle ----------

    /** Initializes reporting and logs the suite start. */
    @BeforeSuite
    public void suiteSetup() {
        suiteStart = Instant.now();
        LOGGER.info("Test execution started at: {}", now());
        ExtentReportManager.INSTANCE.initReport();
    }

    /** Flushes reporting and sends summary mail at suite end. */
    @AfterSuite
    public void suiteTearDown() {
        ExtentReportManager.INSTANCE.flushReport();
        EmailSenderUtil.sendTestResultEmail(
                ExtentReportManager.INSTANCE.getTotalTests(),
                ExtentReportManager.INSTANCE.getTestsPassed(),
                ExtentReportManager.INSTANCE.getTestsFailed()
        );
        LOGGER.info("Test execution ended at: {}", now());
        LOGGER.info("Total execution time: {}", humanizedDuration());
    }

    // ---------- Per-test lifecycle ----------

    /** Creates an Extent node for each test method. */
    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        ExtentReportManager.INSTANCE.createTest(method.getName());
        LOGGER.info("Test setup initialized for method: {}", method.getName());
    }

    /**
     * Initializes driver, navigates to URL, and instantiates page objects based on the provided test ID.
     *
     * @param testID  ID that maps to the data source row
     * @param context TestNG context (stores runtime attributes)
     */
    // @Test(dataProvider = "testData")
    public void executeTestForTestID(String testID, ITestContext context) {
        LOGGER.info("Preparing to execute test for TestID: {}", testID);

        Map<String, String> testData = TestDataUtil.getTestCaseByTestID(testID);
        if (testData.isEmpty()) {
            throw new IllegalArgumentException("No test data found for TestID: " + testID);
        }

        String browser = testData.getOrDefault(TestDataKeys.BROWSER, "chrome");
        String url     = testData.getOrDefault(TestDataKeys.URL, "about:blank");

        context.setAttribute("TestID", testID);
        context.setAttribute("Browser", browser);

        try {
            DriverFactory.initializeDriver(browser);
            driver = DriverFactory.getDriver();
            driver.get(url);

            // make WebDriver available to listeners/utilities
            context.setAttribute("WebDriver", driver);
            context.setAttribute("ApplicationUrl", url);

            LOGGER.info("Navigated to: {}", url);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize WebDriver.", e);
            throw new RuntimeException("Driver initialization failed", e);
        }

        // Page objects (created after driver is ready)
        loginPage = new LoginPage();
        addProductsToCartAndPlaceOrderPage = new AddProductsToCartAndPlaceOrderPage();
        orderInformationPage = new OrderInformationPage();
        registerPage = new RegisterPage();
    }

    /**
     * Convenience login using the current test data row mapped by {@code testID}.
     *
     * @param testID data row ID
     */
    protected void performLogin(String testID) {
        Map<String, String> testData = TestDataUtil.getTestCaseByTestID(testID);
        String username = testData.get(TestDataKeys.USERNAME);
        String password = testData.get(TestDataKeys.PASSWORD);

        try {
            loginPage.login(username, password);
        } catch (Exception e) {
            LOGGER.error("Login failed for user: {}", username, e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // ---------- Reporting & cleanup ----------

    /**
     * Records results to Excel and Extent, captures screenshot on failure,
     * and exposes artifacts to listeners via the test context.
     */
    @AfterMethod(alwaysRun = true)
    public void recordExecutionData(ITestResult result) {
        try {
            int rowIndex = getOrCreateExcelRowIndex(result);
            if (rowIndex >= 0) {
                ExecutionDataUtil.writeExecutionData(rowIndex, result);
                // Key checked by JiraListener to avoid duplicate work
                result.getTestContext().setAttribute("ExecDataWritten", true);
            }

            switch (result.getStatus()) {
                case ITestResult.SUCCESS -> ExtentReportManager.INSTANCE
                        .logPass("Test Passed: " + result.getName());
                case ITestResult.FAILURE -> {
                    ExtentReportManager.INSTANCE
                            .logFail("Test Failed: " + result.getName(), result.getThrowable());
                    String screenshot = ScreenshotUtil.saveScreenshotAsPNG(driver, result.getName());
                    ExtentReportManager.INSTANCE.attachScreenshotFromPath(screenshot, "Failure Screenshot");
                    result.getTestContext().setAttribute("LastScreenshotPath", screenshot);
                    LOGGER.error("Failure details captured.", result.getThrowable());
                }
                case ITestResult.SKIP -> ExtentReportManager.INSTANCE
                        .logSkip("Test Skipped: " + result.getName());
                default -> { /* no-op */ }
            }
        } catch (Exception e) {
            LOGGER.error("Error during execution result recording.", e);
        } finally {
            ExtentReportManager.INSTANCE.removeTest();
        }
    }

    /** Disposes the WebDriver after each test. */
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            DriverFactory.quitDriver();
        } catch (Exception e) {
            LOGGER.error("Error occurred while quitting WebDriver.", e);
        }
    }

    /**
     * Adds a JIRA link to the Extent report when {@link Jira} is present on the method.
     * Avoids reposting when the listener already created an ADF comment.
     */
    @AfterMethod(alwaysRun = true)
    public void logJiraLinkAndComment(ITestResult result) {
        try {
            if (Boolean.TRUE.equals(result.getTestContext().getAttribute("JIRA_ADF_POSTED"))) return;

            Method m = result.getMethod().getConstructorOrMethod().getMethod();
            if (!m.isAnnotationPresent(Jira.class)) return;

            String jiraId  = m.getAnnotation(Jira.class).value();
            String jiraUrl = ConfigReader.getProperty("jira.url") + "/browse/" + jiraId;

            ExtentReportManager.INSTANCE
                    .logInfo("JIRA Link: <a href=\"" + jiraUrl + "\" target=\"_blank\">" + jiraId + "</a>", getClass());
        } catch (Exception e) {
            LOGGER.error("JIRA link logging failed", e);
        }
    }

    /** Clears custom test context state. */
    @AfterMethod(alwaysRun = true)
    public void clearContext() {
        TestContextManager.clear();
    }

    // ---------- Data providers ----------

    /** Provides all test IDs for data-driven runs. */
    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        return TestDataUtil.getAllTestIDs();
    }

    /** Provides synthetic input IDs (if using synthetic inputs). */
    @DataProvider(name = "syntheticData")
    public Object[][] syntheticData() {
        return SyntheticDataUtil.getAllInputIDs();
    }

    // ---------- Helpers ----------

    private String now() {
        return LocalDateTime.now().format(TS);
    }

    private String humanizedDuration() {
        Duration d = Duration.between(suiteStart, Instant.now());
        return String.format("%02d min, %02d sec", d.toMinutes(), d.getSeconds() % 60);
    }

    /**
     * Resolves the next available Excel row index for the current test type and caches it in the context.
     *
     * @return row index to write, or -1 when the test type is unknown
     */
    private int getOrCreateExcelRowIndex(ITestResult result) {
        Object cached = result.getTestContext().getAttribute("ExcelRowIndex");
        if (cached instanceof Integer idx) return idx;

        final boolean isSystem = TestTypeUtil.isSystem(result);
        final boolean isE2E    = TestTypeUtil.isE2E(result);

        final String sheetName;
        final int runIdCol;
        final int startRow;

        if (isSystem) {
            sheetName = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
            runIdCol  = ExcelColumnIndex.RUN_ID;
            startRow  = 2;
        } else if (isE2E) {
            sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");
            runIdCol  = E2EBindingColumnIndex.RUN_ID;
            startRow  = 1;
        } else {
            LogUtil.warn(BaseTest.class, "Unknown test type; skipping exec row index resolution.");
            return -1;
        }

        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                sheetName
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRow); // always append

        result.getTestContext().setAttribute("ExcelRowIndex", rowIndex);
        result.getTestContext().setAttribute("TargetSheet", sheet);
        return rowIndex;
    }

    /**
     * Hybrid initializer for synthetic data scenarios.
     * Bootstraps driver via {@link #executeTestForTestID(String, ITestContext)} and
     * injects synthetic input data into the shared test context.
     */
    protected void initializeTestContext(String testID, String inputID, ITestContext context) {
        executeTestForTestID(testID, context);
        Map<String, String> inputData = SyntheticDataUtil.getInputDataById(inputID);
        TestContextManager.setInputData(inputData);
    }
}
