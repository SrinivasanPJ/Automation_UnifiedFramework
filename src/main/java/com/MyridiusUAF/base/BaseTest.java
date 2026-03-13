package com.MyridiusUAF.base;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.listeners.AiTriageListener;
import com.MyridiusUAF.pages.*;
import com.MyridiusUAF.pages.odoo.pos.*;
import com.MyridiusUAF.utils.annotations.Jira;
import com.MyridiusUAF.utils.context.TestContextManager;
import com.MyridiusUAF.utils.context.TestDataKeys;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.core.ScreenshotUtil;
import com.MyridiusUAF.utils.core.VideoRecorder;
import com.MyridiusUAF.utils.data.ExecutionDataUtil;
import com.MyridiusUAF.utils.data.SyntheticDataUtil;
import com.MyridiusUAF.utils.data.TestDataUtil;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.E2EBindingDao;
import com.MyridiusUAF.utils.db.dao.LoginDataDao;
import com.MyridiusUAF.utils.db.dao.SyntheticDataDao;
import com.MyridiusUAF.utils.db.dao.TransactionalDao;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.*;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Abstract base class for all TestNG test classes in the MyridiusUAF framework.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>WebDriver lifecycle management (initialization and cleanup)</li>
 *   <li>Page Object initialization for Demo Web Shop and Odoo POS applications</li>
 *   <li>Test data loading from Excel or Database</li>
 *   <li>ExtentReport integration for test reporting</li>
 *   <li>Video recording support (per-test or per-run)</li>
 *   <li>JIRA integration for linking test results</li>
 *   <li>AI self-healing status logging</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * public class MyTest extends BaseTest {
 *     @Test
 *     public void testLogin(ITestContext context) {
 *         initializeTestContext("TestID1", "InputID1", context);
 *         performLogin("TestID1");
 *         // Test logic using page objects
 *     }
 * }
 * }</pre>
 *
 * <h2>Data Sources</h2>
 * <ul>
 *   <li><b>Excel Mode:</b> Reads from configured Excel sheets (Login, Synthetic_Data, etc.)</li>
 *   <li><b>DB Mode:</b> Reads from MySQL tables (common_testdata, synthetic_data, etc.)</li>
 * </ul>
 *
 * @see BasePage
 * @see ConfigReader
 * @see DataMode
 */
@Listeners({AiTriageListener.class, JiraListener.class, RecordingRunListener.class})
public abstract class BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BaseTest.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Thread-local test key for video recording identification. */
    private static final ThreadLocal<String> TEST_KEY = new ThreadLocal<>();

    /** De-duplication flag to prevent repeated login source logging. */
    private static final ThreadLocal<Boolean> LOGIN_SOURCE_LOGGED = ThreadLocal.withInitial(() -> false);

    /** De-duplication flag to prevent repeated login user logging. */
    private static final ThreadLocal<Boolean> LOGIN_USER_LOGGED = ThreadLocal.withInitial(() -> false);

    /** Suite start time for duration calculation. */
    private static Instant suiteStart;

    // ═══════════════════════════════════════════════════════════════════════
    // WEBDRIVER
    // ═══════════════════════════════════════════════════════════════════════

    /** WebDriver instance managed by DriverFactory. */
    protected WebDriver driver;

    // ═══════════════════════════════════════════════════════════════════════
    // PAGE OBJECTS - DEMO WEB SHOP
    // ═══════════════════════════════════════════════════════════════════════

    /** Login page for authentication workflows. */
    protected LoginPage loginPage;

    /** Registration page for new user creation. */
    protected RegisterPage registerPage;

    /** Product listing page with category/sort/add-to-cart functionality. */
    protected ProductListPage productListPage;

    /** Cart and checkout page for order placement. */
    protected AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage;

    /** Order confirmation and details page. */
    protected OrderInformationPage orderInformationPage;

    // ═══════════════════════════════════════════════════════════════════════
    // PAGE OBJECTS - ODOO POS
    // ═══════════════════════════════════════════════════════════════════════

    /** Odoo login page. */
    protected OdooLoginPage odooLoginPage;

    /** Odoo app launcher page. */
    protected OdooAppLauncherPage odooAppLauncherPage;

    /** Odoo POS dashboard page. */
    protected OdooPOSDashboardPage odooPOSDashboardPage;

    /** Odoo POS register/cart page. */
    protected OdooPOSRegisterPage odooPOSRegisterPage;

    /** Odoo POS payment page. */
    protected OdooPOSPaymentPage odooPOSPaymentPage;

    /** Odoo POS receipt page. */
    protected OdooPOSReceiptPage odooPOSReceiptPage;

    /** Odoo POS orders history page. */
    protected OdooPOSOrdersPage odooPOSOrdersPage;

    // ═══════════════════════════════════════════════════════════════════════
    // SUITE LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Initializes the test suite.
     * <ul>
     *   <li>Records suite start time</li>
     *   <li>Initializes ExtentReport</li>
     *   <li>Starts run-level video recording if configured</li>
     * </ul>
     */
    @BeforeSuite
    public void suiteSetup() {
        suiteStart = Instant.now();
        LOG.info("Test execution started at: {}", now());
        ExtentReportManager.INSTANCE.initReport();
        startVideoIfPerRun();
    }

    /**
     * Cleans up after test suite completion.
     * <ul>
     *   <li>Stops run-level video recording</li>
     *   <li>Flushes ExtentReport</li>
     *   <li>Sends test result email notification</li>
     *   <li>Logs total execution duration</li>
     * </ul>
     */
    @AfterSuite
    public void suiteTearDown() {
        stopVideoIfPerRun();
        ExtentReportManager.INSTANCE.flushReport();
        EmailSenderUtil.sendTestResultEmail(
                ExtentReportManager.INSTANCE.getTotalTests(),
                ExtentReportManager.INSTANCE.getTestsPassed(),
                ExtentReportManager.INSTANCE.getTestsFailed()
        );
        LOG.info("Test execution ended at: {} | Duration: {}", now(), humanizedDuration());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // METHOD LIFECYCLE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Sets up each test method.
     * <ul>
     *   <li>Creates ExtentReport test node</li>
     *   <li>Initializes thread-local test key</li>
     *   <li>Resets login logging flags</li>
     * </ul>
     *
     * @param method The test method being executed
     */
    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        ExtentReportManager.INSTANCE.createTest(method.getName());
        TEST_KEY.set(getClass().getSimpleName() + "." + method.getName());
        LOGIN_SOURCE_LOGGED.set(false);
        LOGIN_USER_LOGGED.set(false);
        LOG.info("Test setup initialized: {}", method.getName());
    }

    /**
     * Records execution data after each test method.
     * <ul>
     *   <li>Writes results to Excel if applicable</li>
     *   <li>Logs pass/fail/skip to ExtentReport</li>
     *   <li>Updates database status if in DB mode</li>
     *   <li>Captures screenshot on failure</li>
     * </ul>
     *
     * @param result The TestNG test result
     */
    @AfterMethod(alwaysRun = true)
    public void recordExecutionData(ITestResult result) {
        try {
            writeExcelResultIfNeeded(result);
            logResultToExtent(result);
            updateDbStatusIfNeeded(result);
        } catch (Exception e) {
            LOG.error("Error recording execution data.", e);
        } finally {
            ExtentReportManager.INSTANCE.removeTest();
        }
    }

    /**
     * Tears down after each test method.
     * <ul>
     *   <li>Stops video recording for the test</li>
     *   <li>Quits the WebDriver instance</li>
     * </ul>
     */
    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        safeRun(() -> VideoRecorder.INSTANCE.stopIfRunning(TEST_KEY.get()), "Video stop");
        safeRun(DriverFactory::quitDriver, "Driver quit");
    }

    /**
     * Logs JIRA link to ExtentReport if test method has @Jira annotation.
     *
     * @param result The TestNG test result
     */
    @AfterMethod(alwaysRun = true)
    public void logJiraLinkAndComment(ITestResult result) {
        if (Boolean.TRUE.equals(result.getTestContext().getAttribute("JIRA_ADF_POSTED"))) return;
        Method m = result.getMethod().getConstructorOrMethod().getMethod();
        if (!m.isAnnotationPresent(Jira.class)) return;

        String jiraId = m.getAnnotation(Jira.class).value();
        String jiraUrl = ConfigReader.getProperty("jira.url") + "/browse/" + jiraId;
        ExtentReportManager.INSTANCE.logInfo(
                "JIRA Link: <a href=\"" + jiraUrl + "\" target=\"_blank\">" + jiraId + "</a>", getClass());
    }

    /**
     * Clears the test context after each test method.
     */
    @AfterMethod(alwaysRun = true)
    public void clearContext() {
        TestContextManager.clear();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TEST EXECUTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Prepares test execution for a given TestID.
     * <ul>
     *   <li>Loads test data from Excel or Database</li>
     *   <li>Initializes WebDriver and navigates to application URL</li>
     *   <li>Initializes all Page Objects</li>
     *   <li>Logs AI self-healing status</li>
     * </ul>
     *
     * @param testID  The test identifier from data source
     * @param context The TestNG test context
     * @throws IllegalArgumentException if no test data found for TestID
     */
    public void executeTestForTestID(String testID, ITestContext context) {
        LOG.info("Preparing test for TestID: {}", testID);

        Map<String, String> testData = loadTestData(testID, context);
        if (testData == null || testData.isEmpty()) {
            throw new IllegalArgumentException("No test data found for TestID: " + testID);
        }

        initializeDriver(testData, context);
        initializePageObjects();
        logHealingStatus(context);
    }

    /**
     * Initializes test context with both login data and synthetic input data.
     *
     * @param testID  The test identifier for login data
     * @param inputID The input identifier for synthetic data
     * @param context The TestNG test context
     */
    protected void initializeTestContext(String testID, String inputID, ITestContext context) {
        executeTestForTestID(testID, context);
        Map<String, String> inputData = DataMode.isDb()
                ? new SyntheticDataDao().byInputId(inputID)
                : SyntheticDataUtil.getInputDataById(inputID);
        TestContextManager.setInputData(inputData);
    }

    /**
     * Performs login using credentials from test data.
     *
     * @param testID The test identifier to retrieve login credentials
     * @throws RuntimeException if login fails
     */
    protected void performLogin(String testID) {
        Map<String, String> testData = getCachedLoginData(testID);
        logLoginSource(testID);

        String username = testData.get(TestDataKeys.USERNAME);
        String password = testData.get(TestDataKeys.PASSWORD);
        logLoginUser(username, password);

        try {
            loginPage.login(username, password);
        } catch (Exception e) {
            LOG.error("Login failed for user: {}", username, e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA PROVIDERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Provides test data for data-driven tests.
     * Returns all TestIDs from either Excel or Database based on DataMode.
     *
     * @return 2D array of test IDs
     */
    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        return DataMode.isDb()
                ? new LoginDataDao().allTestIdsForDataProvider()
                : TestDataUtil.getAllTestIDs();
    }

    /**
     * Provides synthetic data for data-driven tests.
     * Returns all InputIDs from either Excel or Database based on DataMode.
     *
     * @return 2D array of input IDs
     */
    @DataProvider(name = "syntheticData")
    public Object[][] syntheticData() {
        return DataMode.isDb()
                ? new SyntheticDataDao().allInputIdsForDataProvider()
                : SyntheticDataUtil.getAllInputIDs();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA LOADING HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Loads test data from the configured data source.
     *
     * @param testID  The test identifier
     * @param context The TestNG context for attribute storage
     * @return Map of test data key-value pairs
     */
    private Map<String, String> loadTestData(String testID, ITestContext context) {
        Map<String, String> testData;
        if (DataMode.isDb()) {
            testData = new LoginDataDao().byTestId(testID);
            context.setAttribute("LoginRow", testData);
            seedDbExecutionRow(testData, context);
        } else {
            testData = TestDataUtil.getTestCaseByTestID(testID);
            context.setAttribute("LoginRow", testData);
        }
        return testData;
    }

    /**
     * Seeds the database execution row for tracking test runs.
     * Creates initial "InProgress" status entry in either e2e_binding or transactional_data table.
     *
     * @param testData The test data map
     * @param context  The TestNG context
     */
    private void seedDbExecutionRow(Map<String, String> testData, ITestContext context) {
        boolean isE2E = TestTypeUtil.isFromE2ETestPackage();
        String kind = isE2E ? "E2E" : "SYSTEM";
        context.setAttribute("TEST_KIND", kind);

        try {
            TransactionalDao tdao = new TransactionalDao();
            tdao.ensureAutoIncrementSequential();

            // Generate unique run ID
            String rid = isE2E ? new E2EBindingDao().nextE2ERunId() : tdao.nextRunId(false);
            LOG.info("DB mode: generated RunId={}", rid);
            context.setAttribute("RunId", rid);

            // Set context attributes for later use
            String app = nvl(testData, "Application", "demowebshop");
            String bindingName = getClass().getSimpleName();

            setOrRemove(context, "Application", app);
            setOrRemove(context, "TestType", kind);
            setOrRemove(context, "Functionality", "Order Creation");
            setOrRemove(context, "Scenario", "SC1 -Desktops-Order Creation");
            setOrRemove(context, "BindingTestCaseName", bindingName);
            setOrRemove(context, "ExecutionStatus", "InProgress");
            setOrRemove(context, "FailureReason", null);

            // Insert initial execution row
            if (isE2E) {
                new E2EBindingDao().upsertExecutionRow(app, kind, "Order Creation",
                        "SC1 -Desktops-Order Creation", bindingName, rid, "InProgress", null);
            } else {
                tdao.upsertExecutionRow(app, kind, "Order Creation",
                        "SC1 -Desktops-Order Creation", bindingName, rid, "InProgress", null);
            }
            LOG.info("DB mode: {} seeded for run_id={}", isE2E ? "e2e_binding" : "transactional_data", rid);
        } catch (Exception e) {
            LOG.warn("DB mode: unable to seed exec row: {}", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DRIVER & PAGE OBJECT INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Initializes WebDriver and navigates to application URL.
     *
     * @param testData The test data containing browser and URL
     * @param context  The TestNG context
     */
    private void initializeDriver(Map<String, String> testData, ITestContext context) {
        String browser = testData.getOrDefault(TestDataKeys.BROWSER, "chrome");
        String url = testData.getOrDefault(TestDataKeys.URL, "about:blank");

        context.setAttribute("TestID", testData.get("TestID"));
        context.setAttribute("Browser", browser);

        try {
            DriverFactory.initializeDriver(browser);
            driver = DriverFactory.getDriver();
            driver.get(url);
            ExtentReportManager.INSTANCE.setApplicationUrl(url);
            context.setAttribute("WebDriver", driver);
            context.setAttribute("ApplicationUrl", url);
            LOG.info("Navigated to: {}", url);

            // Start per-test video recording
            safeRun(() -> VideoRecorder.INSTANCE.startOnceForTest(TEST_KEY.get(), driver), "Video start");
        } catch (Exception e) {
            LOG.error("Failed to initialize WebDriver.", e);
            throw new RuntimeException("Driver initialization failed", e);
        }
    }

    /**
     * Initializes all Page Objects for both Demo Web Shop and Odoo POS.
     * Page Objects use PageFactory with optional AI self-healing.
     */
    private void initializePageObjects() {
        // Demo Web Shop Page Objects
        loginPage = new LoginPage();
        registerPage = new RegisterPage();
        productListPage = new ProductListPage();
        addProductsToCartAndPlaceOrderPage = new AddProductsToCartAndPlaceOrderPage();
        orderInformationPage = new OrderInformationPage();

        // Odoo POS Page Objects (require driver parameter)
        odooLoginPage = new OdooLoginPage(driver);
        odooAppLauncherPage = new OdooAppLauncherPage(driver);
        odooPOSDashboardPage = new OdooPOSDashboardPage(driver);
        odooPOSRegisterPage = new OdooPOSRegisterPage(driver);
        odooPOSPaymentPage = new OdooPOSPaymentPage(driver);
        odooPOSReceiptPage = new OdooPOSReceiptPage(driver);
        odooPOSOrdersPage = new OdooPOSOrdersPage(driver);
    }

    /**
     * Logs AI self-healing configuration status.
     *
     * @param context The TestNG context
     */
    private void logHealingStatus(ITestContext context) {
        try {
            boolean healingOn = AiSwitches.selfHealingEnabled();
            boolean llmOn = healingOn && AiSwitches.healingUseLlm() && AiSwitches.openAiGloballyEnabled();
            context.setAttribute("AI_HEALING_ENABLED", healingOn);
            LOG.info("Self-Healing: {} (LLM: {})", healingOn ? "ENABLED" : "DISABLED", llmOn ? "ON" : "OFF");
        } catch (Throwable t) {
            LOG.warn("Healing init skipped: {}", t.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOGIN HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Retrieves cached login data from context or reloads from data source.
     *
     * @param testID The test identifier
     * @return Map of login credentials
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getCachedLoginData(String testID) {
        Map<String, String> testData = (Map<String, String>) Reporter.getCurrentTestResult()
                .getTestContext().getAttribute("LoginRow");
        if (testData == null || testData.isEmpty()) {
            testData = DataMode.isDb()
                    ? new LoginDataDao().byTestId(testID)
                    : TestDataUtil.getTestCaseByTestID(testID);
        }
        return testData;
    }

    /**
     * Logs the login data source (once per test).
     */
    private void logLoginSource(String testID) {
        if (LOGIN_SOURCE_LOGGED.get()) return;
        String source = DataMode.isDb() ? "DATABASE (table=common_testdata)" : "EXCEL (sheet=Login)";
        LOG.info("Login data source = {} test_id={}", source, testID);
        LOGIN_SOURCE_LOGGED.set(true);
    }

    /**
     * Logs the login username (once per test, password masked).
     */
    private void logLoginUser(String username, String password) {
        if (LOGIN_USER_LOGGED.get()) return;
        LOG.info("Login user: username='{}' (password_length={})", username, password == null ? 0 : password.length());
        LOGIN_USER_LOGGED.set(true);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // RESULT RECORDING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Writes test result to Excel if not in DB mode.
     */
    private void writeExcelResultIfNeeded(ITestResult result) {
        int rowIndex = getOrCreateExcelRowIndex(result);
        if (rowIndex >= 0) {
            ExecutionDataUtil.writeExecutionData(rowIndex, result);
            result.getTestContext().setAttribute("ExecDataWritten", true);
        }
    }

    /**
     * Logs test result to ExtentReport with screenshot on failure.
     */
    private void logResultToExtent(ITestResult result) {
        switch (result.getStatus()) {
            case ITestResult.SUCCESS -> ExtentReportManager.INSTANCE.logPass("Test Passed: " + result.getName());
            case ITestResult.FAILURE -> {
                ExtentReportManager.INSTANCE.logFail("Test Failed: " + result.getName(), result.getThrowable());
                String screenshot = ScreenshotUtil.saveScreenshotAsPNG(driver, result.getName());
                ExtentReportManager.INSTANCE.attachScreenshotFromPath(screenshot, "Failure Screenshot");
                result.getTestContext().setAttribute("LastScreenshotPath", screenshot);
                LOG.error("Failure captured.", result.getThrowable());
            }
            case ITestResult.SKIP -> ExtentReportManager.INSTANCE.logSkip("Test Skipped: " + result.getName());
        }
    }

    /**
     * Updates test status in database (e2e_binding or transactional_data).
     */
    private void updateDbStatusIfNeeded(ITestResult result) {
        if (!DataMode.isDb()) return;

        String runId = (String) result.getTestContext().getAttribute("RunId");
        if (runId == null || runId.isBlank()) {
            LOG.warn("DB mode: RunId missing; cannot update final status.");
            return;
        }

        // Map TestNG status to DB status
        String status = switch (result.getStatus()) {
            case ITestResult.SUCCESS -> "Pass";
            case ITestResult.FAILURE -> "Fail";
            case ITestResult.SKIP -> "Skip";
            default -> null;
        };
        if (status == null) return;

        // Extract failure reason if applicable
        String failureReason = result.getThrowable() == null ? null
                : result.getThrowable().getClass().getSimpleName() + ": " + result.getThrowable().getMessage();

        String bindingName = (String) result.getTestContext().getAttribute("BindingTestCaseName");
        if (bindingName == null || bindingName.isBlank()) {
            bindingName = result.getTestClass().getRealClass().getSimpleName();
        }

        // Update appropriate table based on test type
        boolean isE2E = "E2E".equals(result.getTestContext().getAttribute("TEST_KIND"));
        try {
            if (isE2E) {
                new E2EBindingDao().updateStatusForRun(runId, bindingName, status, failureReason);
            } else {
                new TransactionalDao().updateStatusForRun(runId, status, failureReason);
            }
            LOG.info("DB mode: {} updated for run_id={} (status={})",
                    isE2E ? "e2e_binding" : "transactional_data", runId, status);
        } catch (Exception e) {
            LOG.warn("DB mode: failed to update status: {}", e.getMessage());
        }

        setOrRemove(result.getTestContext(), "ExecutionStatus", status);
        setOrRemove(result.getTestContext(), "FailureReason", failureReason);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // EXCEL ROW MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Gets or creates Excel row index for writing execution results.
     *
     * @param result The TestNG test result
     * @return Row index, or -1 if not applicable
     */
    private int getOrCreateExcelRowIndex(ITestResult result) {
        if (DataMode.isDb()) return -1;

        Object cached = result.getTestContext().getAttribute("ExcelRowIndex");
        if (cached instanceof Integer idx) return idx;

        boolean isSystem = TestTypeUtil.isSystem(result);
        boolean isE2E = TestTypeUtil.isE2E(result);

        if (!isSystem && !isE2E) {
            LogUtil.warn(BaseTest.class, "Unknown test type; skipping Excel row resolution.");
            return -1;
        }

        // Determine sheet configuration based on test type
        String sheetName = isSystem
                ? ConfigReader.getProperty("Transactional_Data_Sheet_Name")
                : ConfigReader.getProperty("End_To_End_Sheet_Name");
        int runIdCol = isSystem ? ExcelColumnIndex.RUN_ID : E2EBindingColumnIndex.RUN_ID;
        int startRow = isSystem ? 2 : 1;

        String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.warn(BaseTest.class, "Sheet not found: " + sheetName);
                return -1;
            }

            int rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRow);
            result.getTestContext().setAttribute("ExcelRowIndex", rowIndex);
            result.getTestContext().setAttribute("ExcelTargetSheetName", sheetName);
            return rowIndex;
        } catch (IOException e) {
            throw new RuntimeException("Failed to get Excel row index", e);
        }
    }

    /**
     * Pre-allocates Excel row for E2E tests if needed.
     *
     * @param context The TestNG context
     */
    protected void allocateE2EExcelRowIfNeeded(ITestContext context) {
        if (DataMode.isDb()) return;

        String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        String sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet e2e = wb.getSheet(sheetName);
            if (e2e == null) throw new IllegalStateException("E2E sheet not found: " + sheetName);
            int rowIndex = ExcelUtil.findNextAvailableRow(e2e, E2EBindingColumnIndex.RUN_ID, 1);
            context.setAttribute("ExcelRowIndex", rowIndex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate Excel row", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // VIDEO RECORDING HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Starts run-level video recording if configured as "per_run".
     */
    private void startVideoIfPerRun() {
        if ("per_run".equalsIgnoreCase(ConfigReader.getProperty("recording.mode", "per_test"))) {
            safeRun(() -> VideoRecorder.INSTANCE.startRunIfNeeded(), "Run-level video start");
        }
    }

    /**
     * Stops run-level video recording if configured as "per_run".
     */
    private void stopVideoIfPerRun() {
        if ("per_run".equalsIgnoreCase(ConfigReader.getProperty("recording.mode", "per_test"))) {
            safeRun(() -> VideoRecorder.INSTANCE.stopRunIfRunning(), "Run-level video stop");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Executes an action safely, logging any exceptions without propagating.
     *
     * @param action      The action to execute
     * @param description Description for logging
     */
    private void safeRun(Runnable action, String description) {
        try {
            action.run();
        } catch (Exception e) {
            LOG.warn("{} skipped: {}", description, e.getMessage());
        }
    }

    /**
     * Returns current timestamp formatted as "yyyy-MM-dd HH:mm:ss".
     */
    private String now() {
        return LocalDateTime.now().format(TS);
    }

    /**
     * Returns human-readable duration since suite start.
     */
    private String humanizedDuration() {
        Duration d = Duration.between(suiteStart, Instant.now());
        return String.format("%02d min, %02d sec", d.toMinutes(), d.getSeconds() % 60);
    }

    /**
     * Null-safe map lookup with default value.
     *
     * @param m   The map to lookup
     * @param key The key to find
     * @param def Default value if key is missing or blank
     * @return The value or default
     */
    private static String nvl(Map<String, String> m, String key, String def) {
        String v = m == null ? null : m.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    /**
     * Sets a context attribute, or removes it if value is null.
     * Handles ConcurrentHashMap null-value restriction.
     *
     * @param ctx   The TestNG context
     * @param key   The attribute key
     * @param value The value (null to remove)
     */
    protected static void setOrRemove(ITestContext ctx, String key, Object value) {
        if (value != null) ctx.setAttribute(key, value);
        else ctx.removeAttribute(key);
    }
}
