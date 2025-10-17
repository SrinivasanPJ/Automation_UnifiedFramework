package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

/** Payment screen actions (robust selectors + clear assertions). */
public class OdooPOSPaymentPage extends BasePage {

    public OdooPOSPaymentPage(WebDriver d){ super(d); }

    // =========================
    // Left-side payment methods
    // =========================

    /** Remove inline comments and squeeze whitespace. */
    private String sanitizeMethod(String raw) {
        if (raw == null) return "";
        return raw.split("#", 2)[0].replaceAll("\\s+", " ").trim();
    }

    /** STRICT: find the button by its payment-name span (case-insensitive). */
    private By strictMethodButton(String rawMethod) {
        final String needle = sanitizeMethod(rawMethod).toLowerCase();
        // Match span.payment-name text (case-insensitive) then go up to the clickable tile
        return By.xpath(
                "//span[contains(@class,'payment-name')]" +
                        "[translate(normalize-space(.),'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz')='" + needle + "']" +
                        "/ancestor::*[contains(@class,'button') and contains(@class,'paymentmethod')][1]"
        );
    }

    /** FALLBACK: all tiles; we’ll filter by text in code. */
    private By allMethodTiles() {
        // What your DOM shows (screenshot): div.button.paymentmethod ...
        // We include button/div just in case themes vary.
        return By.cssSelector(".paymentmethods-container .button.paymentmethod, .paymentmethods-container button.paymentmethod");
    }

    /** Picks the desired payment method on the left (e.g., "Cash"). */
    public void choosePaymentMethod(String methodName) {
        final String expected = sanitizeMethod(methodName);
        final String expectedLc = expected.toLowerCase();

        WebElement target = null;

        // 1) Try strict XPath that anchors on span.payment-name
        try {
            target = new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(strictMethodButton(expected)));
        } catch (TimeoutException ignore) {}

        // 2) Fallback: scan all tiles and match the visible payment-name text
        if (target == null) {
            for (WebElement tile : driver.findElements(allMethodTiles())) {
                try {
                    WebElement label = tile.findElement(By.cssSelector(".payment-name"));
                    String txt = label.getText() == null ? "" : label.getText().trim().toLowerCase();
                    if (txt.equals(expectedLc) || txt.contains(expectedLc)) {
                        target = tile;
                        break;
                    }
                } catch (NoSuchElementException ignore) {}
            }
        }

        if (target == null) {
            throw new NoSuchElementException("Payment method tile not found: '" + expected + "'");
        }

        // Ensure clickable & on-screen, then click with fallbacks
        new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.elementToBeClickable(target));
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});", target);
        } catch (Exception ignore) {}

        try {
            // small move to avoid overlapping tooltips or sticky headers
            new Actions(driver).moveToElement(target, 5, 5).pause(Duration.ofMillis(100)).click().perform();
        } catch (Exception primary) {
            try {
                target.click();
            } catch (Exception intercepted) {
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", target);
            }
        }
        log("Choose payment method: " + expected);
    }

    // =========================
    // Center “pill” verification
    // =========================
    private static final By PAYMENT_LINE           = By.cssSelector(".payment-infos .paymentline, .paymentline.selected, .paymentline");
    private static final By PAYMENT_NAME_IN_LINE   = By.cssSelector(".payment-name");
    private static final By PAYMENT_AMOUNT_IN_LINE = By.cssSelector(".payment-amount, .oe_currency_value");

    /**
     * Asserts the center payment pill shows the same method and full amount.
     * Uses explicit fields first; falls back to full text contains.
     */
    public void assertCenterPayment(String expectedMethodRaw, double expectedAmount) {
        final String expectedMethod = sanitizeMethod(expectedMethodRaw);

        WebElement line = waitUntilVisible(PAYMENT_LINE, 10);

        String method = "";
        String amount = "";

        try { method = line.findElement(PAYMENT_NAME_IN_LINE).getText().trim(); } catch (NoSuchElementException ignore) {}
        try { amount = line.findElement(PAYMENT_AMOUNT_IN_LINE).getText().trim(); } catch (NoSuchElementException ignore) {}

        if (method.isBlank()) method = line.getText().trim();
        double amt = amount.isBlank() ? parseMoney(line.getText()) : parseMoney(amount);

        if (!method.toLowerCase().contains(expectedMethod.toLowerCase())) {
            throw new AssertionError("Center payment method mismatch. Expected contains: "
                    + expectedMethod + " but got: " + method);
        }
        String expected2dp = String.format("%.2f", expectedAmount).replace(',', '.');
        String actual2dp   = String.format("%.2f", amt).replace(',', '.');
        if (!expected2dp.equals(actual2dp)) {
            throw new AssertionError("Payment amount mismatch. Expected: " + expected2dp + " but got: " + actual2dp);
        }
        log("Center payment OK: " + expectedMethod + " " + actual2dp);
    }

    // =========================
    // Validate + optional Order
    // =========================
    private static final By VALIDATE_BTN = By.xpath(
            "//button[contains(@class,'validation-button') or .//span[normalize-space()='Validate'] " +
                    "or normalize-space()='Validate' or normalize-space()='Pay' or normalize-space()='Validate Order']"
    );
    private static final By WARNING_MODAL     = By.className("modal-content");
    private static final By WARNING_ORDER_BTN = By.xpath("//div[@class='modal-content']/descendant::button[normalize-space()='Order']");

    /** Clicks Validate and handles optional 'Order' confirmation modal. */
    public void validateAndConfirmOrderIfPrompted() {
        click(VALIDATE_BTN, "Clicked on Validate button");
        if (isPresent(WARNING_MODAL, 10) && isPresent(WARNING_ORDER_BTN, 10)) {
            click(WARNING_ORDER_BTN, "Order button clicked on warning modal");
        }
    }

    // =========================
    // Helpers
    // =========================
    private double parseMoney(String s) {
        if (s == null) return 0.0;
        String cleaned = s.replaceAll("[^0-9.,-]", "").replace(",", "");
        if (cleaned.isBlank()) return 0.0;
        try { return Double.parseDouble(cleaned); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
