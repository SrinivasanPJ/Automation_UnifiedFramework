package com.MyridiusUAF.base;

import com.MyridiusUAF.utils.context.TestContextManager;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import com.github.javafaker.Faker;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.*;
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
    private static final int DEFAULT_TIMEOUT = 10;

    protected final WebDriver driver;

    /**
     * Constructor initializes driver and web elements.
     */
    protected BasePage() {
        this.driver = DriverFactory.getDriver();
        PageFactory.initElements(driver, this);
    }

    // ───── Logging & Context ──────────────────────────────────────────────

    /**
     * Logs an info message to ExtentReport and log output.
     * @param message Message to log
     */
    protected void log(String message) {
        ExtentReportManager.INSTANCE.logInfo(message, this.getClass());
    }

    /**
     * Gets the current test input data map from context.
     */
    protected Map<String, String> getInputData() {
        return TestContextManager.getInputData();
    }

    // ───── Basic Element Actions ──────────────────────────────────────────

    /** Click element and log action. */
    public void click(WebElement element, String logMsg) {
        waitUntilClickable(element, DEFAULT_TIMEOUT).click();
        log(logMsg);
    }

    /** Click by locator and log action. */
    public void click(By locator, String logMsg) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        element.click();
        log(logMsg);
    }

    /** Clear and enter text. */
    protected void sendKeys(WebElement element, String text) {
        WebElement visibleElement = waitUntilVisible(element, DEFAULT_TIMEOUT);
        visibleElement.clear();
        visibleElement.sendKeys(text);
    }

    /** Select dropdown by visible text. */
    protected void selectByVisibleText(WebElement dropdown, String text) {
        new Select(dropdown).selectByVisibleText(text);
    }

    /** Get trimmed text. */
    protected String getText(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT).getText().trim();
    }

    /** Check if element is visible (default timeout). */
    public boolean isDisplayed(WebElement element) {
        try {
            waitUntilVisible(element, DEFAULT_TIMEOUT);
            return true;
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ───── Wait Utilities ────────────────────────────────────────────────

    /** Wait until clickable by element. */
    protected WebElement waitUntilClickable(WebElement element) {
        return waitUntilClickable(element, DEFAULT_TIMEOUT);
    }

    /** Wait until clickable by element with custom timeout. */
    protected WebElement waitUntilClickable(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(element));
    }

    /** Wait until clickable by locator. */
    protected WebElement waitUntilClickable(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    /** Wait until visible by element. */
    protected WebElement waitUntilVisible(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT);
    }

    /** Wait until visible by element with timeout. */
    protected WebElement waitUntilVisible(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOf(element));
    }

    /** Wait until visible by locator. */
    protected WebElement waitUntilVisible(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /** Wait until text present. */
    protected void waitUntilTextPresent(WebElement element, String text, int timeout) {
        new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.textToBePresentInElement(element, text));
    }

    /** Wait until element is gone (not visible or removed). */
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

    /** Wait until element gone by locator. */
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

    /** Wait for page ready state to be 'complete'. */
    public void waitForPageToLoad() {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT)).until(
                d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
        );
    }

    /** Wait for element count to equal expected. */
    public void waitForElementCount(By locator, int expectedCount) {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT))
                .until(d -> d.findElements(locator).size() == expectedCount);
    }

    // ───── Advanced Actions ─────────────────────────────────────────────

    /** Scroll element into view using JS. */
    public void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    public void scrollIntoView(By locator) {
        WebElement element = driver.findElement(locator);
        scrollIntoView(element);
    }

    /** Scroll to page bottom. */
    public void scrollToBottom() {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    /** Click by locator, scroll into view. */
    public void clickBy(By locator) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked element by locator: " + locator);
    }

    /** Click element using JavaScript. */
    public void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    /** Hover over element. */
    public void hoverOverElement(WebElement element) {
        new Actions(driver).moveToElement(element).perform();
    }

    /** Is element present and displayed (by locator)? */
    public boolean isElementPresent(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /** Get attribute from visible element. */
    public String getAttribute(WebElement element, String attribute) {
        return waitUntilVisible(element).getAttribute(attribute);
    }

    // ───── Page-Specific Utilities ─────────────────────────────────────

    /** Get the number of address sections visible. */
    public int getNumberOfAddresses() {
        return driver.findElements(By.xpath("//div[@class='address-list']//div[contains(@class, 'section')]")).size();
    }

    /** Close top notification (if present) and wait for it to disappear. */
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

    // ───── Dynamic Element Actions ─────────────────────────────────────

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

    // ───── Miscellaneous Utilities ─────────────────────────────────────

    /** Generates a random cardholder name (uses Java Faker). */
    public String generateRandomCardholderName() {
        Faker faker = new Faker();
        return faker.name().fullName();
    }

    // ───── Frame Handling Utilities ─────────────────────────────────────

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
}
