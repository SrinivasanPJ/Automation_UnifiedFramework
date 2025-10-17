package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.*;

/**
 * Page Object: POS → Orders list.
 *
 * <p>Responsibilities</p>
 * <ul>
 *   <li>Open the <b>Orders</b> tab from the POS UI</li>
 *   <li>Verify that a specific invoice number is present in the orders table</li>
 * </ul>
 */

public class OdooPOSOrdersPage extends BasePage {

    /* ------------------------ Locators ------------------------ */

    // “Orders” tab/button in the POS screen (some themes render a span inside the button)
    private static final By ORDERS_TAB_BTN = By.xpath(
            "//button[normalize-space()='Orders' or .//span[normalize-space()='Orders']]"
    );

    private By invoiceCell(String invoiceNo) {
        return By.xpath(
                "//table//tr[.//div[contains(normalize-space(), " + xPathLiteral(invoiceNo) + ")]]"
        );
    }

    /* ---------------------- Construction ---------------------- */

    public OdooPOSOrdersPage(WebDriver driver) {
        super(driver);
    }

    /* ------------------------ Actions ------------------------- */

    /** Opens the Orders tab if present. No-op if not visible. */
    public void openOrdersTab() {
        if (isPresent(ORDERS_TAB_BTN, 10)) {
            click(ORDERS_TAB_BTN, "Open Orders tab");
        }
    }

    /**
     * Asserts that the given invoice number is present in the Orders table.
     * @param invoiceNo the invoice number to look for (e.g., {@code 2511-1-000004})
     * @throws AssertionError if the invoice is not found within the timeout
     */
    public void assertInvoiceVisible(String invoiceNo) {
        if (!isPresent(invoiceCell(invoiceNo), 10))
            throw new AssertionError("Invoice not found in Orders table: " + invoiceNo);
        log("Invoice number " + invoiceNo + " present in Orders page");
    }
}
