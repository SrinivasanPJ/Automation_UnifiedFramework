package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Page Object for actions related to adding products to cart and placing orders.
 * <p>
 * Design goals:
 * <ul>
 *   <li>Stable locators and explicit waits</li>
 *   <li>Small, purpose-built methods</li>
 *   <li>Clear, structured logging suitable for CI</li>
 *   <li>No test framework concerns (assertions, data writes) here</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This class intentionally preserves existing behavior and
 * public method signatures. It only improves readability and resiliency.</p>
 */
public class AddProductsToCartAndPlaceOrderPage extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(AddProductsToCartAndPlaceOrderPage.class);

    // ---- Timeouts (seconds) -------------------------------------------------
    private static final int PAGE_LOAD_TIMEOUT     = 60;
    private static final int ELEMENT_WAIT_SHORT    = 10;
    private static final int ELEMENT_WAIT_MEDIUM   = 20;

    // ---- Reused locators / texts -------------------------------------------
    private static final By  BAR_NOTIFICATION = By.id("bar-notification");
    private static final By  SUGGESTION_DROPDOWN =
            By.xpath("//ul[contains(@class,'ui-autocomplete') and contains(@style,'display: block')]");
    private static final String SUCCESS_TEXT = "Your order has been successfully processed!";

    // Synthetic input map keys (kept here to avoid string scattering)
    private static final String KEY_CATEGORY          = "Category";
    private static final String KEY_SUB_CATEGORY      = "Sub-Category";
    private static final String KEY_PRODUCT_TITLE     = "Product title";
    private static final String KEY_BILL_FNAME        = "Billing FirstName";
    private static final String KEY_BILL_LNAME        = "Billing LastName";
    private static final String KEY_EMAIL             = "Email";
    private static final String KEY_COUNTRY           = "Country";
    private static final String KEY_STATE             = "State";
    private static final String KEY_CITY              = "City";
    private static final String KEY_ADDR1             = "Address 1";
    private static final String KEY_ZIP               = "Zip";
    private static final String KEY_PHONE             = "Phone";

    // ---- PageFactory elements ----------------------------------------------
    @FindBy(css  = "div.header-links a.account") private WebElement accountLink;
    @FindBy(xpath = "//a[contains(@class,'active') or contains(@class,'inactive')][normalize-space()='Addresses']")
    private WebElement addressesLink;
    @FindBy(xpath = "//input[@value='Delete']") private WebElement deleteAddressButton;

    @FindBy(xpath = "//input[@name='estimateshipping']") private WebElement estimateShippingButton;
    @FindBy(xpath = "//input[@id='termsofservice']") private WebElement termsOfService;

    @FindBy(xpath = "//input[starts-with(@onclick,'Billing.save')]") private WebElement billingAddressContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'Shipping.save')]") private WebElement shippingAddressContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'ShippingMethod.save')]")private WebElement shippingMethodContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentMethod.save')]") private WebElement paymentMethodContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentInfo.save')]") private WebElement paymentInfoContinueButton;

    @FindBy(xpath = "//input[@name='CardholderName']") private WebElement cardholderNameInput;
    @FindBy(xpath = "//input[@name='CardNumber']") private WebElement cardNumberInput;
    @FindBy(xpath = "//input[@name='CardCode']") private WebElement cardCodeInput;

    @FindBy(xpath = "//strong[text()='" + SUCCESS_TEXT + "']") private WebElement successMessage;

    @FindBy(xpath = "//input[starts-with(@id,'add-to-cart-button')]") private WebElement addToCartButton;
    @FindBy(xpath = "//span[normalize-space()='Shopping cart']") private WebElement shoppingCartButton;

    @FindBy(xpath = "//select[@id='BillingNewAddress_CountryId']") private WebElement countryDropdown;
    @FindBy(xpath = "//select[@id='BillingNewAddress_StateProvinceId']") private WebElement stateDropdown;

    @FindBy(xpath = "//div[@class='checkout-buttons']") private WebElement checkoutButton;
    @FindBy(xpath = "//h1[text()='Checkout']") private WebElement checkoutHeader;

    @FindBy(id = "BillingNewAddress_FirstName") private WebElement billingFirstName;
    @FindBy(id = "BillingNewAddress_LastName") private WebElement billingLastName;
    @FindBy(id = "BillingNewAddress_Email") private WebElement billingEmail;
    @FindBy(id = "BillingNewAddress_City") private WebElement billingCity;
    @FindBy(id = "BillingNewAddress_Address1") private WebElement billingAddress1;
    @FindBy(id = "BillingNewAddress_ZipPostalCode") private WebElement billingZipPostalCode;
    @FindBy(id = "BillingNewAddress_PhoneNumber") private WebElement billingPhoneNumber;

    @FindBy(xpath = "//input[@id='small-searchterms']") private WebElement searchInput;

    @FindBy(xpath = "//input[@value='Re-order']") private WebElement reOrderButton;
    @FindBy(xpath = "//a[@href='/customer/orders' and normalize-space()='Orders']") private WebElement ordersLink;
    @FindBy(xpath = "//h1[normalize-space()='My account - Orders']") private WebElement ordersPageHeader;
    @FindBy(css = "div.block-account-navigation") private WebElement sidebarLocator;

    @FindBy(xpath = "//input[@value='Confirm']") private WebElement confirmButton;

    // ───────────────────────── Public Workflow APIs ──────────────────────────

    /** Opens the Orders page from the account side bar with a simple retry. */
    public void clickOnOrdersLink() {
        waitUntilVisible(sidebarLocator, PAGE_LOAD_TIMEOUT);
        log("Account sidebar is visible.");

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WebElement orders = waitUntilClickable(ordersLink, ELEMENT_WAIT_SHORT);
                scrollIntoView(orders);
                orders.click();
                log("Clicked Orders (attempt " + attempt + ").");

                waitUntilVisible(ordersPageHeader, ELEMENT_WAIT_SHORT);
                log("Orders page loaded.");
                return;
            } catch (TimeoutException ex) {
                log("Orders link/header timeout. Retrying...");
                if (attempt == 2) {
                    throw new RuntimeException("Failed to open Orders page after retries.", ex);
                }
            }
        }
    }

    /** Clicks the “Re-order” button on the Orders page. */
    public void clickOnReorderButton() {
        scrollIntoView(reOrderButton);
        click(reOrderButton, "Reorder button clicked");
    }

    /** Clicks the “Account” link in the header. */
    public void clickOnAccountLink() {
        click(accountLink, "Clicked Account link");
    }

    /**
     * Deletes the first address if present, confirming the alert and refreshing.
     * Does nothing if no delete control is visible.
     */
    public void deleteAddress() {
        click(accountLink, "Clicked Account link");
        click(addressesLink, "Clicked Addresses link");
        if (isDisplayed(deleteAddressButton)) {
            click(deleteAddressButton, "Clicked delete address");
            handleAlertAndRefresh("Address deleted; refreshing UI.");
        }
    }

    /**
     * If no addresses exist, proceed to product selection using synthetic input.
     * Useful for “fresh user” scenarios.
     */
    public void selectProductIfNoAddressesExist() {
        if (getNumberOfAddresses() == 0) {
            log("No address found. Proceeding to product selection.");
            selectProductBasedOnInputData();
        } else {
            log("Address present, skipping product selection.");
        }
    }

    /** Checks the Terms of Service box. */
    public void clickTermsOfServiceButton() {
        click(termsOfService, "Accepted Terms of Service");
    }

    /** Scrolls and confirms the checkout at the final step. */
    public void checkoutConfirmation() {
        pause(2000);
        scrollToBottom();
        waitUntilClickable(confirmButton, ELEMENT_WAIT_SHORT);
        click(confirmButton, "Clicked Confirm");
    }

    /**
     * Navigates Category → Sub-Category → Product using synthetic input data.
     * Fails fast on any missing link.
     */
    public void selectProductBasedOnInputData() {
        Map<String, String> data = getInputData();
        log("Selecting path based on input data...");

        try {
            clickBy("Category", data.get(KEY_CATEGORY),
                    "//ul[@class='top-menu']//a[normalize-space()='%s']");
            clickBy("Sub-Category", data.get(KEY_SUB_CATEGORY),
                    "//div[@class='sub-category-item']//a[normalize-space()='%s']");
            clickBy("Product title", data.get(KEY_PRODUCT_TITLE),
                    "//h2[@class='product-title']/a[contains(text(),'%s')]");
        } catch (Exception e) {
            log("Error selecting product path: " + e.getMessage());
            throw e;
        }
    }

    /** Waits for the Checkout page header. */
    public void waitForCheckoutPageVisible() {
        waitUntilVisible(checkoutHeader);
        log("Checkout page visible.");
    }

    /** Adds to cart, handles notification and navigates to cart. */
    public void addToCartAndGoToCart() {
        scrollIntoView(addToCartButton);
        click(addToCartButton, "Add to Cart");
        waitUntilVisible(BAR_NOTIFICATION, ELEMENT_WAIT_SHORT);
        closeNotificationIfPresentAndWait();
        waitUntilGone(BAR_NOTIFICATION, ELEMENT_WAIT_MEDIUM);
        scrollIntoView(shoppingCartButton);
        click(shoppingCartButton, "Open Shopping Cart");
    }

    /** Searches for a product and selects the first suggestion via keyboard. */
    public void searchAndSelectSuggestion(String keyword) {
        waitUntilVisible(searchInput, ELEMENT_WAIT_SHORT);
        sendKeys(searchInput, keyword);
        log("Entered search keyword: " + keyword);

        try {
            waitUntilVisible(SUGGESTION_DROPDOWN, ELEMENT_WAIT_SHORT);
            log("Suggestion dropdown visible.");
        } catch (TimeoutException ignore) {
            log("Suggestion dropdown not shown; proceeding via keyboard.");
        }

        new Actions(driver)
                .moveToElement(searchInput).pause(500)
                .sendKeys(Keys.ARROW_DOWN).pause(300)
                .sendKeys(Keys.ENTER)
                .perform();

        log("Selected first suggestion and submitted.");
    }

    /** Fills payment inputs (random holder name + supplied number & CVV). */
    public void fillPaymentInformation(String cardNumber, String cardCode) {
        String holder = generateRandomCardholderName();
        waitUntilVisible(cardholderNameInput, ELEMENT_WAIT_SHORT);
        sendKeys(cardholderNameInput, holder);
        sendKeys(cardNumberInput, cardNumber);
        sendKeys(cardCodeInput, cardCode);
        log("Filled payment info (holder=" + holder + ").");
    }

    /** Clicks Estimate Shipping. */
    public void clickOnEstimateShippingButton() {
        click(estimateShippingButton, "Estimate Shipping");
    }

    /** Fills billing (and reused as shipping) details using synthetic input. */
    public void fillBillingDetailsFromInput() {
        Map<String, String> d = getInputData();

        // Intentional verbose dump for debugging data mismatches in CI
        logger.info("---- synthetic inputData ----");
        d.forEach((k, v) -> logger.info("[{}] -> [{}]", k, v));

        sendKeys(billingFirstName,   d.get(KEY_BILL_FNAME));
        sendKeys(billingLastName,    d.get(KEY_BILL_LNAME));
        sendKeys(billingEmail,       d.get(KEY_EMAIL));
        selectByVisibleText(countryDropdown, d.get(KEY_COUNTRY));
        selectStateOption(d.get(KEY_STATE));
        sendKeys(billingCity,        d.get(KEY_CITY));
        sendKeys(billingAddress1,    d.get(KEY_ADDR1));
        sendKeys(billingZipPostalCode, d.get(KEY_ZIP));
        sendKeys(billingPhoneNumber, d.get(KEY_PHONE));
        log("Filled billing & shipping details.");
    }

    // ---- Continue buttons in checkout flow ---------------------------------

    public void clickOnBillingAddressContinueButton()  { click(billingAddressContinueButton,  "Billing Address → Continue"); }
    public void clickOnShippingAddressContinueButton() { click(shippingAddressContinueButton, "Shipping Address → Continue"); }
    public void clickOnShippingMethodContinueButton()  { click(shippingMethodContinueButton,  "Shipping Method → Continue"); }
    public void clickOnPaymentMethodContinueButton()   { click(paymentMethodContinueButton,   "Payment Method → Continue"); }
    public void clickOnPaymentInfoContinueButton()     { click(paymentInfoContinueButton,     "Payment Info → Continue"); }

    /**
     * Selects a payment method by label, retrying with JS if necessary.
     *
     * @param paymentMethodName label text, e.g. {@code "Credit Card"}
     */
    public void selectPaymentMethod(String paymentMethodName) {
        WebElement label = waitUntilVisible(
                By.xpath("//label[normalize-space()='" + paymentMethodName + "']"),
                ELEMENT_WAIT_SHORT);
        WebElement radio = waitUntilClickable(By.id(label.getAttribute("for")), ELEMENT_WAIT_SHORT);

        try {
            scrollIntoView(radio);
            radio.click();
        } catch (ElementClickInterceptedException e) {
            log("Direct click failed; using JavaScript click.");
            jsClick(radio);
        }

        boolean selected = false;
        for (int i = 0; i < 5; i++) {
            if (radio.isSelected()) { selected = true; break; }
            pause(500);
        }
        if (!selected) {
            jsClick(radio); pause(500);
            if (!radio.isSelected()) {
                throw new RuntimeException("Payment method '" + paymentMethodName + "' could not be selected");
            }
        }
        log("Payment method selected: " + paymentMethodName);
    }

    /** Clicks the Checkout button on the cart page. */
    public void clickCheckoutButton() {
        click(checkoutButton, "Checkout");
    }

    /** Verifies that the order success banner is displayed. */
    public void verifyOrderSuccessMessage() {
        waitUntilVisible(successMessage);
        waitUntilTextPresent(successMessage, SUCCESS_TEXT, ELEMENT_WAIT_SHORT);
        log("Order success message verified.");
    }

    // ─────────────────────────── Private helpers ─────────────────────────────

    /** Selects an explicit state or a random one if the value is blank. */
    private void selectStateOption(String state) {
        Select select = new Select(stateDropdown);
        pause(3000); // site-specific delay; retained intentionally
        List<WebElement> options = select.getOptions();

        if (state != null && !state.isBlank()) {
            select.selectByVisibleText(state);
            log("Selected state: " + state);
            return;
        }

        List<String> stateOptions = options.stream()
                .map(WebElement::getText)
                .filter(s -> !s.isBlank())
                .toList();

        if (!stateOptions.isEmpty()) {
            String random = stateOptions.get(new Random().nextInt(stateOptions.size()));
            select.selectByVisibleText(random);
            log("Selected random state: " + random);
        }
    }

    /** Confirms alert, refreshes, and verifies delete control disappears. */
    private void handleAlertAndRefresh(String logMsg) {
        Alert alert = driver.switchTo().alert();
        log("Alert displayed: " + alert.getText());
        alert.accept();
        log("Alert accepted.");
        pause(3000);
        log(logMsg);
        driver.navigate().refresh();
        waitForPageToLoad();
        pause(1000);

        boolean deleted = driver.findElements(By.xpath("//input[@value='Delete']")).isEmpty();
        log(deleted ? "Address deleted successfully." : "Delete button still visible after attempt.");
    }

    /** Small wrapper around Thread.sleep with interruption handling. */
    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    // ─────────────────────── Composite convenience flow ──────────────────────

    /**
     * Complete order workflow using Credit Card.
     * <p>Kept as a convenience aggregator; callers may compose steps manually
     * if they need finer control or alternate payment paths.</p>
     */
    public void addProductToCartAndCheckoutWithCC() {
        clickOnEstimateShippingButton();
        clickTermsOfServiceButton();
        clickCheckoutButton();
        waitForCheckoutPageVisible();
        fillBillingDetailsFromInput();
        clickOnBillingAddressContinueButton();
        clickOnShippingAddressContinueButton();
        clickOnShippingMethodContinueButton();
        selectPaymentMethod("Credit Card");
        clickOnPaymentMethodContinueButton();
        fillPaymentInformation("4485564059489345", "123");
        clickOnPaymentInfoContinueButton();
        checkoutConfirmation();
        verifyOrderSuccessMessage();
    }
}
