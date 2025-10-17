package com.MyridiusUAF.utils.core;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.reporting.LogUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.MutableCapabilities;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.openqa.selenium.safari.SafariOptions;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

/**
 * Centralized factory for creating and managing {@link WebDriver} instances.
 * - Thread-safe via {@link ThreadLocal}
 * - Local and Remote execution
 * - Supported browsers: Chrome, Firefox, Edge, Safari, Safari Technology Preview
 * <p>
 * Notes:
 * - Headless mode and CLI arguments do not apply to Safari; they are safely ignored with warnings.
 * - Safari/STP local runs do not need WebDriverManager (Apple ships safaridriver).
 */
public final class DriverFactory {

    // ─────────────────────────────────────────────────────────────────────
    // Constants / State
    // ─────────────────────────────────────────────────────────────────────
    private static final ThreadLocal<WebDriver> DRIVER = new ThreadLocal<>();
    private static final String DEFAULT_BROWSER = "chrome";

    private DriverFactory() { /* static utility */ }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Initialize the driver for the current thread. No-ops if already initialized.
     *
     * @param browserFromExcel An optional browser override (e.g., from test data). If blank/null,
     *                         falls back to {@code browser} in config or {@value DEFAULT_BROWSER}.
     */
    public static void initializeDriver(String browserFromExcel) {
        if (DRIVER.get() != null) return;

        // Resolve browser
        final String browser = Optional.ofNullable(browserFromExcel)
                .filter(s -> !s.isBlank())
                .orElse(ConfigReader.getProperty("browser", DEFAULT_BROWSER))
                .trim()
                .toLowerCase(Locale.ROOT);

        final boolean isRemote = Boolean.parseBoolean(ConfigReader.getProperty("remote.execution", "false"));
        final boolean isHeadless = Boolean.parseBoolean(ConfigReader.getProperty("headless.mode", "false"));

        // Pull generic & per-browser args from config
        final List<String> browserArgs = getArgs(browser + ".browser.arguments");
        final List<String> headlessArgs = getArgs("headless.arguments");

        // Build driver suppliers map and construct
        final Map<String, Supplier<WebDriver>> suppliers =
                buildSuppliers(browserArgs, headlessArgs, isHeadless, isRemote);

        final Supplier<WebDriver> creator = suppliers.getOrDefault(browser, suppliers.get(DEFAULT_BROWSER));
        final WebDriver webDriver = Objects.requireNonNull(creator, "No WebDriver supplier available!").get();

        DRIVER.set(webDriver);
    }

    /**
     * @return the thread-bound {@link WebDriver} instance.
     * @throws IllegalStateException if the driver was not initialized via {@link #initializeDriver(String)}.
     */
    public static WebDriver getDriver() {
        final WebDriver d = DRIVER.get();
        if (d == null) {
            throw new IllegalStateException("WebDriver not initialized! Call initializeDriver() first.");
        }
        return d;
    }

    /**
     * Gracefully quits the current driver (if present) and clears the thread-local reference.
     */
    public static void quitDriver() {
        final WebDriver d = DRIVER.get();
        if (d == null) return;

        try {
            try {
                d.manage().deleteAllCookies();
                LogUtil.log(DriverFactory.class,
                        "Cleared all cookies for domain: " + getDomainSafe(d));
            } catch (Exception cookieErr) {
                LogUtil.warn(DriverFactory.class, "Unable to clear cookies: " + cookieErr.getMessage());
            }

            d.quit();
            LogUtil.log(DriverFactory.class, "WebDriver quit successfully.");
        } catch (Exception e) {
            LogUtil.log(DriverFactory.class, "Error while quitting WebDriver: " + e.getMessage());
        } finally {
            DRIVER.remove();
        }
    }

    /**
     * Windows-only convenience: kills chromedriver.exe and clears the WDM cache.
     * Safe no-op on non-Windows environments where taskkill is absent.
     */
    public static void clearChromeDriverCache() {
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", "chromedriver.exe")
                    .inheritIO()
                    .start()
                    .waitFor();

            Thread.sleep(1000L); // ensure process termination
            WebDriverManager.chromedriver().clearDriverCache();
            LogUtil.info(DriverFactory.class, "ChromeDriver cache cleared successfully.");
        } catch (Exception e) {
            LogUtil.warn(DriverFactory.class, "Unable to clear ChromeDriver cache: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Supplier map
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Builds the browser→supplier mapping based on execution mode & flags.
     */
    private static Map<String, Supplier<WebDriver>> buildSuppliers(
            List<String> browserArgs,
            List<String> headlessArgs,
            boolean isHeadless,
            boolean isRemote
    ) {
        final Map<String, Supplier<WebDriver>> map = new LinkedHashMap<>();

        // Chrome
        map.put("chrome", () -> isRemote
                ? createRemote(new ChromeOptions(), browserArgs, headlessArgs, isHeadless)
                : createLocal(new ChromeOptions(), WebDriverManager.chromedriver(),
                browserArgs, headlessArgs, isHeadless, ChromeDriver::new));

        // Firefox
        map.put("firefox", () -> isRemote
                ? createRemote(new FirefoxOptions(), browserArgs, headlessArgs, isHeadless)
                : createLocal(new FirefoxOptions(), WebDriverManager.firefoxdriver(),
                browserArgs, headlessArgs, isHeadless, FirefoxDriver::new));

        // Edge
        map.put("edge", () -> isRemote
                ? createRemote(new EdgeOptions(), browserArgs, headlessArgs, isHeadless)
                : createLocal(new EdgeOptions(), WebDriverManager.edgedriver(),
                browserArgs, headlessArgs, isHeadless, EdgeDriver::new));

        // Safari (macOS only)
        map.put("safari", () -> {
            final SafariOptions opts = new SafariOptions();
            applyArguments(opts, browserArgs, headlessArgs, isHeadless); // logs about ignored flags
            return isRemote ? createRemote(opts, Collections.emptyList(), Collections.emptyList(), false)
                    : createLocalSafari(opts, false);
        });

        // Safari Technology Preview (macOS only)
        map.put("safari-tp", () -> {
            final SafariOptions stp = new SafariOptions();
            stp.setUseTechnologyPreview(true);
            applyArguments(stp, browserArgs, headlessArgs, isHeadless); // logs about ignored flags
            return isRemote ? createRemote(stp, Collections.emptyList(), Collections.emptyList(), false)
                    : createLocalSafari(stp, true);
        });
        // common alias
        map.put("safari_technology_preview", map.get("safari-tp"));

        return map;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Local / Remote constructors
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Generic local driver constructor for Chromium/Firefox/Edge.
     */
    private static <T extends WebDriver, O extends MutableCapabilities> T createLocal(
            O options,
            WebDriverManager manager,
            List<String> browserArgs,
            List<String> headlessArgs,
            boolean isHeadless,
            Supplier<T> supplier
    ) {
        LogUtil.info(DriverFactory.class, "Skipping driver cache cleanup for stability.");
        manager.setup();

        applyArguments(options, browserArgs, headlessArgs, isHeadless);

        final T d = supplier.get();
        try {
            d.manage().window().maximize();
        } catch (Exception e) {
            LogUtil.log(DriverFactory.class, "Unable to maximize browser window: " + e.getMessage());
        }
        return d;
    }

    /**
     * Local Safari constructor (no WDM; Apple ships safaridriver).
     *
     * @param options           Safari options (STP flag may be set on it)
     * @param technologyPreview true to log STP usage (capability already set on options)
     */
    private static WebDriver createLocalSafari(SafariOptions options, boolean technologyPreview) {
        if (technologyPreview) {
            LogUtil.info(DriverFactory.class, "Launching Safari Technology Preview (local).");
        } else {
            LogUtil.info(DriverFactory.class, "Launching Safari (local).");
        }
        // No maximize guarantee on macOS full screen; keep default behavior.
        return new SafariDriver(options);
    }

    /**
     * Remote (Grid/Cloud) constructor for any browser capabilities.
     */
    private static RemoteWebDriver createRemote(
            MutableCapabilities options,
            List<String> browserArgs,
            List<String> headlessArgs,
            boolean isHeadless
    ) {
        applyArguments(options, browserArgs, headlessArgs, isHeadless);
        final String raw = ConfigReader.getProperty("remote.url");
        try {
            final URL url = URI.create(raw).toURL();
            LogUtil.info(DriverFactory.class, "Connecting to remote WebDriver at " + url);
            return new RemoteWebDriver(url, options);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid remote WebDriver URL: " + raw, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Capability wiring
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Apply headless & custom arguments to capabilities where the browser supports them.
     * Safari ignores CLI args and headless; we log that for transparency.
     */
    private static void applyArguments(
            MutableCapabilities options,
            List<String> browserArgs,
            List<String> headlessArgs,
            boolean isHeadless
    ) {
        if (options instanceof ChromeOptions chrome) {
            chrome.addArguments(browserArgs);
            if (isHeadless) chrome.addArguments(headlessArgs);

            // Hardened default arguments
            chrome.addArguments(
                    "--incognito",
                    "--disable-popup-blocking",
                    "--disable-notifications",
                    "--disable-save-password-bubble",
                    "--disable-password-manager-reauthentication",
                    "--disable-infobars",
                    "--disable-extensions",
                    "--disable-blink-features=AutomationControlled"
            );

            chrome.setExperimentalOption("prefs", chromePrefs());
            chrome.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            chrome.setExperimentalOption("useAutomationExtension", false);
        } else if (options instanceof FirefoxOptions ff) {
            ff.addArguments(browserArgs);
            if (isHeadless) ff.addArguments(headlessArgs);
        } else if (options instanceof EdgeOptions edge) {
            edge.addArguments(browserArgs);
            if (isHeadless) edge.addArguments(headlessArgs);
        } else if (options instanceof SafariOptions safari) {
            // Safari: no headless, no custom CLI args
            if (isHeadless) {
                LogUtil.warn(DriverFactory.class, "Headless is not supported for Safari. Ignoring headless setting.");
            }
            if (!browserArgs.isEmpty()) {
                LogUtil.warn(DriverFactory.class,
                        "Safari ignores custom browser arguments. Ignoring provided args: " + browserArgs);
            }

            // Config-driven hooks (macOS only; guarded with try-catch for API drift)
            final boolean autoInspect =
                    Boolean.parseBoolean(ConfigReader.getProperty("safari.automaticInspection", "false"));
            final boolean autoProfile =
                    Boolean.parseBoolean(ConfigReader.getProperty("safari.automaticProfiling", "false"));
            try {
                safari.setAutomaticInspection(autoInspect);
            } catch (Throwable t) {
                LogUtil.warn(DriverFactory.class, "Safari setAutomaticInspection not supported on this stack.");
            }
            try {
                safari.setAutomaticProfiling(autoProfile);
            } catch (Throwable t) {
                LogUtil.warn(DriverFactory.class, "Safari setAutomaticProfiling not supported on this stack.");
            }
        }
    }

    /**
     * Opinionated Chrome preferences to reduce prompts and test flakiness.
     */
    private static Map<String, Object> chromePrefs() {
        final Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_settings.popups", 0);
        prefs.put("password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("signin.allowed", false);
        return prefs;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Small utilities
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Splits a comma-separated config value into a list of args.
     */
    private static List<String> getArgs(String key) {
        final String raw = ConfigReader.getProperty(key, "");
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        final String[] parts = raw.split(",");
        final List<String> list = new ArrayList<>(parts.length);
        for (String p : parts) {
            final String s = p.trim();
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    /**
     * Safely extracts the protocol + host of the current URL for logs.
     */
    private static String getDomainSafe(WebDriver d) {
        try {
            final String current = d.getCurrentUrl();
            final URI uri = URI.create(current);
            return uri.getScheme() + "://" + uri.getHost();
        } catch (Exception e) {
            return "(unknown-domain)";
        }
    }
}
