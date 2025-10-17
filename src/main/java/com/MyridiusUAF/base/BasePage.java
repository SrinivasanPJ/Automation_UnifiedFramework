package com.MyridiusUAF.base;

import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.clients.OpenAiClient;
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
 * <p>
 * Provides reusable element actions, wait logic, scrolling, notifications, and logging utilities.
 * Promotes DRY, readable, and maintainable automation code.
 */
public abstract class BasePage {
    private static final Logger logger = LoggerFactory.getLogger(BasePage.class);
    private static final int DEFAULT_TIMEOUT = 20;

    protected final WebDriver driver;
    protected final LlmClient llm;

    /**
     * Constructor initializes driver and web elements with AI healing.
     * Uses OpenAiClient (reads OPENAI_API_KEY) by default, but degrades gracefully if not present.
     */
    protected BasePage() {
        this(DriverFactory.getDriver());
    }

    /**
     * Constructor that lets callers provide a driver and LLM client.
     */
    protected BasePage(WebDriver driver) {
        this.driver = driver;
        // decide healing + LLM here
        boolean healingEnabled = Boolean.parseBoolean(
                ConfigReader.getProperty("ai.selfhealing.enabled", "true"));
        boolean useLlm = Boolean.parseBoolean(
                ConfigReader.getProperty("ai.healing.use.llm", "true"));
        boolean openaiEnabled = Boolean.parseBoolean(
                ConfigReader.getProperty("ai.openai.enabled", "true"));
        boolean apiPresent = System.getenv("OPENAI_API_KEY") != null
                && !System.getenv("OPENAI_API_KEY").isBlank();

        this.llm = (healingEnabled && useLlm && openaiEnabled && apiPresent)
                ? createLlmOrNull()
                : null;

        if (healingEnabled) {
            PageFactory.initElements(new HealingFieldDecorator(driver, llm), this);
            // logger.debug("PageFactory: Self-Healing ENABLED (LLM: {})", (llm != null ? "ON" : "OFF"));
        } else {
            PageFactory.initElements(new DefaultElementLocatorFactory(driver), this);
            // logger.debug("PageFactory: Self-Healing DISABLED");
        }
    }

    /**
     * Best-effort LLM creation that won’t blow up when OPENAI_API_KEY is missing.
     */
    private static LlmClient createSafeLlm() {
        try {
            return new OpenAiClient();
        } catch (Exception e) {
            // Keep tests running without AI; SelfHealingLocator still uses heuristics.
            logger.warn("AI healing disabled (LLM client not available): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Safely quote an arbitrary string as an XPath literal.
     * <p>
     * If the string contains both single and double quotes, it uses XPath concat().
     * Otherwise it wraps the value with the available quote type.
     */
    protected static String xPathLiteral(String value) {
        if (value == null) return "''";
        if (value.contains("'") && value.contains("\"")) {
            String[] parts = value.split("\"", -1); // keep trailing empty segment if any
            StringBuilder sb = new StringBuilder("concat(");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append(", '\"', ");
                sb.append("\"").append(parts[i]).append("\"");
            }
            sb.append(")");
            return sb.toString();
        }
        if (value.contains("'")) {
            return "\"" + value + "\"";
        }
        return "'" + value + "'";
    }

    // ───── Logging & Context ──────────────────────────────────────────────

    private LlmClient createLlmOrNull() {
        try {
            return new OpenAiClient();
        } catch (Exception e) {
            logger.info("Self-healing LLM OFF (client init failed): {}", e.toString());
            return null;
        }
    }

    /**
     * Logs an info message to ExtentReport and log output.
     *
     * @param message Message to log
     */
    protected void log(String message) {
        ExtentReportManager.INSTANCE.logInfo(message, this.getClass());
    }

    // ───── Basic Element Actions ──────────────────────────────────────────

    /**
     * Gets the current test input data map from context.
     */
    protected Map<String, String> getInputData() {
        return TestContextManager.getInputData();
    }

    /**
     * Click element and log action.
     */
    public void click(WebElement element, String logMsg) {
        waitUntilClickable(element, 30).click();
        log(logMsg);
    }

    /**
     * Click by locator and log action.
     */
    public void click(By locator, String logMsg) {
        WebElement element = waitUntilClickable(locator, 30);
        element.click();
        log(logMsg);
    }

    /**
     * Clear and enter text.
     */
    protected void sendKeys(WebElement element, String text) {
        WebElement visibleElement = waitUntilVisible(element, DEFAULT_TIMEOUT);
        visibleElement.clear();
        visibleElement.sendKeys(text);
    }

    /**
     * Select dropdown by visible text.
     */
    protected void selectByVisibleText(WebElement dropdown, String text) {
        new Select(dropdown).selectByVisibleText(text);
    }

    /**
     * Get trimmed text.
     */
    protected String getText(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT).getText().trim();
    }

    // ───── Wait Utilities ────────────────────────────────────────────────

    /**
     * Check if element is visible (default timeout).
     */
    public boolean isDisplayed(WebElement element) {
        try {
            waitUntilVisible(element, DEFAULT_TIMEOUT);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    /**
     * Wait until clickable by element.
     */
    protected WebElement waitUntilClickable(WebElement element) {
        return waitUntilClickable(element, DEFAULT_TIMEOUT);
    }

    /**
     * Wait until clickable by element with custom timeout.
     */
    protected WebElement waitUntilClickable(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(element));
    }

    /**
     * Wait until clickable by locator.
     */
    protected WebElement waitUntilClickable(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Wait until visible by element.
     */
    protected WebElement waitUntilVisible(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT);
    }

    /**
     * Wait until visible by element with timeout.
     */
    protected WebElement waitUntilVisible(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOf(element));
    }

    /**
     * Wait until visible by locator.
     */
    protected WebElement waitUntilVisible(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Wait until text present.
     */
    protected void waitUntilTextPresent(WebElement element, String text, int timeout) {
        new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.textToBePresentInElement(element, text));
    }

    /**
     * Wait until element is gone (not visible or removed).
     */
    public boolean waitUntilElementGone(WebElement element) {
        try {
            boolean isGone = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT))
                    .until(ExpectedConditions.invisibilityOf(element));
            logger.info("Element became invisible: {}", element);
            return isGone;
        } catch (TimeoutException e) {
            logger.warn("Timeout: Element did not disappear: {}", element);
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error while waiting for element to disappear: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Wait until element gone by locator.
     */
    public boolean waitUntilGone(By locator, int timeout) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                    .until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            log("Element with locator [" + locator.toString() + "] did not disappear within " + timeout + " seconds.");
            return false;
        }
    }

    public boolean waitUntilGone(By locator) {
        return waitUntilGone(locator, DEFAULT_TIMEOUT);
    }

    /**
     * Wait for page ready state to be 'complete'.
     */
    public void waitForPageToLoad() {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT)).until(
                d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
        );
    }

    // ───── Advanced Actions ─────────────────────────────────────────────

    /**
     * Wait for element count to equal expected.
     */
    public void waitForElementCount(By locator, int expectedCount) {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT))
                .until(d -> d.findElements(locator).size() == expectedCount);
    }

    /**
     * Scroll element into view using JS.
     */
    public void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    public void scrollIntoView(By locator) {
        WebElement element = driver.findElement(locator);
        scrollIntoView(element);
    }

    /**
     * Scroll to page bottom.
     */
    public void scrollToBottom() {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    /**
     * Click by locator, scroll into view.
     */
    public void clickBy(By locator) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked element by locator: " + locator);
    }

    /**
     * Click element using JavaScript.
     */
    public void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    /**
     * Hover over element.
     */
    public void hoverOverElement(WebElement element) {
        new Actions(driver).moveToElement(element).perform();
    }

    /**
     * Is element present and displayed (by locator)?
     */
    public boolean isElementPresent(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    // ───── Page-Specific Utilities ─────────────────────────────────────

    /**
     * Get attribute from visible element.
     */
    public String getAttribute(WebElement element, String attribute) {
        return waitUntilVisible(element).getAttribute(attribute);
    }

    /**
     * Get the number of address sections visible.
     */
    public int getNumberOfAddresses() {
        return driver.findElements(By.xpath("//div[@class='address-list']//div[contains(@class, 'section')]")).size();
    }

    // ───── Dynamic Element Actions ─────────────────────────────────────

    /**
     * Close top notification (if present) and wait for it to disappear.
     */
    public void closeNotificationIfPresentAndWait() {
        try {
            WebElement bar = driver.findElement(By.id("bar-notification"));
            if (bar.isDisplayed()) {
                WebElement closeBtn = bar.findElement(By.className("close"));
                closeBtn.click();
                log("Clicked on Close button in notification banner.");
                waitUntilGone(By.id("bar-notification"), 10);
                logger.info("Notification banner closed successfully.");
            }
        } catch (NoSuchElementException | TimeoutException ignored) {
            logger.debug("Notification banner not present or already dismissed.");
        } catch (Exception e) {
            logger.warn("Error while closing notification banner: {}", e.getMessage());
        }
    }

    // ───── Miscellaneous Utilities ─────────────────────────────────────

    /**
     * Builds a dynamic XPath using the raw value, waits for visibility and clickability, then clicks it.
     *
     * @param fieldName     name for logging purposes
     * @param rawValue      the value to inject into the XPath
     * @param xpathTemplate the XPath template with a %s placeholder
     */
    protected void clickBy(String fieldName, String rawValue, String xpathTemplate) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is missing!");
        }
        String xpath = String.format(xpathTemplate, rawValue.trim());
        By locator = By.xpath(xpath);

        log("Waiting for " + fieldName + ": " + rawValue);
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked " + fieldName + ": " + rawValue);
    }

    // ───── Frame Handling Utilities ─────────────────────────────────────

    /**
     * Generates a random cardholder name (uses Java Faker).
     */
    public String generateRandomCardholderName() {
        Faker faker = new Faker();
        return faker.name().fullName();
    }

    /**
     * Switch to frame by index.
     */
    public void switchToFrame(int index) {
        try {
            driver.switchTo().frame(index);
            log("Switched to frame by index: " + index);
        } catch (NoSuchFrameException e) {
            log("No frame found with index: " + index);
            throw e;
        }
    }

    /**
     * Switch to frame by name or ID.
     */
    public void switchToFrame(String nameOrId) {
        try {
            driver.switchTo().frame(nameOrId);
            log("Switched to frame by name/ID: " + nameOrId);
        } catch (NoSuchFrameException e) {
            log("No frame found with name/ID: " + nameOrId);
            throw e;
        }
    }

    /**
     * Switch to frame by WebElement (default timeout).
     */
    public void switchToFrame(WebElement frameElement) {
        switchToFrame(frameElement, DEFAULT_TIMEOUT);
    }

    /**
     * Switch to frame by WebElement with timeout.
     */
    public void switchToFrame(WebElement frameElement, int timeoutInSeconds) {
        try {
            waitUntilVisible(frameElement, timeoutInSeconds);  // Ensures frame is present
            driver.switchTo().frame(frameElement);
            log("Switched to frame via WebElement.");
        } catch (NoSuchFrameException | StaleElementReferenceException e) {
            log("Unable to switch to frame via WebElement.");
            throw e;
        }
    }

    /**
     * Switch to frame by locator (default timeout).
     */
    public void switchToFrame(By locator) {
        switchToFrame(locator, DEFAULT_TIMEOUT);
    }

    /**
     * Switch to frame by locator with timeout.
     */
    public void switchToFrame(By locator, int timeoutInSeconds) {
        try {
            WebElement frameElement = waitUntilVisible(locator, timeoutInSeconds);
            driver.switchTo().frame(frameElement);
            log("Switched to frame via locator: " + locator.toString());
        } catch (NoSuchFrameException | TimeoutException e) {
            log("Unable to switch to frame via locator: " + locator.toString());
            throw e;
        }
    }

    /**
     * Switch back to default content (main document).
     */
    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
        log("Switched to default content.");
    }

    protected boolean isPresent(By locator, long seconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(seconds))
                    .ignoring(NoSuchElementException.class)
                    .ignoring(StaleElementReferenceException.class)
                    .until(d -> !d.findElements(locator).isEmpty());
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    protected WebElement waitUntilVisible(By locator, long seconds) {
        return new WebDriverWait(driver, Duration.ofSeconds(seconds))
                .ignoring(StaleElementReferenceException.class)
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    // ───── XPath Utilities ──────────────────────────────────────────────────

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
