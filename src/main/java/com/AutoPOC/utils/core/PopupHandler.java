package com.AutoPOC.utils.core;

import com.AutoPOC.base.BasePage;
import org.openqa.selenium.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for handling dynamic popups during test execution,
 * such as browser "Save Password" dialogs or unexpected overlays.
 */
public class PopupHandler {

    private static final Logger logger = LoggerFactory.getLogger(PopupHandler.class);

    /**
     * Attempts to dismiss the browser "Save Password" popup if it appears.
     * <p>
     * Silently continues execution if the popup is not found.
     */
    public static void dismissSavePasswordPopup() {
        try {
            WebDriver driver = DriverFactory.getDriver();
            BasePage basePage = new BasePage() {}; // Anonymous subclass to access BasePage methods

            WebElement savePasswordPopupButton = driver.findElement(By.xpath(
                    "//button[contains(@aria-label, 'Save password') or contains(@aria-label, 'Never')]"
            ));

            if (basePage.isDisplayed(savePasswordPopupButton)) {
                basePage.click(savePasswordPopupButton, "Dismissed Save Password popup");
                logger.info("Save Password popup dismissed successfully.");
            }

        } catch (NoSuchElementException | TimeoutException e) {
            logger.info("Save Password popup not displayed, proceeding without action.");
        } catch (Exception e) {
            logger.error("Unexpected error while handling Save Password popup.", e);
        }
    }
}
