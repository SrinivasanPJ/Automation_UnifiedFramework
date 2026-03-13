package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class ProductListPage extends BasePage {

    // ------------------------------------------------------------------------
    // Logger
    // ------------------------------------------------------------------------

    private static final Logger logger = LoggerFactory.getLogger(ProductListPage.class);

    // ------------------------------------------------------------------------
    // Timeouts / Waits
    // ------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private static final int PAGE_LOAD_TIMEOUT = 60;

    private static final int ELEMENT_WAIT_SHORT = 10;
    private static final int ELEMENT_WAIT_MEDIUM = 20;

    // ------------------------------------------------------------------------
    // Synthetic Input Map Keys
    // (kept centralized here to avoid string-scattering)
    // ------------------------------------------------------------------------

    private static final String KEY_CATEGORY = "Category";
    private static final String KEY_SUB_CATEGORY = "Sub-Category";
    private static final String KEY_PRODUCT_TITLE = "Product title";
    private static final String KEY_BILL_FNAME = "Billing FirstName";
    private static final String KEY_BILL_LNAME = "Billing LastName";
    private static final String KEY_EMAIL = "Email";
    private static final String KEY_COUNTRY = "Country";
    private static final String KEY_STATE = "State";
    private static final String KEY_CITY = "City";
    private static final String KEY_ADDR1 = "Address 1";
    private static final String KEY_ZIP = "Zip";
    private static final String KEY_PHONE = "Phone";

    // ------------------------------------------------------------------------
    // Common Locators
    // ------------------------------------------------------------------------

    private static final By BAR_NOTIFICATION = By.id("bar-notification");

    // ------------------------------------------------------------------------
    // Product & Category Locators
    // ------------------------------------------------------------------------

    @SuppressWarnings("unused")
    @FindBy(id = "products-orderby")
    private WebElement sortDropdown; // reserved for future sort operations

    @SuppressWarnings("unused")
    @FindBy(css = ".product-item .product-title a")
    private List<WebElement> productTitles; // reserved for future verifications

    @SuppressWarnings("unused")
    @FindBy(css = ".product-item .add-to-cart-button")
    private List<WebElement> addToCartButtons; // reserved for future bulk actions

    @SuppressWarnings("unused")
    @FindBy(css = ".side-2 .listbox .list a")
    private List<WebElement> categoryFilters; // reserved for future filtering tests

    @FindBy(xpath = "//input[starts-with(@id,'add-to-cart-button')]")
    private WebElement addToCartButton;

    // ------------------------------------------------------------------------
    // Header / Navigation Locators
    // ------------------------------------------------------------------------

    @FindBy(css = "div.header-links a.account")
    private WebElement accountLink;

    @FindBy(xpath = "//a[contains(@class,'active') or contains(@class,'inactive')][normalize-space()='Addresses']")
    private WebElement addressesLink;

    // ------------------------------------------------------------------------
    // Address Management Locators
    // ------------------------------------------------------------------------

    @FindBy(xpath = "//input[@value='Delete']")
    private WebElement deleteAddressButton;

    // ------------------------------------------------------------------------
    // Cart / Checkout Entry Locators
    // ------------------------------------------------------------------------

    @FindBy(xpath = "//span[normalize-space()='Shopping cart']")
    private WebElement shoppingCartButton;

    @FindBy(xpath = "//div[@class='checkout-buttons']")
    private WebElement checkoutButton;

    @FindBy(xpath = "//h1[text()='Checkout']")
    private WebElement checkoutHeader;

    @FindBy(xpath = "//input[@name='estimateshipping']")
    private WebElement estimateShippingButton;

    @FindBy(xpath = "//input[@id='termsofservice']")
    private WebElement termsOfService;

    // ------------------------------------------------------------------------
    // Billing Address Locators
    // ------------------------------------------------------------------------

    @FindBy(xpath = "//select[@id='BillingNewAddress_CountryId']")
    private WebElement countryDropdown;

    @FindBy(id = "BillingNewAddress_FirstName")
    private WebElement billingFirstName;

    @FindBy(id = "BillingNewAddress_LastName")
    private WebElement billingLastName;

    @FindBy(id = "BillingNewAddress_Email")
    private WebElement billingEmail;

    @FindBy(id = "BillingNewAddress_City")
    private WebElement billingCity;

    @FindBy(id = "BillingNewAddress_Address1")
    private WebElement billingAddress1;

    @FindBy(id = "BillingNewAddress_ZipPostalCode")
    private WebElement billingZipPostalCode;

    @FindBy(id = "BillingNewAddress_PhoneNumber")
    private WebElement billingPhoneNumber;

    @FindBy(xpath = "//select[@id='BillingNewAddress_StateProvinceId']")
    private WebElement stateDropdown;

    // ------------------------------------------------------------------------
    // Checkout Flow Buttons (Continue Steps)
    // ------------------------------------------------------------------------

    @FindBy(xpath = "//input[starts-with(@onclick,'Billing.save')]")
    private WebElement billingAddressContinueButton;

    @FindBy(xpath = "//input[starts-with(@onclick,'Shipping.save')]")
    private WebElement shippingAddressContinueButton;

    @FindBy(xpath = "//input[starts-with(@onclick,'ShippingMethod.save')]")
    private WebElement shippingMethodContinueButton;

    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentMethod.save')]")
    private WebElement paymentMethodContinueButton;

    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentInfo.save')]")
    private WebElement paymentInfoContinueButton;

    @FindBy(xpath = "//input[@value='Confirm']")
    private WebElement confirmButton;

    // ------------------------------------------------------------------------
    // Checkout Success Locators
    // ------------------------------------------------------------------------

    @SuppressWarnings("unused")
    @FindBy(xpath = "//h1[normalize-space()='Thank you']")
    private WebElement successMessage;

    // ------------------------------------------------------------------------
    // Public API: Address Management
    // ------------------------------------------------------------------------

    /**
     * Deletes the first address if present, confirming the alert and refreshing.
     * Does nothing if no delete control is visible.
     */
    public void deleteAddress() {
        click(accountLink, "Clicked Account link");
        click(addressesLink, "Clicked Addresses link");

        if (isDisplayed(deleteAddressButton)) {
            click(deleteAddressButton, "Clicked delete address");
            handleAlertAndRefresh();
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

    // ------------------------------------------------------------------------
    // Public API: Cart & Product Selection
    // ------------------------------------------------------------------------

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

    /**
     * Adds to cart, handles notification and navigates to cart.
     */
    public void addToCartAndGoToCart() {
        scrollIntoView(addToCartButton);
        click(addToCartButton, "Add to Cart");

        waitUntilVisible(BAR_NOTIFICATION, ELEMENT_WAIT_SHORT);
        closeNotificationIfPresentAndWait();
        waitUntilGone(BAR_NOTIFICATION, ELEMENT_WAIT_MEDIUM);

        scrollIntoView(shoppingCartButton);
        click(shoppingCartButton, "Open Shopping Cart");
    }

    // ------------------------------------------------------------------------
    // Public API: Cart / Checkout Entry
    // ------------------------------------------------------------------------

    /**
     * Clicks Estimate Shipping.
     */
    public void clickOnEstimateShippingButton() {
        click(estimateShippingButton, "Estimate Shipping");
    }

    /**
     * Checks the Terms of Service box.
     */
    public void clickTermsOfServiceButton() {
        click(termsOfService, "Accepted Terms of Service");
    }

    /**
     * Clicks the Checkout button on the cart page.
     */
    public void clickCheckoutButton() {
        click(checkoutButton, "Checkout");
    }

    /**
     * Waits for the Checkout page header.
     */
    public void waitForCheckoutPageVisible() {
        waitUntilVisible(checkoutHeader);
        log("Checkout page visible.");
    }

    // ------------------------------------------------------------------------
    // Public API: Billing / Shipping Details
    // ------------------------------------------------------------------------

    /**
     * Fills billing (and reused as shipping) details using synthetic input.
     */
    public void fillBillingDetailsFromInput() {
        Map<String, String> d = getInputData();

        // Verbose dump for debugging data mismatches in CI logs
        logger.info("---- synthetic inputData ----");
        d.forEach((k, v) -> logger.info("[{}] -> [{}]", k, v));

        sendKeys(billingFirstName, d.get(KEY_BILL_FNAME));
        sendKeys(billingLastName, d.get(KEY_BILL_LNAME));
        sendKeys(billingEmail, d.get(KEY_EMAIL));

        selectByVisibleText(countryDropdown, d.get(KEY_COUNTRY));
        selectStateOption(d.get(KEY_STATE));

        sendKeys(billingCity, d.get(KEY_CITY));
        sendKeys(billingAddress1, d.get(KEY_ADDR1));
        sendKeys(billingZipPostalCode, d.get(KEY_ZIP));
        sendKeys(billingPhoneNumber, d.get(KEY_PHONE));

        log("Filled billing & shipping details.");
    }

    // ------------------------------------------------------------------------
    // Public API: Checkout Continue Buttons
    // ------------------------------------------------------------------------

    public void clickOnBillingAddressContinueButton() {
        click(billingAddressContinueButton, "Billing Address → Continue");
    }

    public void clickOnShippingAddressContinueButton() {
        click(shippingAddressContinueButton, "Shipping Address → Continue");
    }

    public void clickOnShippingMethodContinueButton() {
        click(shippingMethodContinueButton, "Shipping Method → Continue");
    }

    public void clickOnPaymentMethodContinueButton() {
        click(paymentMethodContinueButton, "Payment Method → Continue");
    }

    public void clickOnPaymentInfoContinueButton() {
        click(paymentInfoContinueButton, "Payment Info → Continue");
    }

    // ------------------------------------------------------------------------
    // Public API: Final Confirmation & Verification
    // ------------------------------------------------------------------------

    /**
     * Scrolls and confirms the checkout at the final step.
     */
    public void checkoutConfirmation() {
        pause(2000);
        scrollToBottom();
        waitUntilClickable(confirmButton, ELEMENT_WAIT_SHORT);
        click(confirmButton, "Clicked Confirm");
    }

    // ------------------------------------------------------------------------
    // Private Helpers
    // ------------------------------------------------------------------------

    /**
     * Confirms alert, refreshes, and verifies delete control disappears.
     */
    private void handleAlertAndRefresh() {
        Alert alert = driver.switchTo().alert();

        log("Alert displayed: " + alert.getText());
        alert.accept();
        log("Alert accepted.");

        pause(3000);
        log("Address deleted; refreshing UI.");

        driver.navigate().refresh();
        waitForPageToLoad();
        pause(1000);

        boolean deleted = driver.findElements(By.xpath("//input[@value='Delete']")).isEmpty();
        log(deleted ? "Address deleted successfully." : "Delete button still visible after attempt.");
    }

    /**
     * Selects an explicit state or a random one if the value is blank.
     */
    private void selectStateOption(String state) {
        Select select = new Select(stateDropdown);

        // Site-specific delay; retained intentionally to avoid flakiness
        pause(3000);

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

    /**
     * Small wrapper around Thread.sleep with interruption handling.
     */
    private void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Verifies that the user is navigated to the Checkout Completed page
     * by validating the current URL.
     */
    public void verifyCheckoutCompletedUrl() {
        String expectedPartialUrl = "/checkout/completed";

        // Reuse BasePage URL wait
        waitUntilUrlContains(expectedPartialUrl);

        String actualUrl = getCurrentUrl();
        log("Navigated to URL: " + actualUrl);

        if (!actualUrl.contains(expectedPartialUrl)) {
            throw new AssertionError(
                    "Checkout completion failed. Expected URL to contain: "
                            + expectedPartialUrl + " but was: " + actualUrl);
        }

        log("Checkout completed URL verified successfully.");
    }
}

