package com.AutoPOC.utils.reporting;

import com.AutoPOC.config.ConfigReader;
import com.aventstack.extentreports.*;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.aventstack.extentreports.reporter.configuration.ViewName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Singleton class (Enum based) managing ExtentReports lifecycle,
 * logging test outcomes, attaching screenshots, and configuring system info
 * for enterprise-level automation reporting.
 */
public enum ExtentReportManager {

    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(ExtentReportManager.class);

    private static final ThreadLocal<ExtentTest> extentTest = new ThreadLocal<>();
    private ExtentReports extent;
    private String reportPath;

    private int totalTests = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;

    /** -------------------------------------------------
     *  Initialization Methods
     *  ------------------------------------------------- */

    /**
     * Initializes ExtentReports with Spark reporter configuration.
     * Ensures the report file is dynamically timestamped.
     */
    public synchronized void initReport() {
        if (extent == null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            this.reportPath = "reports/ExecutionReport_" + timestamp + ".html";

            ExtentSparkReporter sparkReporter = new ExtentSparkReporter(reportPath);
            configureSparkReporter(sparkReporter);

            extent = new ExtentReports();
            extent.attachReporter(sparkReporter);

            setSystemInfo();
            logger.info("ExtentReport initialized: {}", reportPath);
        }
    }

    private void configureSparkReporter(ExtentSparkReporter spark) {
        spark.config().setReportName("Automation Execution Report");
        spark.config().setDocumentTitle("Execution Summary");
        spark.config().setTheme(Theme.STANDARD);
        spark.config().setTimelineEnabled(true);

        spark.config().setJs("""
            document.addEventListener("DOMContentLoaded", function() {
                const firstFailure = document.querySelector('.fail');
                if (firstFailure) {
                    firstFailure.scrollIntoView({ behavior: 'smooth', block: 'center' });
                }
            });
        """);

        spark.viewConfigurer().viewOrder()
                .as(new ViewName[]{
                        ViewName.DASHBOARD,
                        ViewName.TEST,
                        ViewName.CATEGORY,
                        ViewName.AUTHOR,
                        ViewName.EXCEPTION,
                        ViewName.LOG
                });
    }

    private void setSystemInfo() {
        extent.setSystemInfo("OS", System.getProperty("os.name"));
        extent.setSystemInfo("User", System.getProperty("user.name"));
        extent.setSystemInfo("Java Version", System.getProperty("java.version"));
        extent.setSystemInfo("Time Zone", System.getProperty("user.timezone"));
        extent.setSystemInfo("Browser", ConfigReader.getProperty("browser"));
        extent.setSystemInfo("Environment", ConfigReader.getProperty("env.name", "Unknown"));
        extent.setSystemInfo("Build Version", ConfigReader.getProperty("build.version", "N/A"));
    }

    /** -------------------------------------------------
     *  Test Node Management
     *  ------------------------------------------------- */

    /**
     * Creates a new test node under the current report for the running thread.
     *
     * @param testName The test case name
     */
    public void createTest(String testName) {
        ExtentTest test = extent.createTest(testName);
        extentTest.set(test);
        totalTests++;
    }

    /**
     * Returns the current thread's ExtentTest node.
     *
     * @return ExtentTest instance
     */
    public ExtentTest getTest() {
        return extentTest.get();
    }

    /** -------------------------------------------------
     *  Logging Methods
     *  ------------------------------------------------- */

    /**
     * Logs a passing step and increments the pass counter.
     *
     * @param message The pass message
     */
    public void logPass(String message) {
        getTest().pass(message);
        testsPassed++;
        logger.info("[PASS] {}", message);
    }

    /**
     * Logs a failing step along with the Throwable and increments the fail counter.
     *
     * @param message Failure description
     * @param t       Exception or error thrown
     */
    public void logFail(String message, Throwable t) {
        getTest().fail(message, MediaEntityBuilder.createScreenCaptureFromPath("./" + attachScreenshotFromThrowable(t)).build());
        testsFailed++;
        logger.error("[FAIL] {}", message, t);
    }

    /**
     * Logs a skipped test step.
     *
     * @param message Skip reason
     */
    public void logSkip(String message) {
        getTest().skip(message);
        logger.warn("[SKIP] {}", message);
    }

    /**
     * Logs an informational message tied to a specific class.
     *
     * @param message Info message
     * @param clazz   Class context
     */
    public void logInfo(String message, Class<?> clazz) {
        getTest().info(message);
        LoggerFactory.getLogger(clazz).info(message);
    }

    /**
     * Logs a simple information message.
     *
     * @param message Info message
     */
    public void logInfoSimple(String message) {
        getTest().info(message);
        logger.info(message);
    }

    /**
     * Logs a message with a specified Status (PASS/FAIL/INFO) tied to a class.
     *
     * @param status Logging status
     * @param message Log message
     * @param clazz Class context
     */
    public void log(Status status, String message, Class<?> clazz) {
        if (extentTest.get() != null) {
            extentTest.get().log(status, "[" + clazz.getSimpleName() + "] " + message);
        }
    }

    /**
     * Attaches a screenshot to the test report.
     *
     * @param path  Relative path to screenshot
     * @param title Caption title
     */
    public void attachScreenshotFromPath(String path, String title) {
        try {
            getTest().fail(title,
                    MediaEntityBuilder.createScreenCaptureFromPath("./" + path).build());
        } catch (Exception e) {
            logger.error("Failed to attach screenshot to report.", e);
        }
    }

    private String attachScreenshotFromThrowable(Throwable t) {
        // Placeholder — Normally you’d take screenshot here or generate error stack trace snapshot
        return "path/to/error_screenshot.png";
    }

    /** -------------------------------------------------
     *  Finalization Methods
     *  ------------------------------------------------- */

    /**
     * Returns the generated report file path.
     *
     * @return HTML report path
     */
    public String getReportPath() {
        return reportPath;
    }

    /**
     * Flushes the ExtentReports data to disk.
     */
    public void flushReport() {
        if (extent != null) {
            extent.flush();
            logger.info("Extent Report flushed to disk.");
        }
    }

    /**
     * Removes the ExtentTest instance from ThreadLocal storage.
     */
    public void removeTest() {
        extentTest.remove();
    }

    /** -------------------------------------------------
     *  Metrics Getters
     *  ------------------------------------------------- */

    public int getTotalTests() {
        return totalTests;
    }

    public int getTestsPassed() {
        return testsPassed;
    }

    public int getTestsFailed() {
        return testsFailed;
    }
}
