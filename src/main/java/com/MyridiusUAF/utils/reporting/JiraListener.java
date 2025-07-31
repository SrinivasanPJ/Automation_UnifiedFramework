package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.utils.core.ScreenshotUtil;
import com.MyridiusUAF.utils.annotations.Jira;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.openqa.selenium.WebDriver;

import java.nio.file.Paths;

/**
 * TestNG Listener for integrating JIRA traceability and reporting with automated test outcomes.
 * <p>
 * For each test, this class can:
 * <ul>
 *     <li>Comment on or create a JIRA issue upon failure, attaching screenshots and details</li>
 *     <li>Annotate pass/skip in JIRA as comments</li>
 *     <li>Embed clickable JIRA links with live status into ExtentReports</li>
 * </ul>
 * This is designed for enterprise-grade traceability and audit support.
 */
public class JiraListener implements ITestListener {

    private static final String JIRA_BASE_URL = "https://myridius-team-uaf.atlassian.net/browse/";
    // Use standard colors for status tags in the report
    private static final String COLOR_FAIL = "#1976D2";
    private static final String COLOR_PASS = "#43A047";
    private static final String COLOR_SKIP = "#FBC02D";

    /**
     * Called when a test method fails.
     * Attempts to comment on an existing JIRA ticket (if annotated), or creates a new JIRA issue.
     */
    @Override
    public void onTestFailure(ITestResult result) {
        String testName = result.getMethod().getMethodName();
        String errorMessage = result.getThrowable() != null ? result.getThrowable().toString() : "Test failed.";

        String summary = "[Automation Failure] " + testName;
        String description = "Test Method: " + testName + "\nError: " + errorMessage;

        // *** Use screenshot captured and stored in BaseTest ***
        String screenshotPath = (String) result.getTestContext().getAttribute("LastScreenshotPath");

        // Use annotation for traceability, else create a new ticket
        String jiraId = getJiraId(result);
        if (jiraId != null && !jiraId.isEmpty()) {
            JiraUtil.addADFComment(jiraId, result);

            // Always attach screenshot if present and file exists
            if (screenshotPath != null && new java.io.File(screenshotPath).exists()) {
                JiraUtil.attachScreenshot(jiraId, screenshotPath);
            }
            addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_FAIL, "ADF Comment Added");

        } else {
            // No annotation: create new ticket for this failure
            String issueKey = JiraUtil.createIssue(summary, description);
            if (issueKey != null && !issueKey.isEmpty() && screenshotPath != null && new java.io.File(screenshotPath).exists()) {
                JiraUtil.attachScreenshot(issueKey, screenshotPath);
            }
            addJiraLinkToReport(issueKey, JiraUtil.getIssueStatus(issueKey), COLOR_FAIL, "Created");
        }
    }

    /**
     * Called when a test method passes.
     * Optionally comments on the mapped JIRA ticket and annotates status in report.
     */
    @Override
    public void onTestSuccess(ITestResult result) {
        String jiraId = getJiraId(result);
        if (jiraId != null && !jiraId.isEmpty()) {
            JiraUtil.addADFComment(jiraId, result);
            addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_PASS, "ADF Comment Added");
        }
    }

    /**
     * Called when a test is skipped.
     * Optionally comments on the mapped JIRA ticket and annotates status in report.
     */
    @Override
    public void onTestSkipped(ITestResult result) {
        String jiraId = getJiraId(result);
        if (jiraId != null && !jiraId.isEmpty()) {
            JiraUtil.addADFComment(jiraId, result);
            addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_SKIP, "ADF Comment Added");
        }
    }

    /**
     * Attempts to capture a screenshot using the WebDriver stored in the test context.
     * @param result The ITestResult object
     * @param screenshotName The desired screenshot filename
     * @return The relative path to the screenshot (or null if none captured)
     */
    private String captureScreenshot(ITestResult result, String screenshotName) {
        Object driverAttr = result.getTestContext().getAttribute("WebDriver");
        if (driverAttr instanceof WebDriver driver) {
            return ScreenshotUtil.saveScreenshotAsPNG(driver, screenshotName);
        }
        return null;
    }

    /**
     * Helper to embed a clickable JIRA link and live status tag in the report.
     */
    private void addJiraLinkToReport(String jiraId, String status, String color, String action) {
        if (jiraId == null || jiraId.isEmpty()) return;
        String jiraUrl = JIRA_BASE_URL + jiraId;
        String statusTag = "<span style='color: " + color + "; font-weight:bold;'>[" + status + "]</span>";
        ExtentReportManager.INSTANCE.getTest().info(
                "JIRA Ticket: <a href='" + jiraUrl + "' target='_blank'>" + jiraId + "</a> " +
                        statusTag + " (" + action + ")"
        );
    }

    /**
     * Extracts the JIRA ID from the @Jira annotation (if present) on the test method.
     * @param result The ITestResult object
     * @return JIRA issue key or null if annotation not found
     */
    private String getJiraId(ITestResult result) {
        Jira jira = result.getMethod().getConstructorOrMethod().getMethod().getAnnotation(Jira.class);
        return jira != null ? jira.value() : null;
    }
}
