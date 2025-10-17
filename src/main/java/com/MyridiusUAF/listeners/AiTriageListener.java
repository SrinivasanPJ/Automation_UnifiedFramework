package com.MyridiusUAF.listeners;

import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.CountDownLatch;

public class AiTriageListener implements ITestListener {
    private static final Logger log = LoggerFactory.getLogger(AiTriageListener.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final String CTX_LAST_SCREENSHOT = "LastScreenshotPath";
    private static final String CTX_TRIAGE_LATCH = "AiTriageLatch";
    private static final String CTX_MD_PATH = "AiTriageMdPath";
    private static final String CTX_MD = "AiTriageMd";

    // Returns "LLM" or "Fallback" based on the triage header we add in addHeader(...)
    private static String sourceTag(String md) {
        if (md == null) return "Unknown";
        String s = md.stripLeading();
        if (s.startsWith("# AI Triage (LLM)")) return "LLM";
        if (s.startsWith("# AI Triage (Fallback)")) return "Fallback";
        return "Unknown";
    }

    private static Path writeMarkdown(String testName, String md) {
        try {
            Path dir = Paths.get("reports", "triage");
            Files.createDirectories(dir);
            Path out = dir.resolve(testName + "_" + LocalDateTime.now().format(TS) + ".md");
            Files.writeString(out, md, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Failed to write AI triage markdown", e);
        }
    }

    private static String makeMarkdownTriage(String clazz, String test, String browser,
                                             String url, String screenshot, String stack) {
        LlmClient llm = LlmClientFactory.maybeCreate();

        final String guide =
                "Return GitHub-Flavored Markdown with headings exactly: " +
                        "## Summary, ## Likely Root Cause, ## Evidence, ## Fix Suggestion. " +
                        "Be concise (80-150 words total). Prefer concrete, test-engineering advice.";

        if (llm != null) {
            try {
                String sys = "You are a senior SDET triaging failed Selenium+TestNG UI tests.";
                String user = ""
                        + "Test: " + clazz + "#" + test + "\n"
                        + "Browser: " + browser + "\n"
                        + "URL: " + url + "\n"
                        + "Screenshot: " + (screenshot == null ? "(none)" : screenshot) + "\n"
                        + "Triage Guide: " + guide + "\n\n"
                        + "Stack (trimmed):\n" + clip(stack, 3000);
                String ans = llm.chat(sys, user);
                String md = normalizeMarkdown(ans);
                if (!md.isBlank()) return addHeader(md, "LLM");
            } catch (Exception ignored) { /* fall back */ }
        }

        return addHeader(ruleBasedFallback(clazz, test, browser, url, screenshot, stack), "Fallback");
    }

    private static String ruleBasedFallback(String clazz, String test, String browser,
                                            String url, String screenshot, String stack) {
        String top = stack == null ? "" : stack.split("\n", 2)[0];
        String root;
        String fix;

        if (top.contains(InvalidSelectorException.class.getSimpleName())) {
            root = "Invalid XPath/CSS selector caused DOM query to fail.";
            fix = "Correct the selector syntax. Prefer robust CSS; add data-test attributes.";
        } else if (top.contains(NoSuchElementException.class.getSimpleName())) {
            root = "Element not found before timeout.";
            fix = "Wait for visibility, stabilize locator, ensure correct page/frame.";
        } else if (top.contains(TimeoutException.class.getSimpleName())) {
            root = "Condition not met within wait.";
            fix = "Tune waits, assert page readiness and network/async states.";
        } else if (top.contains(StaleElementReferenceException.class.getSimpleName())) {
            root = "DOM refreshed after element reference was captured.";
            fix = "Re-locate element after refresh; wait for stable state.";
        } else if (top.contains(ElementClickInterceptedException.class.getSimpleName())) {
            root = "Click blocked by overlay or off-screen element.";
            fix = "Scroll into view, wait overlay to disappear, JS click as last resort.";
        } else {
            root = "General test failure (see top of stack).";
            fix = "Inspect failing step, align waits/locators, reproduce with debug logs.";
        }

        String evidence = "Top of stack: `" + top.replace("`", "'") + "`"
                + (isBlank(screenshot) ? "" : "\nScreenshot: `" + screenshot + "`");

        return """
                ## Summary
                The test failed in `%s#%s` on **%s** while navigating `%s`.
                
                ## Likely Root Cause
                %s
                
                ## Evidence
                %s
                
                ## Fix Suggestion
                %s
                """.formatted(clazz, test, browser, url, root, evidence, fix).trim();
    }

    private static String clip(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "\n... [truncated]";
    }

    // ---------- helpers ----------

    private static String normalizeMarkdown(String ans) {
        if (ans == null) return "";
        return ans.trim()
                .replaceFirst("^```(?:md|markdown)?\\s*", "")
                .replaceFirst("\\s*```$", "")
                .trim();
    }

    private static String addHeader(String md, String src) {
        return "# AI Triage (" + src + ")\n\n" + md;
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String asString(Object o) {
        return (o == null) ? null : String.valueOf(o);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String findLatestScreenshotForTest(String testName) {
        Path dir = Paths.get("reports", "screenshots");
        if (!Files.isDirectory(dir)) return null;
        final long windowMs = 5 * 60_000L;
        final long now = System.currentTimeMillis();

        try {
            return Files.list(dir)
                    .filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith(testName + "_") &&
                                (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"));
                    })
                    .filter(p -> {
                        try {
                            return (now - Files.getLastModifiedTime(p).toMillis()) <= windowMs;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return -Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return Long.MIN_VALUE;
                        }
                    }))
                    .map(Path::toString)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
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

    @Override
    public void onTestFailure(ITestResult result) {
        if (!Boolean.parseBoolean(ConfigReader.getProperty("ai.triage.enabled", "true"))) return;

        final ITestContext ctx = result.getTestContext();
        final String testName = result.getMethod().getMethodName();
        final String className = result.getTestClass().getName();
        final String browser = String.valueOf(ctx.getAttribute("Browser"));
        final String appUrl = String.valueOf(ctx.getAttribute("ApplicationUrl"));

        // Prefer screenshot captured by BaseTest; else pick freshest by name
        String screenshot = asString(ctx.getAttribute(CTX_LAST_SCREENSHOT));
        if (isBlank(screenshot)) screenshot = findLatestScreenshotForTest(testName);

        final String stack = (result.getThrowable() == null)
                ? "(no stack trace)"
                : ExceptionUtils.getStackTrace(result.getThrowable());

        String md = makeMarkdownTriage(className, testName, browser, appUrl, screenshot, stack);

        // Save artifact
        Path out = writeMarkdown(testName, md);
        ctx.setAttribute(CTX_MD_PATH, out.toString());  // JiraListener reads this
        ctx.setAttribute(CTX_MD, md);

        // Signal JiraListener that triage is ready
        try {
            CountDownLatch latch = getOrCreateLatch(ctx);
            latch.countDown();
        } catch (Throwable ignored) {
            // never block test flow
        }

        if (Boolean.parseBoolean(ConfigReader.getProperty("ai.triage.log.md.to.console", "false"))) {
            log.info("\n===== AI TRIAGE ({}) =====\n{}\n==========================", sourceTag(md), md);
        }

        // Single report log (avoid duplicate console prints)
        try {
            ExtentReportManager.INSTANCE.logInfo(
                    "<pre style='white-space:pre-wrap'>" + escapeHtml(md) + "</pre>",
                    AiTriageListener.class
            );
        } catch (Throwable ignored) { /* best-effort */ }
    }
}
