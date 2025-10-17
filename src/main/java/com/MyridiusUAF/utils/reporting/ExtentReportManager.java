package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import com.aventstack.extentreports.reporter.configuration.ViewName;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Locale;

/**
 * Singleton (enum) managing ExtentReports lifecycle.
 * - Initializes reporter
 * - Logs in a consistent, enterprise-friendly format
 * - Populates System/Environment from runtime (no config for machine/host data)
 */
public enum ExtentReportManager {

    INSTANCE;

    private static final Logger logger = LoggerFactory.getLogger(ExtentReportManager.class);

    private static final ThreadLocal<ExtentTest> extentTest = new ThreadLocal<>();
    private static final ObjectMapper OM = new ObjectMapper();

    private ExtentReports extent;
    private String reportPath;

    private volatile String applicationUrl; // set by BaseTest after driver.get(url)

    private int totalTests = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;

    // -------------------------------------------------
    // Initialization
    // -------------------------------------------------

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String orNA(String v) {
        return isBlank(v) ? "N/A" : v;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String detectOsPretty() {
        if (isWindows()) {
            String caption = runPS("(Get-CimInstance Win32_OperatingSystem | Select-Object -Expand Caption)");
            String display = runPS("(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion').DisplayVersion");
            if (!isBlank(caption)) return caption.trim() + (isBlank(display) ? "" : " " + display.trim());
        }
        String name = System.getProperty("os.name", "Unknown");
        String ver = System.getProperty("os.version", "");
        return name + (ver.isBlank() ? "" : " " + ver);
    }

    // -------------------------------------------------
    // Test Node Management
    // -------------------------------------------------

    private static String detectDeviceModel() {
        if (isWindows()) {
            String model = runPS("(Get-CimInstance Win32_ComputerSystem | Select-Object -Expand Model)");
            if (!isBlank(model)) return model.trim();
            String alt = runPS("(Get-CimInstance Win32_ComputerSystemProduct | Select-Object -Expand Name)");
            if (!isBlank(alt)) return alt.trim();
        }
        return "N/A";
    }

    private static String detectRunner() {
        if (System.getenv("JENKINS_HOME") != null) return "Jenkins";
        if ("true".equalsIgnoreCase(System.getenv("GITHUB_ACTIONS"))) return "GitHub Actions";
        if ("true".equalsIgnoreCase(System.getenv("GITLAB_CI"))) return "GitLab CI";
        if (System.getenv("TF_BUILD") != null) return "Azure Pipelines";
        if ("true".equalsIgnoreCase(System.getenv("CIRCLECI"))) return "CircleCI";
        if (System.getenv("CI") != null) return "CI Environment";
        return "Local Machine";
    }

    // -------------------------------------------------
    // Logging
    // -------------------------------------------------

    private static String detectIDE() {
        for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-javaagent:") && arg.toLowerCase(Locale.ROOT).contains("idea_rt.jar")) {
                // Try to read edition/version folder from path
                String path = arg.substring("-javaagent:".length());
                int eq = path.indexOf('=');
                if (eq > 0) path = path.substring(0, eq);
                String norm = path.replace('\\', '/').toLowerCase(Locale.ROOT);
                if (norm.contains("/community")) return "IntelliJ IDEA Community Edition";
                return "IntelliJ IDEA";
            }
        }
        if (System.getProperty("eclipse.buildId") != null) return "Eclipse IDE";
        if (System.getenv("VSCODE_PID") != null || "vscode".equalsIgnoreCase(System.getenv("TERM_PROGRAM")))
            return "Visual Studio Code";
        return "N/A";
    }

    private static HostSnapshot snapshotHost() {
        String host = "Unknown";
        String local = "N/A";
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception ignored) {
        }
        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                var addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var a = addrs.nextElement();
                    if (a instanceof Inet4Address v4 && v4.isSiteLocalAddress()) {
                        local = v4.getHostAddress();
                        break;
                    }
                }
                if (!"N/A".equals(local)) break;
            }
        } catch (Exception ignored) {
        }
        return new HostSnapshot(host, local);
    }

    /**
     * Best-effort public IP + country lookup (ipapi.co). Times out quickly and fails gracefully.
     */
    private static GeoSnapshot snapshotGeo() {
        String pub = "N/A", name = "N/A", code = "";
        try {
            HttpClient hc = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(3)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://ipapi.co/json"))
                    .header("User-Agent", "MyridiusUAF/1.0").timeout(java.time.Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> rsp = hc.send(req, HttpResponse.BodyHandlers.ofString());
            if (rsp.statusCode() >= 200 && rsp.statusCode() < 300) {
                JsonNode root = OM.readTree(rsp.body());
                pub = opt(root, "ip", pub);
                name = opt(root, "country_name", name);
                code = opt(root, "country", code);
            }
        } catch (Exception ignored) {
            // offline? keep defaults
        }
        // fallback to JVM locale if country unknown
        if ("N/A".equals(name)) {
            Locale l = Locale.getDefault();
            name = l.getDisplayCountry(Locale.ENGLISH);
            code = l.getCountry();
            if (isBlank(name)) name = "N/A";
        }
        return new GeoSnapshot(pub, name, code == null ? "" : code);
    }

    private static String opt(JsonNode n, String field, String def) {
        if (n == null) return def;
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? def : v.asText(def);
    }

    /**
     * Short PowerShell runner for Windows-only lookups.
     */
    private static String runPS(String ps) {
        if (!isWindows()) return null;
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        p.waitFor();
                        return line;
                    }
                }
            }
            p.waitFor();
        } catch (Exception ignored) {
        }
        return null;
    }

    public synchronized void initReport() {
        if (extent == null) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            this.reportPath = "reports/ExecutionReport_" + timestamp + ".html";

            ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
            configureSparkReporter(spark);

            extent = new ExtentReports();
            extent.attachReporter(spark);

            // First pass of system info (Application URL may be set later and will overwrite)
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
                      if (firstFailure) firstFailure.scrollIntoView({behavior:'smooth', block:'center'});
                    });
                """);
        spark.viewConfigurer().viewOrder()
                .as(new ViewName[]{ViewName.DASHBOARD, ViewName.TEST, ViewName.CATEGORY, ViewName.AUTHOR, ViewName.EXCEPTION, ViewName.LOG});
    }

    /**
     * Ordered, runtime-derived System/Environment info.
     * (No config for machine/host/IDE/network — we detect it.)
     */
    private void setSystemInfo() {
        // ---- New runtime fields (requested) ----
        put("Application URL", orNA(applicationUrl));                                // set later by setApplicationUrl
        put("Device Model", detectDeviceModel());                                    // Windows PS; else N/A
        put("Operating System", detectOsPretty());                                   // Friendly OS string
        put("Runs On", detectRunner());                                              // Jenkins/GitHub/etc or Local Machine
        put("IDE", detectIDE());                                                     // IntelliJ/Eclipse/VS Code best-effort

        HostSnapshot host = snapshotHost();
        put("Host Name", host.host);
        put("Local IP", host.localIp);
        GeoSnapshot geo = snapshotGeo();
        put("Public IP", geo.publicIp);
        put("Country", geo.countryName + (geo.countryCode.isBlank() ? "" : " (" + geo.countryCode + ")"));

        // ---- Existing standard fields (these may legitimately come from config) ----
        put("User", System.getProperty("user.name", "Unknown"));
        put("Java Version", System.getProperty("java.version", "Unknown"));
        put("Time Zone", ZoneId.systemDefault().getId());
        put("Browser", ConfigReader.getProperty("browser", "unknown"));
        put("Environment", ConfigReader.getProperty("env.name", "Test"));
        put("Build Version", ConfigReader.getProperty("build.version", "N/A"));
    }

    // -------------------------------------------------
    // Finalization & Metrics
    // -------------------------------------------------

    // allow BaseTest to set/overwrite Application URL at runtime
    public void setApplicationUrl(String url) {
        this.applicationUrl = url;
        if (extent != null) extent.setSystemInfo("Application URL", orNA(url)); // overwrite row in the table
    }

    public void createTest(String testName) {
        ExtentTest test = extent.createTest(testName);
        extentTest.set(test);
        totalTests++;
    }

    public ExtentTest getTest() {
        return extentTest.get();
    }

    public void logPass(String message) {
        getTest().pass(message);
        testsPassed++;
        logger.info("[PASS] {}", message);
    }

    public void logFail(String message, Throwable t) {
        try {
            getTest().fail(message,
                    MediaEntityBuilder.createScreenCaptureFromPath("./" + attachScreenshotFromThrowable(t)).build());
        } catch (Exception ignore) {
            getTest().fail(message);
        }
        testsFailed++;
        logger.error("[FAIL] {}", message, t);
    }

    public void logSkip(String message) {
        getTest().skip(message);
        logger.warn("[SKIP] {}", message);
    }

    public void logInfo(String message, Class<?> clazz) {
        getTest().info(message);
        LoggerFactory.getLogger(clazz).info(message);
    }

    // -------------------------------------------------
    // Internal helpers (runtime discovery)
    // -------------------------------------------------

    public void logInfoSimple(String message) {
        getTest().info(message);
        logger.info(message);
    }

    public void log(Status status, String message, Class<?> clazz) {
        if (extentTest.get() != null) extentTest.get().log(status, "[" + clazz.getSimpleName() + "] " + message);
    }

    public void attachScreenshotFromPath(String path, String title) {
        try {
            getTest().fail(title, MediaEntityBuilder.createScreenCaptureFromPath("./" + path).build());
        } catch (Exception e) {
            logger.error("Failed to attach screenshot to report.", e);
        }
    }

    private String attachScreenshotFromThrowable(Throwable t) {
        // hook for your screenshot capture
        return "path/to/error_screenshot.png";
    }

    public String getReportPath() {
        return reportPath;
    }

    public String getReportUrl() {
        if (this.reportPath == null) return "";
        try {
            return new java.io.File(this.reportPath).toURI().toString();
        } catch (Exception e) {
            logger.warn("Unable to generate file:// URL for report", e);
            return "";
        }
    }

    public void flushReport() {
        if (extent != null) {
            extent.flush();
            logger.info("Extent Report flushed to disk.");
        }
    }

    public void removeTest() {
        extentTest.remove();
    }

    public int getTotalTests() {
        return totalTests;
    }

    public int getTestsPassed() {
        return testsPassed;
    }

    public int getTestsFailed() {
        return testsFailed;
    }

    private void put(String key, String value) {
        extent.setSystemInfo(key, value == null ? "" : value);
    }

    private record HostSnapshot(String host, String localIp) {
    }

    private record GeoSnapshot(String publicIp, String countryName, String countryCode) {
    }
}
