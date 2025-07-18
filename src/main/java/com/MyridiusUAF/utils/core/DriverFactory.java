package com.AutoPOC.utils.core;

import com.AutoPOC.config.ConfigReader;
import com.AutoPOC.utils.reporting.LogUtil;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.edge.*;
import org.openqa.selenium.firefox.*;
import org.openqa.selenium.remote.*;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

/**
 * DriverFactory is responsible for initializing and managing WebDriver instances across threads.
 * Supports local and remote execution across Chrome, Firefox, and Edge.
 */
public class DriverFactory {

    private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();
    private static final String DEFAULT_BROWSER = "chrome";

    /**
     * Initializes the WebDriver instance for the specified browser.
     *
     * @param browserFromExcel browser name from test data or Excel sheet
     */
    public static void initializeDriver(String browserFromExcel) {
        if (driver.get() != null) return;

        String browser = Optional.ofNullable(browserFromExcel)
                .filter(s -> !s.isBlank())
                .orElse(ConfigReader.getProperty("browser", DEFAULT_BROWSER))
                .trim()
                .toLowerCase();

        boolean isRemote = Boolean.parseBoolean(ConfigReader.getProperty("remote.execution", "false"));
        boolean isHeadless = Boolean.parseBoolean(ConfigReader.getProperty("headless.mode", "false"));
        List<String> browserArgs = getArgs(browser + ".browser.arguments");
        List<String> headlessArgs = getArgs("headless.arguments");

        Map<String, Supplier<WebDriver>> browserMap = getBrowserMap(browserArgs, headlessArgs, isHeadless, isRemote);
        WebDriver webDriver = browserMap
                .getOrDefault(browser, browserMap.get(DEFAULT_BROWSER))
                .get();

        driver.set(webDriver);
    }

    /**
     * Returns the WebDriver instance for the current thread.
     *
     * @return WebDriver instance
     */
    public static WebDriver getDriver() {
        if (driver.get() == null)
            throw new IllegalStateException("WebDriver not initialized! Call initializeDriver() first.");
        return driver.get();
    }

    /**
     * Quits the current WebDriver instance and cleans up the thread-local storage.
     */
    public static void quitDriver() {
        if (driver.get() != null) {
            try {
                driver.get().manage().deleteAllCookies();
                LogUtil.log(DriverFactory.class, "Cleared all cookies for domain: " + getDomain(driver.get().getCurrentUrl()));
                driver.get().quit();
                LogUtil.log(DriverFactory.class, "WebDriver quit successfully.");
            } catch (Exception e) {
                LogUtil.log(DriverFactory.class, "Error while quitting WebDriver: " + e.getMessage());
            } finally {
                driver.remove();
            }
        }
    }

    // ────────────────────────────────────────────────
    // Internal Helper Methods
    // ────────────────────────────────────────────────

    /**
     * Splits comma-separated browser arguments from config.
     *
     * @param configKey the property key to read from config
     * @return list of arguments
     */
    private static List<String> getArgs(String configKey) {
        String argString = ConfigReader.getProperty(configKey, "");
        return argString.isBlank() ? Collections.emptyList() : Arrays.asList(argString.split(","));
    }

    /**
     * Builds a browser-to-driver mapping based on execution mode and configuration.
     */
    private static Map<String, Supplier<WebDriver>> getBrowserMap(List<String> browserArgs, List<String> headlessArgs,
                                                                  boolean isHeadless, boolean isRemote) {
        return Map.of(
                "chrome", () -> isRemote
                        ? createRemoteDriver(new ChromeOptions(), browserArgs, headlessArgs, isHeadless)
                        : createDriver(new ChromeOptions(), WebDriverManager.chromedriver(), browserArgs, headlessArgs, isHeadless, ChromeDriver::new),

                "firefox", () -> isRemote
                        ? createRemoteDriver(new FirefoxOptions(), browserArgs, headlessArgs, isHeadless)
                        : createDriver(new FirefoxOptions(), WebDriverManager.firefoxdriver(), browserArgs, headlessArgs, isHeadless, FirefoxDriver::new),

                "edge", () -> isRemote
                        ? createRemoteDriver(new EdgeOptions(), browserArgs, headlessArgs, isHeadless)
                        : createDriver(new EdgeOptions(), WebDriverManager.edgedriver(), browserArgs, headlessArgs, isHeadless, EdgeDriver::new)
        );
    }

    /**
     * Initializes local WebDriver with the given options and arguments.
     */
    private static <T extends WebDriver, O extends MutableCapabilities> T createDriver(
            O options,
            WebDriverManager manager,
            List<String> browserArgs,
            List<String> headlessArgs,
            boolean isHeadless,
            Supplier<T> driverSupplier) {

        LogUtil.info(DriverFactory.class, "Skipping driver cache cleanup for stability.");
        manager.setup(); // Setup driver without clearing the cache

        applyArguments(options, browserArgs, headlessArgs, isHeadless);
        T webDriver = driverSupplier.get();

        try {
            webDriver.manage().window().maximize();
        } catch (Exception e) {
            LogUtil.log(DriverFactory.class, "Unable to maximize browser window: " + e.getMessage());
        }

        return webDriver;
    }

    /**
     * Creates a remote WebDriver instance using Selenium Grid or cloud.
     */
    private static RemoteWebDriver createRemoteDriver(MutableCapabilities options,
                                                      List<String> browserArgs,
                                                      List<String> headlessArgs,
                                                      boolean isHeadless) {
        applyArguments(options, browserArgs, headlessArgs, isHeadless);
        try {
            URI uri = URI.create(ConfigReader.getProperty("remote.url"));
            URL remoteUrl = uri.toURL();
            LogUtil.info(DriverFactory.class, "Connecting to remote WebDriver at " + remoteUrl);
            return new RemoteWebDriver(remoteUrl, options);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid remote WebDriver URL", e);
        }
    }

    private static Map<String, Object> getChromePreferences() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("profile.default_content_settings.popups", 0);

        // Explicitly disable Chrome's password manager prompts
        prefs.put("password_manager_enabled", false);
        prefs.put("profile.password_manager_leak_detection", false);
        prefs.put("signin.allowed", false);

        return prefs;
    }

    private static void applyArguments(MutableCapabilities options,
                                       List<String> browserArgs,
                                       List<String> headlessArgs,
                                       boolean isHeadless) {

        if (options instanceof ChromeOptions chromeOptions) {
            chromeOptions.addArguments(browserArgs);
            if (isHeadless) chromeOptions.addArguments(headlessArgs);

            chromeOptions.addArguments(
                    "--incognito",
                    "--disable-popup-blocking",
                    "--disable-notifications",
                    "--disable-save-password-bubble",
                    "--disable-password-manager-reauthentication",
                    "--disable-infobars",
                    "--disable-extensions",
                    "--disable-blink-features=AutomationControlled"
            );

            chromeOptions.setExperimentalOption("prefs", getChromePreferences());
            chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            chromeOptions.setExperimentalOption("useAutomationExtension", false);
        }
        else if (options instanceof FirefoxOptions ffOptions) {
            ffOptions.addArguments(browserArgs);
            if (isHeadless) ffOptions.addArguments(headlessArgs);
        }
        else if (options instanceof EdgeOptions edgeOptions) {
            edgeOptions.addArguments(browserArgs);
            if (isHeadless) edgeOptions.addArguments(headlessArgs);
        }
    }

    /**
     * Extracts the domain from a full URL.
     *
     * @param url complete URL
     * @return domain as string
     */
    private static String getDomain(String url) {
        try {
            URI uri = URI.create(url);
            URL parsedUrl = uri.toURL();
            return parsedUrl.getProtocol() + "://" + parsedUrl.getHost();
        } catch (Exception e) {
            LogUtil.error(DriverFactory.class, "Error parsing URL: " + e.getMessage());
            return url;
        }
    }

    /**
     * Forcefully kills existing ChromeDriver instances and clears WebDriverManager cache.
     */
    public static void clearChromeDriverCache() {
        try {
            new ProcessBuilder("taskkill", "/F", "/IM", "chromedriver.exe")
                    .inheritIO()
                    .start()
                    .waitFor();

            Thread.sleep(1000); // Wait briefly to ensure cleanup
            WebDriverManager.chromedriver().clearDriverCache();
            LogUtil.info(DriverFactory.class, "ChromeDriver cache cleared successfully.");
        } catch (Exception e) {
            LogUtil.warn(DriverFactory.class, "Unable to clear ChromeDriver cache: " + e.getMessage());
        }
    }
}