package com.MyridiusUAF.base;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.context.TestContextManager;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.healing.HealingFieldDecorator;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import com.github.javafaker.Faker;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.DefaultElementLocatorFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;

/**
 * Abstract base class for all Page Object classes in the automation framework.
 * Provides reusable element actions, wait logic, scrolling, notifications, and logging utilities.
 */
public abstract class BasePage {

    private static final Logger logger = LoggerFactory.getLogger(BasePage.class);
    private static final int DEFAULT_TIMEOUT = 40;
    private static final Faker FAKER = new Faker(); // Reuse single instance

    protected final WebDriver driver;
    protected final LlmClient llm;

    // ───── Constructors ─────────────────────────────────────────────────────

    protected BasePage() {
        this(DriverFactory.getDriver());
    }

    protected BasePage(WebDriver driver) {
        this.driver = driver;
        boolean healingEnabled = ConfigReader.getBoolean("ai.selfhealing.enabled", true);
        boolean useLlm = ConfigReader.getBoolean("ai.healing.use.llm", true);

        this.llm = (healingEnabled && useLlm && AiSwitches.openAiGloballyEnabled())
                ? createLlmOrNull()
                : null;

        if (healingEnabled) {
            PageFactory.initElements(new HealingFieldDecorator(driver, llm), this);
        } else {
            PageFactory.initElements(new DefaultElementLocatorFactory(driver), this);
        }
    }

    private LlmClient createLlmOrNull() {
        LlmClient client = LlmClientFactory.maybeCreate();
        if (client == null) {
            logger.info("Self-healing LLM OFF (OpenAI disabled or client init failed)");
        }
        return client;
    }

    // ───── Logging & Context ────────────────────────────────────────────────

    protected void log(String message) {
        ExtentReportManager.INSTANCE.logInfo(message, this.getClass());
    }

    protected Map<String, String> getInputData() {
        return TestContextManager.getInputData();
    }

    // ───── Basic Element Actions ────────────────────────────────────────────

    public void click(WebElement element, String logMsg) {
        waitUntilClickable(element).click();
        log(logMsg);
    }

    public void click(By locator, String logMsg) {
        waitUntilClickable(locator, DEFAULT_TIMEOUT).click();
        log(logMsg);
    }

    protected void sendKeys(WebElement element, String text) {
        waitUntilVisible(element).clear();
        element.sendKeys(text);
    }

    protected void selectByVisibleText(WebElement dropdown, String text) {
        new Select(waitUntilVisible(dropdown)).selectByVisibleText(text);
    }

    protected String getText(WebElement element) {
        return waitUntilVisible(element).getText().trim();
    }

    public void jsClick(WebElement element) {
        executeJs("arguments[0].click();", element);
    }

    public void hoverOverElement(WebElement element) {
        new Actions(driver).moveToElement(element).perform();
    }

    // ───── Wait Utilities ───────────────────────────────────────────────────

    public boolean isDisplayed(WebElement element) {
        try {
            waitUntilVisible(element);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    public boolean isElementPresent(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    protected boolean isPresent(By locator, long seconds) {
        try {
            newWait(seconds)
                    .ignoring(NoSuchElementException.class, StaleElementReferenceException.class)
                    .until(d -> !d.findElements(locator).isEmpty());
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected WebElement waitUntilClickable(WebElement element) {
        return waitUntilClickable(element, DEFAULT_TIMEOUT);
    }

    protected WebElement waitUntilClickable(WebElement element, int timeout) {
        return newWait(timeout).until(ExpectedConditions.elementToBeClickable(element));
    }

    protected WebElement waitUntilClickable(By locator, int timeout) {
        return newWait(timeout).until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected WebElement waitUntilVisible(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT);
    }

    protected WebElement waitUntilVisible(WebElement element, int timeout) {
        return newWait(timeout).until(ExpectedConditions.visibilityOf(element));
    }

    protected WebElement waitUntilVisible(By locator, int timeout) {
        return newWait(timeout)
                .ignoring(StaleElementReferenceException.class)
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement waitUntilVisible(By locator, long seconds) {
        return waitUntilVisible(locator, (int) seconds);
    }

    protected void waitUntilTextPresent(WebElement element, String text, int timeout) {
        newWait(timeout).until(ExpectedConditions.textToBePresentInElement(element, text));
    }

    public boolean waitUntilElementGone(WebElement element) {
        return waitUntilGone(element);
    }

    public boolean waitUntilGone(WebElement element) {
        try {
            boolean isGone = newWait(DEFAULT_TIMEOUT).until(ExpectedConditions.invisibilityOf(element));
            logger.info("Element became invisible: {}", element);
            return isGone;
        } catch (TimeoutException e) {
            logger.warn("Timeout: Element did not disappear: {}", element);
            return false;
        } catch (Exception e) {
            logger.error("Error waiting for element to disappear: {}", e.getMessage());
            return false;
        }
    }

    public boolean waitUntilGone(By locator) {
        return waitUntilGone(locator, DEFAULT_TIMEOUT);
    }

    public boolean waitUntilGone(By locator, int timeout) {
        try {
            return newWait(timeout).until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            log("Element [" + locator + "] did not disappear within " + timeout + "s.");
            return false;
        }
    }

    public void waitForPageToLoad() {
        newWait(DEFAULT_TIMEOUT).until(d ->
                "complete".equals(executeJs("return document.readyState")));
    }

    public void waitForElementCount(By locator, int expectedCount) {
        newWait(DEFAULT_TIMEOUT).until(d -> d.findElements(locator).size() == expectedCount);
    }

    // ───── Scroll Utilities ─────────────────────────────────────────────────

    public void scrollIntoView(WebElement element) {
        executeJs("arguments[0].scrollIntoView(true);", element);
    }

    public void scrollIntoView(By locator) {
        scrollIntoView(driver.findElement(locator));
    }

    public void scrollToBottom() {
        executeJs("window.scrollTo(0, document.body.scrollHeight);");
    }

    // ───── Click Utilities ──────────────────────────────────────────────────

    public void clickBy(By locator) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked element: " + locator);
    }

    protected void clickBy(String fieldName, String rawValue, String xpathTemplate) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is missing!");
        }
        By locator = By.xpath(String.format(xpathTemplate, rawValue.trim()));
        log("Waiting for " + fieldName + ": " + rawValue);
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked " + fieldName + ": " + rawValue);
    }

    // ───── Attribute & Text Utilities ───────────────────────────────────────

    public String getAttribute(WebElement element, String attribute) {
        return waitUntilVisible(element).getAttribute(attribute);
    }

    public int getNumberOfAddresses() {
        return driver.findElements(By.xpath("//div[@class='address-list']//div[contains(@class, 'section')]")).size();
    }

    // ───── Notification Utilities ───────────────────────────────────────────

    public void closeNotificationIfPresentAndWait() {
        try {
            WebElement bar = driver.findElement(By.id("bar-notification"));
            if (bar.isDisplayed()) {
                bar.findElement(By.className("close")).click();
                log("Closed notification banner.");
                waitUntilGone(By.id("bar-notification"), 10);
            }
        } catch (NoSuchElementException | TimeoutException ignored) {
            logger.debug("Notification banner not present or already dismissed.");
        } catch (Exception e) {
            logger.warn("Error closing notification: {}", e.getMessage());
        }
    }

    // ───── Frame Handling ───────────────────────────────────────────────────

    public void switchToFrame(int index) {
        driver.switchTo().frame(index);
        log("Switched to frame by index: " + index);
    }

    public void switchToFrame(String nameOrId) {
        driver.switchTo().frame(nameOrId);
        log("Switched to frame: " + nameOrId);
    }

    public void switchToFrame(WebElement frameElement) {
        switchToFrame(frameElement, DEFAULT_TIMEOUT);
    }

    public void switchToFrame(WebElement frameElement, int timeout) {
        waitUntilVisible(frameElement, timeout);
        driver.switchTo().frame(frameElement);
        log("Switched to frame via WebElement.");
    }

    public void switchToFrame(By locator) {
        switchToFrame(locator, DEFAULT_TIMEOUT);
    }

    public void switchToFrame(By locator, int timeout) {
        WebElement frame = waitUntilVisible(locator, timeout);
        driver.switchTo().frame(frame);
        log("Switched to frame: " + locator);
    }

    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
        log("Switched to default content.");
    }

    // ───── Faker & Misc ─────────────────────────────────────────────────────

    public String generateRandomCardholderName() {
        return FAKER.name().fullName();
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ───── XPath Utilities ──────────────────────────────────────────────────

    protected static String xPathLiteral(String value) {
        if (value == null) return "''";
        if (value.contains("'") && value.contains("\"")) {
            StringBuilder sb = new StringBuilder("concat(");
            String[] parts = value.split("\"", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", '\"', ");
                sb.append("\"").append(parts[i]).append("\"");
            }
            return sb.append(")").toString();
        }
        return value.contains("'") ? "\"" + value + "\"" : "'" + value + "'";
    }

    // ───── Internal Helpers ─────────────────────────────────────────────────

    private WebDriverWait newWait(long seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds));
    }

    private Object executeJs(String script, Object... args) {
        return ((JavascriptExecutor) driver).executeScript(script, args);
    }

    // ───── URL Wait Utilities ──────────────────────────────────────────────

    /**
     * Waits until the current URL contains the given partial string.
     */
    public void waitUntilUrlContains(String partialUrl) {
        waitUntilUrlContains(partialUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until the current URL contains the given partial string,
     * using a custom timeout (in seconds).
     */
    public void waitUntilUrlContains(String partialUrl, int timeoutSeconds) {
        newWait(timeoutSeconds).until(ExpectedConditions.urlContains(partialUrl));
    }

    /**
     * Waits until the current URL exactly matches the expected URL.
     */
    public void waitUntilUrlIs(String expectedUrl) {
        waitUntilUrlIs(expectedUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until the current URL exactly matches the expected URL,
     * using a custom timeout (in seconds).
     */
    public void waitUntilUrlIs(String expectedUrl, int timeoutSeconds) {
        newWait(timeoutSeconds).until(ExpectedConditions.urlToBe(expectedUrl));
    }

    /**
     * Returns the current browser URL.
     */
    protected String getCurrentUrl() {
        return driver.getCurrentUrl();
    }
}
