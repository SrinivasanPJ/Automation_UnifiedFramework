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
 * Factory class for managing WebDriver initialization, configuration, and cleanup.
 */
public class DriverFactory {

    private static final ThreadLocal<WebDriver> driver = new ThreadLocal<>();
    private static final String DEFAULT_BROWSER = "chrome";

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
        //LogUtil.log(DriverFactory.class, "WebDriver initialized for browser: " + browser + (isRemote ? " (remote)" : ""));
    }

    public static WebDriver getDriver() {
        if (driver.get() == null)
            throw new IllegalStateException("WebDriver not initialized! Call initializeDriver() first.");
        return driver.get();
    }

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

    // ─── Internal Helpers ─────────────────────────────────────────────

    private static List<String> getArgs(String configKey) {
        String argString = ConfigReader.getProperty(configKey, "");
        return argString.isBlank() ? Collections.emptyList() : Arrays.asList(argString.split(","));
    }

    private static Map<String, Supplier<WebDriver>> getBrowserMap(List<String> browserArgs, List<String> headlessArgs, boolean isHeadless, boolean isRemote) {
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

    private static <T extends WebDriver, O extends MutableCapabilities>
    T createDriver(O options, WebDriverManager manager, List<String> browserArgs,
                   List<String> headlessArgs, boolean isHeadless, Supplier<T> driverSupplier) {

        // Skipping driver cache cleanup to avoid deletion exceptions
        LogUtil.info(DriverFactory.class, "Skipping driver cache cleanup for stability.");
        manager.setup(); // Only setup, without clearing cache

        applyArguments(options, browserArgs, headlessArgs, isHeadless);
        T webDriver = driverSupplier.get();

        try {
            webDriver.manage().window().maximize();
            // LogUtil.log(DriverFactory.class, "Browser window maximized explicitly.");
        } catch (Exception e) {
            LogUtil.log(DriverFactory.class, "Unable to maximize browser window: " + e.getMessage());
        }

        return webDriver;
    }

    private static RemoteWebDriver createRemoteDriver(MutableCapabilities options, List<String> browserArgs, List<String> headlessArgs, boolean isHeadless) {
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

    private static void applyArguments(MutableCapabilities options, List<String> browserArgs,
                                       List<String> headlessArgs, boolean isHeadless) {

        if (options instanceof ChromeOptions chromeOptions) {
            chromeOptions.addArguments(browserArgs);
            if (isHeadless) chromeOptions.addArguments(headlessArgs);
            chromeOptions.addArguments("--incognito", "--disable-popup-blocking", "--disable-notifications",
                    "--disable-blink-features=AutomationControlled", "--disable-infobars", "--disable-extensions");

            Map<String, Object> prefs = Map.of(
                    "credentials_enable_service", false,
                    "profile.password_manager_enabled", false
            );

            chromeOptions.setExperimentalOption("prefs", prefs);
            chromeOptions.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            chromeOptions.setExperimentalOption("useAutomationExtension", false);
        } else if (options instanceof FirefoxOptions ff) {
            ff.addArguments(browserArgs);
            if (isHeadless) ff.addArguments(headlessArgs);
        } else if (options instanceof EdgeOptions edge) {
            edge.addArguments(browserArgs);
            if (isHeadless) edge.addArguments(headlessArgs);
        }

        //LogUtil.log(DriverFactory.class, "Applying browser arguments: " + browserArgs);
    }

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

    public static void clearChromeDriverCache() {
        try {
            // Kill any running chromedriver.exe process
            Runtime.getRuntime().exec("taskkill /F /IM chromedriver.exe");
            Thread.sleep(1000); // Give it time to terminate cleanly

            // Now safely clear the WebDriverManager cache
            WebDriverManager.chromedriver().clearDriverCache();
            LogUtil.info(DriverFactory.class, "ChromeDriver cache cleared successfully.");
        } catch (Exception e) {
            LogUtil.warn(DriverFactory.class, "Unable to clear ChromeDriver cache: " + e.getMessage());
        }
    }
}
