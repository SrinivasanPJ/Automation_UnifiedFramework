package com.AutoPOC.base;

import com.AutoPOC.utils.context.TestContextManager;
import com.AutoPOC.utils.core.DriverFactory;
import com.AutoPOC.utils.reporting.ExtentReportManager;
import com.AutoPOC.utils.reporting.LogUtil;
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
 * Abstract base class for all Page Object classes within the Automation POC framework.
 * <p>
 * Encapsulates common actions and utility methods to reduce duplication and promote reusable, readable code.
 */
public abstract class BasePage {
    private static final Logger logger = LoggerFactory.getLogger(BasePage.class);

    protected final WebDriver driver;
    private static final int DEFAULT_TIMEOUT = 10;

    /**
     * Initializes the WebDriver instance and page elements.
     */
    protected BasePage() {
        this.driver = DriverFactory.getDriver();
        PageFactory.initElements(driver, this);
    }

    // ───── Logging and Test Context ─────────────────────────────────────

    /**
     * Logs a message to the reporting framework.
     *
     * @param message the message to log
     */
    protected void log(String message) {
        ExtentReportManager.INSTANCE.logInfo(message, this.getClass());
    }

    /**
     * Retrieves input data associated with the current test context.
     *
     * @return a map of input data
     */
    protected Map<String, String> getInputData() {
        return TestContextManager.getInputData();
    }

    // ───── Basic Element Actions ───────────────────────────────────────

    /**
     * Clicks on the specified WebElement and logs the action.
     *
     * @param element the WebElement to click
     * @param logMsg  the message to log after the click
     */
    public void click(WebElement element, String logMsg) {
        waitUntilClickable(element, DEFAULT_TIMEOUT).click();
        log(logMsg);
    }

    /**
     * Clicks on the element found by the given locator and logs the action.
     *
     * @param locator the By locator of the element to click
     * @param logMsg  the message to log after the click
     */
    public void click(By locator, String logMsg) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        element.click();
        log(logMsg);
    }

    /**
     * Clears existing text and enters new text into an input field.
     *
     * @param element the input element
     * @param text    the text to enter
     */
    protected void sendKeys(WebElement element, String text) {
        WebElement visibleElement = waitUntilVisible(element, DEFAULT_TIMEOUT);
        visibleElement.clear();
        visibleElement.sendKeys(text);
    }

    /**
     * Selects an option from a dropdown using visible text.
     *
     * @param dropdown the dropdown WebElement
     * @param text     the visible text to select
     */
    protected void selectByVisibleText(WebElement dropdown, String text) {
        new Select(dropdown).selectByVisibleText(text);
    }

    /**
     * Retrieves trimmed text from a WebElement.
     *
     * @param element the WebElement to read text from
     * @return the trimmed text
     */
    protected String getText(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT).getText().trim();
    }

    /**
     * Checks if a WebElement is visible within the default timeout.
     *
     * @param element the WebElement to check
     * @return true if visible; false otherwise
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
     * Waits until a WebElement is no longer visible (invisible in DOM or hidden via style).
     *
     * @param element the WebElement to monitor
     * @return true if the element becomes invisible within timeout; false otherwise
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

    // ───── Dynamic Actions ─────────────────────────────────────────────

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
        scrollIntoView(element); // Ensures it’s visible in viewport
        element.click();
        log("Clicked " + fieldName + ": " + rawValue);
    }

    /**
     * Scrolls the page to bring the element into view.
     *
     * @param element the target element
     */
    public void scrollIntoView(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", element);
    }

    /**
     * Scrolls the page to bring the element into view using its locator.
     *
     * @param locator the By locator of the element
     */
    public void scrollIntoView(By locator) {
        WebElement element = driver.findElement(locator);
        scrollIntoView(element);
    }

    public void clickBy(By locator) {
        WebElement element = waitUntilClickable(locator, DEFAULT_TIMEOUT);
        scrollIntoView(element);
        element.click();
        log("Clicked element by locator: " + locator);
    }

    /**
     * Clicks a WebElement using JavaScript.
     *
     * @param element the WebElement to click
     */
    public void jsClick(WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
    }

    /**
     * Hovers over a WebElement using mouse actions.
     *
     * @param element the WebElement to hover over
     */
    public void hoverOverElement(WebElement element) {
        new Actions(driver).moveToElement(element).perform();
    }

    /**
     * Checks if any elements matching the locator are present in the DOM.
     *
     * @param locator the element locator
     * @return true if at least one match is found; false otherwise
     */
    public boolean isElementPresent(By locator) {
        try {
            return driver.findElement(locator).isDisplayed();
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Gets an attribute's value from a visible WebElement.
     *
     * @param element   the WebElement
     * @param attribute the attribute name
     * @return the attribute value
     */
    public String getAttribute(WebElement element, String attribute) {
        return waitUntilVisible(element).getAttribute(attribute);
    }

    /**
     * Waits until the number of elements matching the locator equals the expected count.
     *
     * @param locator       the element locator
     * @param expectedCount the expected number of elements
     */
    public void waitForElementCount(By locator, int expectedCount) {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT))
                .until(d -> d.findElements(locator).size() == expectedCount);
    }

    /**
     * Waits until the page's JavaScript ready state is complete.
     */
    public void waitForPageToLoad() {
        new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT)).until(
                d -> ((JavascriptExecutor) d).executeScript("return document.readyState").equals("complete")
        );
    }

    // ───── Wait Utilities ─────────────────────────────────────────────

    /**
     * Waits until the element is clickable using the default timeout.
     *
     * @param element the WebElement to wait for
     * @return the clickable WebElement
     */
    protected WebElement waitUntilClickable(WebElement element) {
        return waitUntilClickable(element, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until the element is clickable using a custom timeout.
     *
     * @param element the WebElement to wait for
     * @param timeout timeout in seconds
     * @return the clickable WebElement
     */
    protected WebElement waitUntilClickable(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(element));
    }

    /**
     * Waits until the element is visible using the default timeout.
     *
     * @param element the WebElement to wait for
     * @return the visible WebElement
     */
    protected WebElement waitUntilVisible(WebElement element) {
        return waitUntilVisible(element, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until the element is visible using a custom timeout.
     *
     * @param element the WebElement to wait for
     * @param timeout timeout in seconds
     * @return the visible WebElement
     */
    protected WebElement waitUntilVisible(WebElement element, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOf(element));
    }

    /**
     * Waits until the element located by the given locator is visible using a custom timeout.
     *
     * @param locator the By locator to wait for
     * @param timeout timeout in seconds
     * @return the visible WebElement
     */
    protected WebElement waitUntilVisible(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    /**
     * Waits until the element located by the given locator is clickable using a custom timeout.
     *
     * @param locator the By locator to wait for
     * @param timeout timeout in seconds
     * @return the clickable WebElement
     */
    protected WebElement waitUntilClickable(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.elementToBeClickable(locator));
    }

    /**
     * Waits until specific text is present within the WebElement.
     *
     * @param element the WebElement to inspect
     * @param text    the text to wait for
     * @param timeout timeout in seconds
     */
    protected void waitUntilTextPresent(WebElement element, String text, int timeout) {
        new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.textToBePresentInElement(element, text));
    }

    // ───── Page-Specific Utilities ─────────────────────────────────────

    /**
     * Returns the number of address sections currently visible on the page.
     *
     * @return the count of address sections
     */
    public int getNumberOfAddresses() {
        return driver.findElements(By.xpath("//div[@class='address-list']//div[contains(@class, 'section')]")).size();
    }

    /**
     * Closes the top notification banner (e.g., "Product added to cart") if it is displayed.
     * Waits until the banner is no longer visible before proceeding.
     */
    public void closeNotificationIfPresent() {
        try {
            WebElement bar = driver.findElement(By.id("bar-notification"));
            WebElement closeBtn = bar.findElement(By.className("close"));

            if (bar.isDisplayed() && closeBtn.isDisplayed()) {
                closeBtn.click();
                waitUntilElementGone(bar); // Wait until fade out is complete
                logger.info("Notification banner closed successfully.");
            }
        } catch (NoSuchElementException | TimeoutException ignored) {
            logger.debug("Notification banner not present or already dismissed.");
        } catch (Exception e) {
            logger.warn("Error while closing notification banner: {}", e.getMessage());
        }
    }

    /**
     * Waits until an element located by the given locator is no longer visible on the page.
     *
     * @param locator By locator of the element to wait for
     * @param timeout Timeout in seconds
     * @return true if element disappears within timeout; false otherwise
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

    /**
     * Overloaded method with default timeout.
     */
    public boolean waitUntilGone(By locator) {
        return waitUntilGone(locator, DEFAULT_TIMEOUT);
    }

    /**
     * Waits until an element identified by locator becomes invisible.
     */
    public void waitForElementToDisappear(By locator, int timeoutSeconds) {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
                    .until(ExpectedConditions.invisibilityOfElementLocated(locator));
        } catch (TimeoutException e) {
            LogUtil.warn(getClass(), "Notification did not disappear within timeout.");
        }
    }

    public void closeNotificationIfPresentAndWait() {
        try {
            WebElement bar = driver.findElement(By.id("bar-notification"));
            if (bar.isDisplayed()) {
                WebElement closeBtn = bar.findElement(By.className("close"));
                closeBtn.click();
                log("Clicked on Close button in notification banner.");
                waitUntilGone(By.id("bar-notification"), 10);
            }
        } catch (NoSuchElementException ignored) {
        } catch (Exception e) {
            logger.warn("Error during closing banner: {}", e.getMessage());
        }
    }

    public String generateRandomCardholderName() {
        Faker faker = new Faker();
        return faker.name().fullName();
    }
}
