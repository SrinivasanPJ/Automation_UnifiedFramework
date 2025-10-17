package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Page object for the POS Register screen (product grid, cart, footer actions).
 * <p>
 * Responsibilities:
 * - Choose customer
 * - Navigate categories
 * - Add products (exactly once each)
 * - Read cart and totals
 * - Basic visual assertions (tile badge)
 */
public class OdooPOSRegisterPage extends BasePage {

    // ─────────────────────────────────────────
    // Constants
    // ─────────────────────────────────────────
    private static final int WAIT_SHORT = 5;
    private static final int WAIT_STD   = 10;

    // ─────────────────────────────────────────
    // Locators
    // ─────────────────────────────────────────

    // Footer actions
    private static final By CUSTOMER_BTN = By.xpath("//button[normalize-space()='Customer']");
    private static final By PAYMENT_BTN  = By.xpath("//button[normalize-space()='Payment']");

    // Category tabs
    private static final By DRINKS_TAB   = By.xpath("//button[.//span[normalize-space()='Drinks'] or normalize-space()='Drinks']");

    // Product tiles (grid on the right)
    private static final By PRODUCT_TILES     = By.cssSelector("article.product, article .product-content, button.product"); // (kept for future)
    private static final By PRODUCT_TILE_NAME = By.cssSelector(".product-name, .text-center, [id^='article_product_'], span"); // (kept for future)

    // Common “qty badge = 1” inside a tile
    private static final By TILE_QTY_1 = By.xpath(
            ".//span[normalize-space()='1' or (contains(@class,'fw-bolder') and normalize-space()='1') or contains(@class,'bg-black')]"
    );

    // Left cart
    private static final By CART_LINES      = By.cssSelector("ul.info-list li.orderline, li.orderline");
    private static final By CART_LINE_NAME  = By.cssSelector(".line-details .product-name, .line-details .text-wrap, .product-name");
    private static final By CART_LINE_PRICE = By.cssSelector(".price, .text-end, .oe_currency_value, span:has(+ .oe_currency_value)");
    private static final By TOTAL_VALUE     = By.xpath("//span[contains(@class,'total')][normalize-space()]");

    // Customer dialog
    private static final By CUSTOMER_MODAL      = By.cssSelector("div.o_dialog, div.modal-dialog");
    private static final By FIRST_CUSTOMER_ROW  = By.xpath("(//tr[contains(@class,'partner-line') or contains(@class,'partner-info')])[1]");
    private static final By FIRST_CUSTOMER_TICK = By.xpath("(//tr[contains(@class,'partner-line') or contains(@class,'partner-info')])[1]//i[contains(@class,'fa-check')]");
    private static final By FIRST_CUSTOMER_NAME = By.xpath("(//tr[contains(@class,'partner-line') or contains(@class,'partner-info')])[1]//td[contains(@class,'text-break')]");

    // ─────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────
    public OdooPOSRegisterPage(WebDriver d) { super(d); }

    // ─────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────

    /**
     * Opens the Customer dialog, reads the first company name, selects it, and returns the name.
     *
     * @return selected customer's company name (single-spaced)
     */
    public String chooseFirstCustomerAndReturnName() {
        click(CUSTOMER_BTN, "Open Customer modal");
        waitUntilVisible(CUSTOMER_MODAL, WAIT_STD);
        String company = waitUntilVisible(FIRST_CUSTOMER_NAME, WAIT_STD)
                .getText().trim().replaceAll("\\s+", " ");
        click(FIRST_CUSTOMER_ROW, "Select first customer row");
        if (isPresent(FIRST_CUSTOMER_TICK, 1)) click(FIRST_CUSTOMER_TICK, "Confirm selected");
        sleep(250);
        return company;
    }

    /** Opens Customer dialog and selects the first row. */
    public void chooseFirstCustomer() {
        click(CUSTOMER_BTN, "Open Customer modal");
        waitUntilVisible(CUSTOMER_MODAL, WAIT_STD);
        click(FIRST_CUSTOMER_ROW, "Select first customer row");
        if (isPresent(FIRST_CUSTOMER_TICK, 1)) click(FIRST_CUSTOMER_TICK, "Confirm selected");
        sleep(250);
    }

    /** Clicks the Drinks category. */
    public void openDrinksCategory() {
        click(DRINKS_TAB, "Clicked on Drinks category");
    }

    /** Returns the numeric order total shown at the bottom-left. */
    public double getDisplayedTotal() {
        String raw = waitUntilVisible(TOTAL_VALUE, WAIT_STD).getText();
        return parseMoney(raw);
    }

    /** Navigates to the Payment screen. */
    public void goToPayment() {
        click(PAYMENT_BTN, "Clicked on Payment button");
    }

    /**
     * Adds each product in order exactly once.
     * Steps per item:
     *  - Click the tile
     *  - Wait for the tile badge to show "1"
     *  - Wait for the corresponding cart line to appear
     * After all items, sleep(3000) and verify each name is present in the cart.
     *
     * @param names product display names
     */
    public void addProductsExactly(List<String> names) {
        Set<String> seen  = new HashSet<>();
        List<String> added = new ArrayList<>();

        for (String raw : names) {
            if (raw == null) continue;
            String name = raw.trim();
            if (name.isEmpty() || !seen.add(name)) continue; // ignore blanks/dupes

            WebElement tile = getTile(name);
            clickSilently(tile);

            // 1) badge = 1
            new WebDriverWait(driver, Duration.ofSeconds(WAIT_SHORT))
                    .until(ExpectedConditions.presenceOfNestedElementLocatedBy(tile, TILE_QTY_1));

            // 2) cart has the line
            new WebDriverWait(driver, Duration.ofSeconds(WAIT_SHORT))
                    .until(d -> {
                        Map<String, Double> m = readCartItems(false);
                        return m.keySet().stream().anyMatch(k ->
                                k.equalsIgnoreCase(name) || k.toLowerCase().contains(name.toLowerCase()));
                    });

            added.add(name);
        }

        // pause as requested
        sleep(3000);

        // final verify: cart contains everything
        Map<String, Double> finalCart = readCartItems(false);
        for (String name : added) {
            boolean present = finalCart.keySet().stream().anyMatch(k ->
                    k.equalsIgnoreCase(name) || k.toLowerCase().contains(name.toLowerCase()));
            if (!present) {
                throw new AssertionError("Cart missing after pause: " + name + " | Cart: " + finalCart.keySet());
            }
        }
    }

    /** Reads the cart as an ordered map: product name ➜ line/price. */
    public Map<String, Double> readCartItems() { return readCartItems(true); }

    /**
     * Reads the cart (optionally suppressing page log).
     *
     * @param logIt true to log "Cart: {...}"
     * @return ordered map of name -> price
     */
    public Map<String, Double> readCartItems(boolean logIt) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (WebElement line : driver.findElements(CART_LINES)) {
            String name  = "";
            String price = "";
            try { name  = line.findElement(CART_LINE_NAME).getText().trim(); } catch (Exception ignore) {}
            try {
                List<WebElement> prices = line.findElements(CART_LINE_PRICE);
                if (!prices.isEmpty()) price = prices.get(prices.size() - 1).getText();
            } catch (Exception ignore) {}
            if (!name.isBlank() && !price.isBlank()) {
                map.put(name, parseMoney(price));
            }
        }
        if (logIt) log("Cart: " + map);
        return map;
    }

    /** Convenience sum helper used by tests. */
    public static double sum(Collection<Double> vals) {
        return vals.stream().collect(Collectors.summingDouble(Double::doubleValue));
    }

    /**
     * Verifies the small quantity badge on the tile shows the expected number.
     *
     * @param productName tile display name
     * @param expected    expected count (e.g., 1)
     */
    public void assertTileBadgeCount(String productName, int expected) {
        WebElement tile = getTile(productName);
        boolean ok = new WebDriverWait(driver, Duration.ofSeconds(WAIT_SHORT))
                .until(d -> {
                    Integer count = readTileBadgeCount(tile);
                    return count != null && count == expected;
                });
        if (!ok) {
            Integer got = readTileBadgeCount(tile);
            throw new AssertionError("Expected badge " + expected + " for '" + productName + "' but was " + got);
        }
        log("Tile '" + productName + "' badge " + expected + " confirmed");
    }

    // ─────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────

    /** Exact tile locator (then fuzzy fallback). */
    private WebElement getTile(String name) {
        By exact = productTileExact(name);
        if (isPresent(exact, 1)) return driver.findElement(exact);
        return driver.findElement(productTileContains(name));
    }

    private By productTileExact(String name) {
        return By.xpath(
                "//article[contains(@class,'product') or contains(@class,'product-content')]" +
                        "[.//div[contains(@class,'product-name') or @id or self::div][normalize-space()='" + name + "']]" +
                        " | //button[contains(@class,'product')][normalize-space()='" + name + "']"
        );
    }

    private By productTileContains(String name) {
        return By.xpath(
                "//article[contains(@class,'product') or contains(@class,'product-content')]" +
                        "[.//div[contains(@class,'product-name') or @id or self::div][contains(normalize-space(),'" + name + "')]]" +
                        " | //button[contains(@class,'product')][contains(normalize-space(),'" + name + "')]"
        );
    }

    /** Silent robust click (avoids per-click page logs). */
    private void clickSilently(WebElement el) {
        new WebDriverWait(driver, Duration.ofSeconds(WAIT_STD))
                .until(ExpectedConditions.elementToBeClickable(el));
        try {
            el.click();
        } catch (ElementClickInterceptedException e) {
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
        }
    }

    /** Attempts to resolve price for a given name from a cart map (kept for compatibility). */
    private double resolvePrice(Map<String, Double> cart, String name) {
        for (Map.Entry<String, Double> e : cart.entrySet()) {
            String k = e.getKey();
            if (k.equalsIgnoreCase(name) || k.toLowerCase().contains(name.toLowerCase())) return e.getValue();
        }
        return 0.0;
    }

    /** Parse a currency string to double (lenient). */
    private double parseMoney(String s) {
        if (s == null) return 0.0;
        String cleaned = s.replaceAll("[^0-9.,-]", "").replace(",", "");
        if (cleaned.isBlank()) return 0.0;
        return Double.parseDouble(cleaned);
    }

    /** Attempts to read a numeric badge from a tile (various theme patterns). */
    private Integer readTileBadgeCount(WebElement tile) {
        List<By> candidates = List.of(
                By.xpath(".//span[normalize-space()='1' or contains(@class,'fw-bolder') or contains(@class,'bg-black')]"),
                By.xpath(".//span[normalize-space()]")
        );

        for (By by : candidates) {
            List<WebElement> spans = tile.findElements(by);
            for (WebElement s : spans) {
                String t = s.getText().trim();
                if (t.matches("\\d+")) {
                    try { return Integer.parseInt(t); } catch (NumberFormatException ignore) { /* try next */ }
                }
            }
        }

        // Fallback: any number in the tile text
        String all = tile.getText();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(all);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignore) {}
        }
        return null;
    }
}
