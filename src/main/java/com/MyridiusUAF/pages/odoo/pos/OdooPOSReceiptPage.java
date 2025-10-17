package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Page object for the POS Receipt panel shown after validating payment.
 * <p>
 * Notes:
 * - Targets the visible receipt area only (excludes the print overlay).
 * - Locators tolerate minor theme/layout changes.
 * - Public API intentionally small and task-focused.
 */
public class OdooPOSReceiptPage extends BasePage {

    // ─────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────
    private static final int  SHORT_WAIT_SEC = 10;
    private static final int  LONG_WAIT_SEC  = 25;
    private static final Locale LOCALE_EN    = Locale.ENGLISH;

    // Visible receipt panel (NOT the print overlay)
    private static final String RECEIPT_ROOT_CSS   = "div.pos-receipt.p-2:not(.pos-receipt-print)";
    private static final By    RECEIPT_ROOT        = By.cssSelector(RECEIPT_ROOT_CSS);
    // XPath anchor (avoid mixing :not() with XPath)
    private static final String RECEIPT_ROOT_XPATH =
            "//div[contains(@class,'pos-receipt') and contains(@class,'p-2') and not(contains(@class,'pos-receipt-print'))]";

    // Header / success
    private static final By SUCCESS_BANNER = By.xpath("//span[normalize-space()='Payment Successful']");
    private static final By RECEIPT_TOTAL  = By.xpath(
            RECEIPT_ROOT_XPATH +
                    "//*[self::span or self::div][normalize-space()='Total']" +
                    "/following::span[contains(@class,'oe_currency_value') or contains(@class,'pos-receipt-right-align')][1]"
    );

    // Send by email
    private static final By PLANE_SEND_BTN  = By.xpath(
            "//input[contains(@class,'send-receipt-email')]/following::button[@style='width: 8rem']"
    );
    private static final By EMAIL_SENT_TEXT = By.xpath(
            "//div[@class='text-success' and contains(.,'Receipt and invoice sent successfully')]"
    );

    // Items
    private static final By RECEIPT_ITEM_ROWS  = By.cssSelector(
            RECEIPT_ROOT_CSS + " .product-row, " + RECEIPT_ROOT_CSS + " .line-details"
    );
    private static final By RECEIPT_ITEM_NAME  = By.cssSelector(".text-wrap, .product-name, .name, .qt .text-wrap, .d-inline .text-wrap");
    private static final By RECEIPT_ITEM_PRICE = By.cssSelector(".oe_currency_value, .price, .text-end, .product-price");

    // Tax invoice & timestamp
    private static final By TAX_INVOICE_NUM    = By.cssSelector(RECEIPT_ROOT_CSS + " span.pos-receipt-vat");
    private static final By RECEIPT_ORDER_DATE = By.cssSelector(RECEIPT_ROOT_CSS + " #order-date");

    // Timestamp parsers (supports both D/M/Y and M/D/Y; case-insensitive AM/PM)
    private static final List<DateTimeFormatter> RECEIPT_TIME_FORMATS = List.of(
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d/M/uuuu, h:mm a").toFormatter(LOCALE_EN),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("d-M-uuuu, h:mm a").toFormatter(LOCALE_EN),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M/d/uuuu, h:mm a").toFormatter(LOCALE_EN),
            new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern("M-d-uuuu, h:mm a").toFormatter(LOCALE_EN)
    );

    // ─────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────
    public OdooPOSReceiptPage(WebDriver d) { super(d); }

    // ─────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────

    /**
     * Verifies the success banner and asserts the receipt total matches expected.
     *
     * @param expected expected total (double, already 2dp at source)
     */
    public void assertPaymentSuccessfulWithTotal(double expected) {
        waitUntilVisible(SUCCESS_BANNER, LONG_WAIT_SEC);
        double total = parseMoney(waitUntilVisible(RECEIPT_TOTAL, SHORT_WAIT_SEC).getText());
        if (Math.abs(total - expected) > 0.01) {
            throw new AssertionError("Receipt total mismatch. Expected " + expected + " got " + total);
        }
        log("Payment successful. Receipt total: " + total);
    }

    /**
     * Clicks the "send receipt by email" plane button and waits for the success toast, if present.
     * If the control isn't visible, the step is skipped (not an error).
     */
    public void sendReceiptByEmailAndAssertSuccess() {
        if (!isPresent(PLANE_SEND_BTN, 5)) {
            log("Send-by-email control not visible; skipping email check");
            return;
        }
        click(PLANE_SEND_BTN, "Send receipt/invoice by email");
        waitUntilVisible(EMAIL_SENT_TEXT, 30);
        log("Receipt/invoice email success message visible");
    }

    /**
     * @return ordered map of receipt line items (name -> price) taken from the visible receipt.
     */
    public Map<String, Double> readReceiptItems() {
        WebElement root = waitUntilVisible(RECEIPT_ROOT, SHORT_WAIT_SEC);
        Map<String, Double> map = new LinkedHashMap<>();
        for (WebElement row : root.findElements(RECEIPT_ITEM_ROWS)) {
            String name  = safeText(row, RECEIPT_ITEM_NAME);
            String price = safeText(row, RECEIPT_ITEM_PRICE);
            if (!name.isBlank() && !price.isBlank()) {
                map.put(name, parseMoney(price));
            }
        }
        log("Receipt items: " + map);
        return map;
    }

    /**
     * Extracts invoice number (removes the leading 'Tax Invoice' label) and logs it.
     *
     * @return invoice number (e.g., 2511-1-000004)
     */
    public String getTaxInvoiceNumber() {
        String raw = waitUntilVisible(TAX_INVOICE_NUM, SHORT_WAIT_SEC).getText().replace("\"", "").trim();
        String num = raw.replaceFirst("(?i)^tax\\s*invoice\\s*", "").trim();
        log("Tax Invoice number: " + num);
        return num;
    }

    /**
     * Asserts that the given company name appears anywhere in the visible receipt.
     *
     * @param expectedName company name expected to appear (case-insensitive, substring match)
     */
    public void assertCompanyOnReceipt(String expectedName) {
        String all = waitUntilVisible(RECEIPT_ROOT, SHORT_WAIT_SEC).getText();
        boolean ok = (all != null) && all.toLowerCase().contains(expectedName.trim().toLowerCase());
        if (!ok) throw new AssertionError("Company '" + expectedName + "' not found on receipt");
    }

    /**
     * @return raw receipt timestamp text (e.g., "7/10/2025, 6:12 am") and logs it.
     */
    public String getReceiptTimestampText() {
        String raw = waitUntilVisible(RECEIPT_ORDER_DATE, SHORT_WAIT_SEC).getText().trim();
        log("Receipt timestamp (raw): " + raw);
        return raw;
    }

    /**
     * Parses the receipt timestamp (supports D/M/Y and M/D/Y) and logs the parsed Instant + zone.
     *
     * @param zone zone used for interpretation
     * @return Instant representing the receipt time
     */
    public Instant getReceiptTimestampInstant(ZoneId zone) {
        String raw   = getReceiptTimestampText(); // already logs raw value
        String clean = raw.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim(); // normalize nbsp/spaces

        for (DateTimeFormatter f : RECEIPT_TIME_FORMATS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(clean, f);
                Instant parsed = ldt.atZone(zone).toInstant();
                log("Receipt timestamp (parsed): " + parsed + " | zone=" + zone);
                return parsed;
            } catch (DateTimeParseException ignore) {
                // try next
            }
        }
        throw new IllegalStateException("Could not parse receipt timestamp: '" + raw + "'");
    }

    // ─────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────

    private String safeText(WebElement root, By child) {
        try {
            String t = root.findElement(child).getText();
            return t == null ? "" : t.trim();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    private double parseMoney(String s) {
        if (s == null) return 0.0;
        String cleaned = s.replaceAll("[^0-9.,-]", "").replace(",", "");
        if (cleaned.isBlank()) return 0.0;
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
