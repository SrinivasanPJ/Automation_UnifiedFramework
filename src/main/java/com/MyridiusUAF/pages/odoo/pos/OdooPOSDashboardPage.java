package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page Object: POS Dashboard (entry screen before the register UI).
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Handle optional “Continue Selling” interstitial</li>
 *   <li>Start a fresh “New Order” when the button is present</li>
 * </ul>
 */
public class OdooPOSDashboardPage extends BasePage {

    /* ------------------------ Locators ------------------------ */

    // “Continue Selling” appears when there’s an open session/order to resume
    private static final By CONTINUE_SELLING = By.xpath(
            "//button[@name='open_ui' or normalize-space()='Continue Selling' or contains(normalize-space(),'Continue Selling')]"
    );

    // “New Order” to start a fresh order; some themes include an icon + text, others only a class
    private static final By NEW_ORDER = By.xpath(
            "//button[contains(@class,'new-order') or normalize-space()='New Order' or .//span[normalize-space()='New Order']]"
    );

    /* ---------------------- Construction ---------------------- */

    public OdooPOSDashboardPage(WebDriver driver) {
        super(driver);
    }

    /* ------------------------ Actions ------------------------- */

    /**
     * Clicks “Continue Selling” if the interstitial is visible.
     * Silent no-op when absent.
     */
    public void continueSellingIfVisible() {
        if (isPresent(CONTINUE_SELLING, 15)) {
            click(CONTINUE_SELLING, "Clicked on Continue Selling button");
        }
    }

    /**
     * Clicks “New Order” if the button is visible.
     * Silent no-op when absent.
     */
    public void clickNewOrderIfVisible() {
        if (isPresent(NEW_ORDER, 20)) {
            click(NEW_ORDER, "Clicked on New Order button");
        }
    }

    /**
     * Convenience: proceed past interstitials and open a new order when possible.
     * Safe to call even if only one of the buttons is present.
     */
    public void proceedToNewOrderIfPossible() {
        continueSellingIfVisible();
        clickNewOrderIfVisible();
    }
}
