package com.MyridiusUAF.base;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.listeners.AiTriageListener;
import com.MyridiusUAF.pages.AddProductsToCartAndPlaceOrderPage;
import com.MyridiusUAF.pages.LoginPage;
import com.MyridiusUAF.pages.OrderInformationPage;
import com.MyridiusUAF.pages.RegisterPage;
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

@Listeners({
        AiTriageListener.class,
        JiraListener.class,
        RecordingRunListener.class
})
public abstract class BaseTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseTest.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ThreadLocal<String> TEST_KEY = new ThreadLocal<>();
    // de-dup guards
    private static final ThreadLocal<Boolean> LOGIN_SOURCE_LOGGED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> LOGIN_USER_LOGGED   = ThreadLocal.withInitial(() -> false);

    private static Instant suiteStart;

    // Driver
    protected WebDriver driver;

    // Existing sample app POs
    protected LoginPage loginPage;
    protected AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage;
    protected OrderInformationPage orderInformationPage;
    protected RegisterPage registerPage;

    // ==== Odoo POS POs (moved here) ====
    protected OdooLoginPage odooLoginPage;
    protected OdooAppLauncherPage odooAppLauncherPage;
    protected OdooPOSDashboardPage odooPOSDashboardPage;
    protected OdooPOSRegisterPage odooPOSRegisterPage;
    protected OdooPOSPaymentPage odooPOSPaymentPage;
    protected OdooPOSReceiptPage odooPOSReceiptPage;
    protected OdooPOSOrdersPage odooPOSOrdersPage;

    @BeforeSuite
    public void suiteSetup() {
        suiteStart = Instant.now();
        LOGGER.info("Test execution started at: {}", now());
        ExtentReportManager.INSTANCE.initReport();

        if ("per_run".equalsIgnoreCase(ConfigReader.getProperty("recording.mode", "per_test"))) {
            try {
                VideoRecorder.INSTANCE.startRunIfNeeded();
            } catch (Exception e) {
                LOGGER.warn("Run-level video start skipped: {}", e.getMessage());
            }
        }
    }

    @AfterSuite
    public void suiteTearDown() {
        if ("per_run".equalsIgnoreCase(ConfigReader.getProperty("recording.mode", "per_test"))) {
            try {
                VideoRecorder.INSTANCE.stopRunIfRunning();
            } catch (Exception e) {
                LOGGER.warn("Run-level video stop skipped: {}", e.getMessage());
            }
        }

        ExtentReportManager.INSTANCE.flushReport();
        EmailSenderUtil.sendTestResultEmail(
                ExtentReportManager.INSTANCE.getTotalTests(),
                ExtentReportManager.INSTANCE.getTestsPassed(),
                ExtentReportManager.INSTANCE.getTestsFailed()
        );
        LOGGER.info("Test execution ended at: {}", now());
        LOGGER.info("Total execution time: {}", humanizedDuration());
    }

    @BeforeMethod(alwaysRun = true)
    public void setUp(Method method) {
        ExtentReportManager.INSTANCE.createTest(method.getName());
        TEST_KEY.set(getClass().getSimpleName() + "." + method.getName());
        // reset de-dupe flags
        LOGIN_SOURCE_LOGGED.set(false);
        LOGIN_USER_LOGGED.set(false);
        LOGGER.info("Test setup initialized for method: {}", method.getName());
    }

    public void executeTestForTestID(String testID, ITestContext context) {
        LOGGER.info("Preparing to execute test for TestID: {}", testID);

        // ---- get test row either from DB or Excel ----
        // ---- get test row either from DB or Excel ----
        Map<String, String> testData;
        if (DataMode.isDb()) {
            testData = new LoginDataDao().byTestId(testID);
            context.setAttribute("LoginRow", testData);

            // Decide kind ONCE and persist it
            final boolean isE2E = TestTypeUtil.isFromE2ETestPackage();
            final String  kind  = isE2E ? "E2E" : "SYSTEM";
            context.setAttribute("TEST_KIND", kind);

            try {
                var tdao = new TransactionalDao();
                tdao.ensureAutoIncrementSequential();

                // Generate the right run id
                final String rid = isE2E
                        ? new E2EBindingDao().nextE2ERunId()  // e.g. E2E29
                        : tdao.nextRunId(false);              // e.g. R265

                LOGGER.info("DB mode: generated RunId={}", rid);
                context.setAttribute("RunId", rid);

                // Common, null-safe context hints
                final String app           = nvl(testData, "Application", "demowebshop");
                final String functionality = "Order Creation";
                final String scenario      = "SC1 -Desktops-Order Creation";
                final String bindingName   = getClass().getSimpleName();
                final String status        = "InProgress";

                setOrRemove(context, "Application",         app);
                setOrRemove(context, "TestType",            kind);      // "E2E" or "SYSTEM"
                setOrRemove(context, "Functionality",       functionality);
                setOrRemove(context, "Scenario",            scenario);
                setOrRemove(context, "BindingTestCaseName", bindingName);
                setOrRemove(context, "ExecutionStatus",     status);
                setOrRemove(context, "FailureReason",       null);

                // Seed ONLY the relevant table
                if (isE2E) {
                    new E2EBindingDao().upsertExecutionRow(
                            app, kind, functionality, scenario, bindingName, rid, status, null);
                    LOGGER.info("DB mode: e2e_binding seeded for run_id={}", rid);
                } else {
                    tdao.upsertExecutionRow(
                            app, kind, functionality, scenario, bindingName, rid, status, null);
                    LOGGER.info("DB mode: transactional_data seeded for run_id={}", rid);
                }
            } catch (Exception seedingErr) {
                LOGGER.warn("DB mode: unable to seed exec row: ", seedingErr);
            }
        } else {
            testData = TestDataUtil.getTestCaseByTestID(testID);
            context.setAttribute("LoginRow", testData);
        }

        if (testData == null || testData.isEmpty()) {
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
            ExtentReportManager.INSTANCE.setApplicationUrl(url);
            context.setAttribute("WebDriver", driver);
            context.setAttribute("ApplicationUrl", url);
            LOGGER.info("Navigated to: {}", url);

            try { VideoRecorder.INSTANCE.startOnceForTest(TEST_KEY.get(), driver); }
            catch (Exception e) { LOGGER.warn("Video start skipped: {}", e.getMessage()); }
        } catch (Exception e) {
            LOGGER.error("Failed to initialize WebDriver.", e);
            throw new RuntimeException("Driver initialization failed", e);
        }

        // POS…
        loginPage = new LoginPage();
        addProductsToCartAndPlaceOrderPage = new AddProductsToCartAndPlaceOrderPage();
        orderInformationPage = new OrderInformationPage();
        registerPage = new RegisterPage();

        // Odoo POs…
        odooLoginPage        = new OdooLoginPage(driver);
        odooAppLauncherPage  = new OdooAppLauncherPage(driver);
        odooPOSDashboardPage = new OdooPOSDashboardPage(driver);
        odooPOSRegisterPage  = new OdooPOSRegisterPage(driver);
        odooPOSPaymentPage   = new OdooPOSPaymentPage(driver);
        odooPOSReceiptPage   = new OdooPOSReceiptPage(driver);
        odooPOSOrdersPage    = new OdooPOSOrdersPage(driver);

        try {
            boolean healingOn = AiSwitches.selfHealingEnabled();
            boolean llmOn = healingOn && AiSwitches.healingUseLlm() && AiSwitches.openAiGloballyEnabled();
            context.setAttribute("AI_HEALING_ENABLED", healingOn);
            LOGGER.info("Self-Healing: {} (LLM: {})", healingOn ? "ENABLED" : "DISABLED", llmOn ? "ON" : "OFF");
        } catch (Throwable t) {
            LOGGER.warn("Healing init skipped (non-fatal): {}", t.toString());
        }
    }

    @AfterMethod(alwaysRun = true)
    public void recordExecutionData(ITestResult result) {
        try {
            int rowIndex = getOrCreateExcelRowIndex(result);
            if (rowIndex >= 0) {
                ExecutionDataUtil.writeExecutionData(rowIndex, result);
                result.getTestContext().setAttribute("ExecDataWritten", true);
            }

            switch (result.getStatus()) {
                case ITestResult.SUCCESS -> ExtentReportManager.INSTANCE.logPass("Test Passed: " + result.getName());
                case ITestResult.FAILURE -> {
                    ExtentReportManager.INSTANCE.logFail("Test Failed: " + result.getName(), result.getThrowable());
                    String screenshot = ScreenshotUtil.saveScreenshotAsPNG(driver, result.getName());
                    ExtentReportManager.INSTANCE.attachScreenshotFromPath(screenshot, "Failure Screenshot");
                    result.getTestContext().setAttribute("LastScreenshotPath", screenshot);
                    LOGGER.error("Failure details captured.", result.getThrowable());
                }
                case ITestResult.SKIP -> ExtentReportManager.INSTANCE.logSkip("Test Skipped: " + result.getName());
                default -> { /* no-op */ }
            }

            // ---- DB: write final status to the correct table only (null-safe context) ----
            if (DataMode.isDb()) {
                try {
                    String runId = (String) result.getTestContext().getAttribute("RunId");
                    if (runId == null || runId.isBlank()) {
                        LOGGER.warn("DB mode: RunId missing; cannot update final status.");
                    } else {
                        String status = switch (result.getStatus()) {
                            case ITestResult.SUCCESS -> "Pass";
                            case ITestResult.FAILURE -> "Fail";
                            case ITestResult.SKIP   -> "Skip";
                            default -> null;
                        };

                        String failureReason = (result.getThrowable() == null)
                                ? null
                                : result.getThrowable().getClass().getSimpleName() + ": " + result.getThrowable().getMessage();

                        String bindingName = (String) result.getTestContext().getAttribute("BindingTestCaseName");
                        if (bindingName == null || bindingName.isBlank()) {
                            bindingName = result.getTestClass().getRealClass().getSimpleName();
                        }

                        boolean isE2E = "E2E".equals(result.getTestContext().getAttribute("TEST_KIND"));
                        if (isE2E) {
                            if (status != null) {
                                new E2EBindingDao().updateStatusForRun(runId, bindingName, status, failureReason);
                                LOGGER.info("DB mode: e2e_binding updated for run_id={} binding={} (status={})",
                                        runId, bindingName, status);
                            }
                        } else {
                            if (status != null) {
                                new TransactionalDao().updateStatusForRun(runId, status, failureReason);
                                LOGGER.info("DB mode: transactional_data updated for run_id={} (status={})",
                                        runId, status);
                            }
                        }

                        // keep context aligned (ConcurrentHashMap can't store nulls)
                        setOrRemove(result.getTestContext(), "ExecutionStatus", status);
                        setOrRemove(result.getTestContext(), "FailureReason",  failureReason);
                    }
                } catch (Exception e) {
                    LOGGER.warn("DB mode: failed to update final statuses for run: ", e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error during execution result recording.", e);
        } finally {
            ExtentReportManager.INSTANCE.removeTest();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {
        try {
            try {
                VideoRecorder.INSTANCE.stopIfRunning(TEST_KEY.get());
            } catch (Exception e) {
                LOGGER.warn("Video stop skipped: {}", e.getMessage());
            }
            DriverFactory.quitDriver();
        } catch (Exception e) {
            LOGGER.error("Error occurred while quitting WebDriver.", e);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void logJiraLinkAndComment(ITestResult result) {
        try {
            if (Boolean.TRUE.equals(result.getTestContext().getAttribute("JIRA_ADF_POSTED"))) return;

            Method m = result.getMethod().getConstructorOrMethod().getMethod();
            if (!m.isAnnotationPresent(Jira.class)) return;

            String jiraId = m.getAnnotation(Jira.class).value();
            String jiraUrl = ConfigReader.getProperty("jira.url") + "/browse/" + jiraId;

            ExtentReportManager.INSTANCE
                    .logInfo("JIRA Link: <a href=\"" + jiraUrl + "\" target=\"_blank\">" + jiraId + "</a>", getClass());
        } catch (Exception e) {
            LOGGER.error("JIRA link logging failed", e);
        }
    }

    @AfterMethod(alwaysRun = true)
    public void clearContext() {
        TestContextManager.clear();
    }

    @DataProvider(name = "testData")
    public Object[][] getTestData() {
        return DataMode.isDb()
                ? new LoginDataDao().allTestIdsForDataProvider()
                : TestDataUtil.getAllTestIDs();
    }

    @DataProvider(name = "syntheticData")
    public Object[][] syntheticData() {
        return DataMode.isDb()
                ? new SyntheticDataDao().allInputIdsForDataProvider()
                : SyntheticDataUtil.getAllInputIDs();
    }

    private String now() {
        return LocalDateTime.now().format(TS);
    }

    private String humanizedDuration() {
        Duration d = Duration.between(suiteStart, Instant.now());
        return String.format("%02d min, %02d sec", d.toMinutes(), d.getSeconds() % 60);
    }

    private int getOrCreateExcelRowIndex(ITestResult result) {
        if (DataMode.isDb()) {
            return -1; // DB backend → do not allocate / write Excel rows
        }
        Object cached = result.getTestContext().getAttribute("ExcelRowIndex");
        if (cached instanceof Integer idx) return idx;

        final boolean isSystem = TestTypeUtil.isSystem(result);
        final boolean isE2E = TestTypeUtil.isE2E(result);

        final String sheetName;
        final int runIdCol;
        final int startRow;

        if (isSystem) {
            sheetName = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
            runIdCol = ExcelColumnIndex.RUN_ID;
            startRow = 2;
        } else if (isE2E) {
            sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");
            runIdCol = E2EBindingColumnIndex.RUN_ID;
            startRow = 1;
        } else {
            LogUtil.warn(BaseTest.class, "Unknown test type; skipping exec row index resolution.");
            return -1;
        }

        // Open workbook just to compute the next free row
        final String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        int rowIndex;

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.warn(BaseTest.class, "Sheet not found: " + sheetName + " in " + filePath);
                return -1;
            }

            rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRow);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        result.getTestContext().setAttribute("ExcelRowIndex", rowIndex);
        result.getTestContext().setAttribute("ExcelTargetSheetName", sheetName);
        return rowIndex;
    }

    protected void initializeTestContext(String testID, String inputID, ITestContext context) {
        executeTestForTestID(testID, context);

        Map<String, String> inputData = DataMode.isDb()
                ? new SyntheticDataDao().byInputId(inputID)   // synthetic_data by input_id
                : SyntheticDataUtil.getInputDataById(inputID); // Excel “Synthetic_Data”
        TestContextManager.setInputData(inputData);
    }

    protected void performLogin(String testID) {
        final boolean fromDb = DataMode.isDb();

        // Prefer the cached login row from context to avoid a second DB/Excel read
        @SuppressWarnings("unchecked")
        Map<String, String> testData =
                (Map<String, String>) Reporter.getCurrentTestResult()
                        .getTestContext()
                        .getAttribute("LoginRow");

        if (testData == null || testData.isEmpty()) {
            // Fallback (should rarely occur)
            testData = fromDb
                    ? new LoginDataDao().byTestId(testID)
                    : TestDataUtil.getTestCaseByTestID(testID);
        }

        // Source telemetry (once per test)
        if (fromDb) {
            if (!LOGIN_SOURCE_LOGGED.get()) {
                LOGGER.info("Login data source = DATABASE (table=common_testdata, test_id={})", testID);
                LOGIN_SOURCE_LOGGED.set(true);
            } else {
                LOGGER.debug("Login data source = DATABASE (test_id={}) [duplicate suppressed]", testID);
            }
        } else {
            if (!LOGIN_SOURCE_LOGGED.get()) {
                LOGGER.info("Login data source = EXCEL (sheet=Login, test_id={})", testID);
                LOGIN_SOURCE_LOGGED.set(true);
            } else {
                LOGGER.debug("Login data source = EXCEL (test_id={}) [duplicate suppressed]", testID);
            }
        }

        String username = testData.get(TestDataKeys.USERNAME);
        String password = testData.get(TestDataKeys.PASSWORD);

        if (!LOGIN_USER_LOGGED.get()) {
            LOGGER.info("Login user resolved: username='{}' (password_length={})",
                    username, (password == null ? 0 : password.length()));
            LOGIN_USER_LOGGED.set(true);
        } else {
            LOGGER.debug("Login user resolved: username='{}' (password_length={}) [duplicate suppressed]",
                    username, (password == null ? 0 : password.length()));
        }

        try {
            loginPage.login(username, password);
        } catch (Exception e) {
            LOGGER.error("Login failed for user: {}", username, e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // --------- helpers ---------

    // Null-safe NVL for Map lookups (treats missing OR null/blank as default)
    private static String nvl(Map<String, String> m, String key, String def) {
        String v = (m == null) ? null : m.get(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    // Null-safe context writer (ConcurrentHashMap can't store nulls)
    protected static void setOrRemove(final ITestContext ctx, final String key, final Object value) {
        if (value != null) ctx.setAttribute(key, value);
        else ctx.removeAttribute(key);
    }

    private boolean isE2E() {
        return TestTypeUtil.isE2ETestClass(getClass().getName());
    }

    // BaseTest.java
    protected void allocateE2EExcelRowIfNeeded(ITestContext context) {
        if (DataMode.isDb()) return;
        final String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        final String sheet    = ConfigReader.getProperty("End_To_End_Sheet_Name");
        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {
            Sheet e2e = wb.getSheet(sheet);
            if (e2e == null) throw new IllegalStateException("E2E sheet not found: " + sheet);
            int rowIndex = ExcelUtil.findNextAvailableRow(e2e, E2EBindingColumnIndex.RUN_ID, 1);
            context.setAttribute("ExcelRowIndex", rowIndex);
        } catch (Exception e) {
            throw new RuntimeException("Failed to allocate Excel row", e);
        }
    }
}
