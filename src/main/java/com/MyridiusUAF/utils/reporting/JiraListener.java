package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.annotations.Jira;
import com.MyridiusUAF.utils.core.FileAwait;
import com.MyridiusUAF.utils.core.ScreenshotUtil;
import com.MyridiusUAF.utils.data.ExecutionDataUtil;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * TestNG listener that:
 * <ul>
 *   <li>Ensures an execution row is written exactly once per test.</li>
 *   <li>Resolves/creates the corresponding JIRA issue (via annotation/store/search).</li>
 *   <li>Uploads Extent report & screenshots, and posts an ADF comment.</li>
 * </ul>
 */
public class JiraListener implements ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(JiraListener.class);

    private static final String CONTEXT_EXEC_WRITTEN = "ExecDataWritten";
    private static final String JIRA_BASE_URL = "https://myridius-team-uaf.atlassian.net/browse/";

    private static final String COLOR_FAIL = "#1976D2";
    private static final String COLOR_PASS = "#43A047";
    private static final String COLOR_SKIP = "#FBC02D";

    // ---- AI triage handshake keys
    private static final String CTX_TRIAGE_LATCH = "AiTriageLatch";
    private static final String CTX_TRIAGE_MD_PATH = "AiTriageMdPath";

    private static final Duration REPORT_AWAIT = Duration.ofSeconds(5);

    private static final boolean POST_DEBUG_LINKS_COMMENT =
            Boolean.parseBoolean(String.valueOf(ConfigReader.getProperty("jira.debug.links.comment")));

    // ------------------------------------------------------------
    // Util
    // ------------------------------------------------------------

    private static boolean fileExists(final String path) {
        return path != null && !path.isEmpty() && new File(path).exists();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String valueOrMissing(String v) {
        return isBlank(v) ? "MISSING" : v;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static CountDownLatch getOrCreateLatch(final ITestContext ctx) {
        synchronized (ctx) {
            Object o = ctx.getAttribute(CTX_TRIAGE_LATCH);
            if (o instanceof CountDownLatch c) return c;
            CountDownLatch c = new CountDownLatch(1);
            ctx.setAttribute(CTX_TRIAGE_LATCH, c);
            return c;
        }
    }

    // ------------------------------------------------------------
    // Core operations
    // ------------------------------------------------------------

    @Override
    public void onTestFailure(final ITestResult result) {
        LOG.info("JIRA: onTestFailure for {}", result.getMethod().getQualifiedName());
        ensureExecRowWrittenOnce(result);

        final String jiraId = getOrCreateJiraId(result);
        logResolvedKey(jiraId);
        if (isBlank(jiraId)) return;

        final String reportUrl = ensureReportAttachedReturnUrl(jiraId);
        final String screenshotMediaId = captureAndAttachFailureScreenshotReturnMediaId(result, jiraId);

        final ITestContext ctx = result.getTestContext();

        // ---- Wait (up to configured ms) for AiTriageListener to finish and set AiTriageMdPath
        try {
            long awaitMs = Long.parseLong(ConfigReader.getProperty("jira.ai.triage.await.ms", "30000"));
            CountDownLatch latch = getOrCreateLatch(ctx); // always create/obtain same latch
            latch.await(awaitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // ---- Attach AI Triage markdown if available
        try {
            // 1) try the context attribute directly
            String triagePath = asString(ctx.getAttribute(CTX_TRIAGE_MD_PATH));
            if (!fileExists(triagePath)) {
                // 2) brief poll in case attribute is set just after the await
                triagePath = pollForAiTriageMd(result, 3000L, 200L);
            }
            if (triagePath == null) {
                // 3) best-effort fallback: newest md in reports/triage for this test (last 5 min)
                triagePath = newestTriageMdFromDisk(result, 5 * 60_000L);
            }

            if (triagePath != null) {
                JiraUtil.JiraAttachment att = attachWithRetry(jiraId, triagePath);
                if (att != null) {
                    LOG.info("JIRA: triage attach OK -> {}", triagePath);
                } else {
                    LOG.warn("JIRA: triage attach FAILED -> {}", triagePath);
                }
            } else {
                LOG.info("JIRA: no triage markdown found to attach.");
            }
        } catch (Throwable t) {
            LOG.warn("JIRA: triage attach step failed (non-fatal).", t);
        }

        JiraUtil.addADFComment(jiraId, result, reportUrl, screenshotMediaId);
        result.getTestContext().setAttribute("JIRA_ADF_POSTED", true);
        LOG.info("JIRA: ADF comment posted for FAIL {}", jiraId);

        addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_FAIL, "ADF Comment Added");
    }

    private String pollForAiTriageMd(ITestResult result, long maxWaitMs, long stepMs) {
        final ITestContext ctx = result.getTestContext();
        long end = System.currentTimeMillis() + Math.max(0, maxWaitMs);
        while (System.currentTimeMillis() < end) {
            String p = asString(ctx.getAttribute(CTX_TRIAGE_MD_PATH));
            if (fileExists(p)) return p;
            try {
                Thread.sleep(stepMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }

    private String newestTriageMdFromDisk(ITestResult result, long windowMs) {
        File dir = new File("reports/triage");
        if (!dir.isDirectory()) return null;
        String prefix = result.getMethod().getMethodName() + "_";
        long now = System.currentTimeMillis();
        File newest = null;
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix) && name.endsWith(".md"));
        if (files != null) {
            for (File f : files) {
                if (now - f.lastModified() <= windowMs &&
                        (newest == null || f.lastModified() > newest.lastModified())) {
                    newest = f;
                }
            }
        }
        return newest == null ? null : newest.getAbsolutePath();
    }

    @Override
    public void onTestSuccess(final ITestResult result) {
        LOG.info("JIRA: onTestSuccess for {}", result.getMethod().getQualifiedName());
        ensureExecRowWrittenOnce(result);

        final String jiraId = getOrCreateJiraId(result);
        logResolvedKey(jiraId);
        if (isBlank(jiraId)) return;

        final String reportUrl = ensureReportAttachedReturnUrl(jiraId);

        JiraUtil.addADFComment(jiraId, result, reportUrl, null);
        result.getTestContext().setAttribute("JIRA_ADF_POSTED", true);
        LOG.info("JIRA: ADF comment posted for PASS {}", jiraId);

        addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_PASS, "ADF Comment Added");
    }

    @Override
    public void onTestSkipped(final ITestResult result) {
        LOG.info("JIRA: onTestSkipped for {}", result.getMethod().getQualifiedName());
        ensureExecRowWrittenOnce(result);

        final String jiraId = getOrCreateJiraId(result);
        logResolvedKey(jiraId);
        if (isBlank(jiraId)) return;

        final String reportUrl = ensureReportAttachedReturnUrl(jiraId);

        JiraUtil.addADFComment(jiraId, result, reportUrl, null);
        result.getTestContext().setAttribute("JIRA_ADF_POSTED", true);
        LOG.info("JIRA: ADF comment posted for SKIP {}", jiraId);

        addJiraLinkToReport(jiraId, JiraUtil.getIssueStatus(jiraId), COLOR_SKIP, "ADF Comment Added");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private void ensureExecRowWrittenOnce(final ITestResult result) {
        // config gate to disable JiraListener's exec-row write
        boolean jiraExecWriteEnabled =
                Boolean.parseBoolean(ConfigReader.getProperty("jira.exec.write.enabled", "false"));
        if (!jiraExecWriteEnabled) {
            LOG.info("JIRA: exec row write is DISABLED via jira.exec.write.enabled=false (listener will not append).");
            return; // let BaseTest/@AfterMethod remain the single writer
        }

        // run-once guard
        Object done = result.getTestContext().getAttribute(CONTEXT_EXEC_WRITTEN);
        if (Boolean.TRUE.equals(done)) return;

        try {
            final boolean isSystem = TestTypeUtil.isSystem(result);
            final boolean isE2E    = TestTypeUtil.isE2E(result);
            if (!isSystem && !isE2E) {
                LOG.warn("Unknown test type; exec write skipped.");
                return;
            }

            final String filePath  = ConfigReader.getProperty("Test_Data_File_Path");
            final String sheetName = isSystem
                    ? ConfigReader.getProperty("Transactional_Data_Sheet_Name")
                    : ConfigReader.getProperty("End_To_End_Sheet_Name");

            final int startRow = isSystem ? 2 : 1;
            final int runCol   = isSystem ? ExcelColumnIndex.RUN_ID : E2EBindingColumnIndex.RUN_ID;

            // Open workbook locally; do not expose Sheet outside this scope
            int rowIdx;
            try (FileInputStream fis = new FileInputStream(filePath);
                 Workbook wb = WorkbookFactory.create(fis)) {

                Sheet sheet = wb.getSheet(sheetName);
                if (sheet == null) {
                    LOG.warn("JIRA: sheet not found: {} in {}", sheetName, filePath);
                    return;
                }
                rowIdx = ExcelUtil.findNextAvailableRow(sheet, runCol, startRow);
            }

            // Write execution data using the resolved row index
            ExecutionDataUtil.writeExecutionData(rowIdx, result);
            result.getTestContext().setAttribute(CONTEXT_EXEC_WRITTEN, true);
            LOG.info("Exec row written by listener (sheet={}, row={})", sheetName, rowIdx);
        } catch (Throwable t) {
            LOG.warn("Exec row write by listener failed (non-fatal).", t);
        }
    }

    private String getOrCreateJiraId(final ITestResult result) {
        final String fqn = result.getTestClass().getName() + "#" + result.getMethod().getMethodName();
        LOG.info("JIRA: resolve key for {}", fqn);

        final String url = ConfigReader.getProperty("jira.url");
        final String email = ConfigReader.getProperty("jira.email");
        final String proj = ConfigReader.getProperty("jira.project.key");
        if (isBlank(url) || isBlank(email) || isBlank(proj)) {
            LOG.warn("JIRA: config looks incomplete (url='{}', email='{}', project='{}')",
                    valueOrMissing(url), valueOrMissing(email), valueOrMissing(proj));
        }

        final String testDescRaw = result.getMethod().getDescription();
        final String fallBackTitle = "Automation: " + result.getTestClass().getName() + "." + result.getMethod().getMethodName();
        final String desiredSummary = JiraUtil.trimTo255(isBlank(testDescRaw) ? fallBackTitle : testDescRaw);
        final String desiredDescription = isBlank(testDescRaw)
                ? ("Auto-created for test '" + result.getMethod().getMethodName()
                + "'. Framework will append execution comments and artifacts.")
                : testDescRaw;

        final com.MyridiusUAF.utils.annotations.Jira jiraAnn =
                result.getMethod().getConstructorOrMethod().getMethod().getAnnotation(Jira.class);
        if (jiraAnn != null && !isBlank(jiraAnn.value())) {
            final String key = jiraAnn.value();
            LOG.info("JIRA: @Jira annotation -> {}", key);
            JiraUtil.updateIssueSummaryAndDescription(key, desiredSummary, desiredDescription);
            return key;
        }

        final String stored = JiraKeyStore.get(fqn);
        if (!isBlank(stored)) {
            LOG.info("JIRA: keystore HIT -> {}", stored);
            JiraUtil.updateIssueSummaryAndDescription(stored, desiredSummary, desiredDescription);
            return stored;
        }
        LOG.info("JIRA: keystore MISS");

        LOG.info("JIRA: findOrCreate '{}'", desiredSummary);
        final String key = JiraUtil.findOrCreateIssue(desiredSummary, desiredDescription);

        if (!isBlank(key)) {
            JiraKeyStore.put(fqn, key);
            LOG.info("JIRA: using issue {} for {}", key, fqn);
            JiraUtil.updateIssueSummaryAndDescription(key, desiredSummary, desiredDescription);
        } else {
            LOG.warn("JIRA: could not resolve/create issue for {}", fqn);
        }
        return key;
    }

    private String ensureReportAttachedReturnUrl(final String jiraId) {
        ExtentReportManager.INSTANCE.flushReport();

        final String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        final Path report = Paths.get(reportPath);

        FileAwait.waitForExistence(report, REPORT_AWAIT);
        final File reportFile = report.toFile();

        LOG.info("JIRA: reportPath = {} (exists={})", reportPath, reportFile.exists());
        final JiraUtil.JiraAttachment att = reportFile.exists() ? attachWithRetry(jiraId, reportPath) : null;
        final String url = att != null ? att.contentUrl() : null;

        LOG.info("JIRA: report attach {}", att == null ? "FAILED" : "OK -> " + url);
        return url;
    }

    private String captureAndAttachFailureScreenshotReturnMediaId(final ITestResult result, final String jiraId) {
        try {
            final Object drvObj = result.getTestContext().getAttribute("WebDriver");
            if (!(drvObj instanceof WebDriver driver)) {
                LOG.warn("JIRA: WebDriver not found in context; skipping screenshot capture");
                return null;
            }

            String shotPath = ScreenshotUtil.saveScreenshotAsPNG(driver, result.getName());
            File shotFile = new File(shotPath);
            if (!shotFile.exists()) shotFile = new File("reports", shotPath);

            if (!shotFile.exists()) {
                LOG.warn("JIRA: screenshot file missing -> {}", shotFile.getAbsolutePath());
                return null;
            }

            final JiraUtil.JiraAttachment shotAtt = attachWithRetry(jiraId, shotFile.getAbsolutePath());
            if (shotAtt != null) {
                LOG.info("JIRA: screenshot attach OK (mediaId={})", shotAtt.mediaId());
                return shotAtt.mediaId();
            }
            LOG.warn("JIRA: screenshot attach FAILED");
        } catch (Exception e) {
            LOG.warn("JIRA: screenshot capture/attach failed", e);
        }
        return null;
    }

    private void addJiraLinkToReport(final String jiraId, final String status, final String color, final String action) {
        if (isBlank(jiraId)) return;
        final String jiraUrl = JIRA_BASE_URL + jiraId;
        final String statusTag = "<span style='color: " + color + "; font-weight:bold;'>[" + status + "]</span>";
        if (ExtentReportManager.INSTANCE.getTest() != null) {
            ExtentReportManager.INSTANCE.getTest().info(
                    "JIRA Ticket: <a href='" + jiraUrl + "' target='_blank'>" + jiraId + "</a> " + statusTag + " (" + action + ")"
            );
        } else {
            LOG.info("Report not initialized; JIRA Ticket: {} ({}) {}", jiraUrl, status, action);
        }
    }

    private void logResolvedKey(String jiraId) {
        LOG.info("JIRA: resolved key = {}", isBlank(jiraId) ? "<none>" : jiraId);
    }

    private JiraUtil.JiraAttachment attachWithRetry(final String issueKey, final String path) {
        if (isBlank(path)) {
            LOG.info("JIRA: attach skipped (path blank)");
            return null;
        }
        if (!fileExists(path)) {
            LOG.info("JIRA: attach skipped (file missing): {}", path);
            return null;
        }

        final File f = new File(path);
        final long size = f.length();
        final long[] waits = {0L, 800L, 1500L};

        for (int i = 0; i < waits.length; i++) {
            if (i > 0) {
                try {
                    Thread.sleep(waits[i]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            LOG.info("JIRA: attach attempt {} -> {} ({} bytes)", (i + 1), path, size);
            final JiraUtil.JiraAttachment att = JiraUtil.attachFile(issueKey, path);
            if (att != null) {
                LOG.info("JIRA: attach OK on attempt {} (filename={}, mediaId={})", (i + 1), att.filename(), att.mediaId());
                return att;
            }
            LOG.warn("JIRA: attach attempt {} failed", (i + 1));
        }
        LOG.warn("JIRA: attach FAILED after retries -> {}", path);
        return null;
    }

    @SuppressWarnings("unused")
    private void addLinksToReportAsComment(final String jiraId, final String reportUrl, final String screenshotUrl) {
        if (isBlank(jiraId)) return;
        final StringBuilder comment = new StringBuilder("🔗 *Automation Debug Info* 🔗\n");
        if (!isBlank(reportUrl)) comment.append("- [Extent Report](").append(reportUrl).append(") ✅\n");
        if (!isBlank(screenshotUrl)) comment.append("- [Screenshot](").append(screenshotUrl).append(") 📸\n");
        if (comment.indexOf("](") > 0) JiraUtil.addComment(jiraId, comment.toString());
    }
}
