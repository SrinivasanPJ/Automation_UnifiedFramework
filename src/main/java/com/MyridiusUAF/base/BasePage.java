package com.MyridiusUAF.base;

import com.MyridiusUAF.utils.context.TestContextManager;
import com.MyridiusUAF.utils.core.DriverFactory;
import com.MyridiusUAF.utils.reporting.ExtentReportManager;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for all Page Object classes within the Automation POC framework.
 * <p>
 * Encapsulates common actions and utility methods to reduce duplication and promote reusable, readable code.
 */
public abstract class BasePage {

    protected final WebDriver driver;
    private static final int DEFAULT_TIMEOUT = 60;
    private static final Logger logger = LoggerFactory.getLogger(BasePage.class);

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

    public void cardPaymentModalHeader() {
        // Switch to iframe
        WebElement iframe = driver.findElement(By.id("service-provider-416-frame"));
        driver.switchTo().frame(iframe);
        WebElement heading = new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.visibilityOfElementLocated(
                        By.xpath("//h1[contains(text(), 'Card Payment')]")
                ));
        if (heading.isDisplayed()) {
            System.out.println("Card Payment heading is displayed.");
        } else {
            throw new AssertionError("Card Payment heading not displayed.");
        }
    }
    public void scrollToBottom() {
        ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    public boolean isDisplayed(WebElement element, int timeout) {
        try {
            driver.switchTo().defaultContent();

            // First check main DOM
            try {
                if (waitUntilVisible(element, timeout) != null) {
                    logger.info("Element is displayed in the main DOM (outside iframe).");
                    return true;
                }
            } catch (TimeoutException e) {
                logger.debug("Element not found in main DOM, checking iframes...");
            }

            // Check each iframe
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            for (int i = 0; i < iframes.size(); i++) {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);

                try {
                    if (waitUntilVisible(element, timeout) != null) {
                        logger.info("Element is displayed inside iframe index: {}", i);
                        driver.switchTo().defaultContent();
                        return true;
                    }
                } catch (TimeoutException e) {
                    logger.debug("Element not found in iframe index: {}", i);
                }
            }

        } catch (Exception e) {
            logger.error("Error while checking element display status", e);
        } finally {
            driver.switchTo().defaultContent();
        }

        logger.info("Element is not displayed in main DOM or any iframe.");
        return false;
    }

    /**
     * Waits until a WebElement is no longer visible.
     *
     * @param element the WebElement to monitor
     * @return true if the element becomes invisible; false otherwise
     */
    public boolean waitUntilElementGone(WebElement element) {
        try {
            return new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT))
                    .until(ExpectedConditions.invisibilityOf(element));
        } catch (TimeoutException e) {
            return false;
        }
    }

    // ───── Dynamic Actions ─────────────────────────────────────────────

    /**
     * Builds a dynamic XPath using the raw value and clicks the resulting element.
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
        click(driver.findElement(By.xpath(xpath)), "Clicked " + fieldName + ": " + rawValue);
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
        return !driver.findElements(locator).isEmpty();
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

    protected WebElement waitUntilVisible(By locator, int timeout) {
        return new WebDriverWait(driver, Duration.ofSeconds(timeout))
                .until(ExpectedConditions.visibilityOfElementLocated(locator));
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

    public static ExpectedCondition<WebDriver> frameToBeAvailableAndSwitchToIt(final By locator) {
        return new ExpectedCondition<WebDriver>() {
            @Override
            public WebDriver apply(WebDriver driver) {
                try {
                    return driver.switchTo().frame(driver.findElement(locator));
                } catch (NoSuchFrameException e) {
                    return null;
                } catch (StaleElementReferenceException e) {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "frame to be available: " + locator;
            }
        };
    }

    public static ExpectedCondition<WebDriver> frameToBeAvailableAndSwitchToIt(final WebElement frameLocator) {
        return new ExpectedCondition<WebDriver>() {
            @Override
            public WebDriver apply(WebDriver driver) {
                try {
                    return driver.switchTo().frame(frameLocator);
                } catch (NoSuchFrameException e) {
                    return null;
                } catch (StaleElementReferenceException e) {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "frame to be available: " + frameLocator;
            }
        };
    }

    public static ExpectedCondition<WebDriver> frameToBeAvailableAndSwitchToIt(final int frameLocator) {
        return new ExpectedCondition<WebDriver>() {
            @Override
            public WebDriver apply(WebDriver driver) {
                try {
                    return driver.switchTo().frame(frameLocator);
                } catch (NoSuchFrameException | StaleElementReferenceException e) {
                    return null;
                }
            }

            @Override
            public String toString() {
                return "frame to be available: " + frameLocator;
            }
        };
    }

    /**
     * Switch back to default content (main document).
     */
    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
        log("Switched to default content.");
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
}
