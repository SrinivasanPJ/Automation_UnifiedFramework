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
 * Page Object Model for all actions related to adding products to the cart,
 * performing checkout, and placing orders.
 *
 * <p>Follows best practices for enterprise frameworks: strongly typed WebElements,
 * robust error handling, JavaDoc for public API, and workflow encapsulation.
 */
public class AddProductsToCartAndPlaceOrderPage extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(AddProductsToCartAndPlaceOrderPage.class);
    private static final int PAGE_LOAD_TIMEOUT = 60;
    private static final int ELEMENT_WAIT_SHORT = 10;
    private static final int ELEMENT_WAIT_MEDIUM = 20;

    // WebElement Declarations (PageFactory)
    @FindBy(css = "div.header-links a.account") private WebElement accountLink;
    @FindBy(xpath = "//a[contains(@class, 'active') or contains(@class, 'inactive')][normalize-space()='Addresses']") private WebElement addressesLink;
    @FindBy(xpath = "//input[@value='Delete']") private WebElement deleteAddressButton;
    @FindBy(xpath = "//input[@name='estimateshipping']") private WebElement estimateShippingButton;
    @FindBy(xpath = "//input[@id='termsofservice']") private WebElement termsOfService;
    @FindBy(xpath = "//input[starts-with(@onclick,'Billing.save')]") private WebElement billingAddressContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'Shipping.save')]") private WebElement shippingAddressContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'ShippingMethod.save')]") private WebElement shippingMethodContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentMethod.save')]") private WebElement paymentMethodContinueButton;
    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentInfo.save')]") private WebElement paymentInfoContinueButton;
    @FindBy(xpath = "//input[@name='CardholderName']") private WebElement cardholderNameInput;
    @FindBy(xpath = "//input[@name='CardNumber']") private WebElement cardNumberInput;
    @FindBy(xpath = "//input[@name='CardCode']") private WebElement cardCodeInput;
    @FindBy(xpath = "//strong[text()='Your order has been successfully processed!']") private WebElement successMessage;
    @FindBy(xpath = "//input[starts-with(@id, 'add-to-cart-button')]") private WebElement addToCartButton;
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

    // ─────── Workflow Methods ───────────────────────────────────────────────

    /**
     * Navigates to the Orders page from account sidebar, with retry logic on failure.
     * Throws if not navigated after two attempts.
     */
    public void clickOnOrdersLink() {
        waitUntilVisible(sidebarLocator, PAGE_LOAD_TIMEOUT);
        log("Account sidebar is visible.");

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WebElement orders = waitUntilClickable(ordersLink, ELEMENT_WAIT_SHORT);
                scrollIntoView(orders);
                orders.click();
                log("Clicked on Orders link (attempt " + attempt + ").");

                waitUntilVisible(ordersPageHeader, ELEMENT_WAIT_SHORT);
                log("Orders page loaded successfully.");
                return;
            } catch (TimeoutException e) {
                log("Timeout waiting for Orders link or Orders page header. Retrying...");
                if (attempt == 2) throw new RuntimeException("Failed to open Orders page after retries.", e);
            }
        }
    }

    /** Clicks the "Re-order" button on the Orders page. */
    public void clickOnReorderButton() {
        scrollIntoView(reOrderButton);
        click(reOrderButton, "ReOrder button clicked");
    }

    /** Clicks the "Account" link in the header. */
    public void clickOnAccountLink() {
        click(accountLink, "Clicked Account link");
    }

    /**
     * Deletes the first saved address found for the user, if present.
     * Handles the confirmation alert and refreshes the UI.
     */
    public void deleteAddress() {
        click(accountLink, "Clicked Account link");
        click(addressesLink, "Clicked Addresses link");

        if (isDisplayed(deleteAddressButton)) {
            click(deleteAddressButton, "Clicked delete address button");
            handleAlertAndRefresh("Address deleted, refreshing the page to update UI.");
        }
    }

    /**
     * Proceeds to product selection only if no addresses exist.
     * This logic is useful for "fresh user" test cases.
     */
    public void selectProductIfNoAddressesExist() {
        if (getNumberOfAddresses() == 0) {
            log("No address found. Proceeding to product selection.");
            selectProductBasedOnInputData();
        } else {
            log("Address still present, skipping product selection.");
        }
    }

    /** Accepts the Terms of Service checkbox during checkout. */
    public void clickTermsOfServiceButton() {
        click(termsOfService, "Terms of service accepted");
    }

    /**
     * Confirms the checkout at the last step, scrolling to the bottom before clicking.
     */
    public void checkoutConfirmation() {
        pause(2000);
        scrollToBottom();
        waitUntilClickable(confirmButton, ELEMENT_WAIT_SHORT);
        click(confirmButton, "Clicked Confirm button.");
    }

    /**
     * Navigates category, sub-category, and product from synthetic input data.
     * Uses XPath templates and fails fast on any missing data.
     */
    public void selectProductBasedOnInputData() {
        Map<String, String> data = getInputData();

        log("Selecting Category, Sub-Category, and Product based on input data...");
        try {
            clickBy("Category", data.get("Category"), "//ul[@class='top-menu']//a[normalize-space()='%s']");
            clickBy("Sub-Category", data.get("Sub-Category"), "//div[@class='sub-category-item']//a[normalize-space()='%s']");
            clickBy("Product title", data.get("Product title"), "//h2[@class='product-title']/a[contains(text(),'%s')]");
        } catch (Exception e) {
            log("Error while selecting product path: " + e.getMessage());
            throw e;
        }
    }

    /** Waits for the Checkout header to be visible. */
    public void waitForCheckoutPageVisible() {
        waitUntilVisible(checkoutHeader);
        log("Checkout page is visible");
    }

    /**
     * Adds product to cart, handles notification, then navigates to cart.
     */
    public void addToCartAndGoToCart() {
        scrollIntoView(addToCartButton);
        click(addToCartButton, "Add to Cart clicked");
        By notificationBar = By.id("bar-notification");
        waitUntilVisible(notificationBar, ELEMENT_WAIT_SHORT);
        closeNotificationIfPresentAndWait();
        waitUntilGone(notificationBar, ELEMENT_WAIT_MEDIUM);
        scrollIntoView(shoppingCartButton);
        click(shoppingCartButton, "Shopping Cart clicked");
    }

    /**
     * Searches for a product, waits for suggestion, and selects it via keyboard actions.
     */
    public void searchAndSelectSuggestion(String keyword) {
        waitUntilVisible(searchInput, ELEMENT_WAIT_SHORT);
        sendKeys(searchInput, keyword);
        log("Entered search keyword: " + keyword);

        try {
            By suggestionLocator = By.xpath("//ul[contains(@class, 'ui-autocomplete') and contains(@style, 'display: block')]");
            waitUntilVisible(suggestionLocator, ELEMENT_WAIT_SHORT);
            log("Search suggestion dropdown is visible.");
        } catch (TimeoutException e) {
            log("Suggestion dropdown did not appear, proceeding with keyboard actions anyway.");
        }
        new Actions(driver).moveToElement(searchInput).pause(500)
                .sendKeys(Keys.ARROW_DOWN).pause(300).sendKeys(Keys.ENTER).perform();
        log("Selected the first search suggestion and submitted.");
    }

    /**
     * Fills in payment information on checkout.
     */
    public void fillPaymentInformation(String cardNumber, String cardCode) {
        String cardholderName = generateRandomCardholderName();
        waitUntilVisible(cardholderNameInput, ELEMENT_WAIT_SHORT);
        sendKeys(cardholderNameInput, cardholderName);
        sendKeys(cardNumberInput, cardNumber);
        sendKeys(cardCodeInput, cardCode);
        log("Filled payment information with random cardholder name: " + cardholderName);
    }

    /** Clicks Estimate Shipping during checkout. */
    public void clickOnEstimateShippingButton() {
        click(estimateShippingButton, "Estimate shipping button clicked");
    }

    /**
     * Fills billing and shipping details from synthetic input data.
     */
    public void fillBillingDetailsFromInput() {
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));
        sendKeys(billingFirstName, d.get("Billing FirstName"));
        sendKeys(billingLastName, d.get("Billing LastName"));
        sendKeys(billingEmail, d.get("Email"));
        selectByVisibleText(countryDropdown, d.get("Country"));
        selectStateOption(d.get("State"));
        sendKeys(billingCity, d.get("City"));
        sendKeys(billingAddress1, d.get("Address 1"));
        sendKeys(billingZipPostalCode, d.get("Zip"));
        sendKeys(billingPhoneNumber, d.get("Phone"));
        log("Filled billing and shipping details");
    }

    // --- Continue buttons (checkout flow) ---

    public void clickOnBillingAddressContinueButton() {
        click(billingAddressContinueButton, "Clicked on Billing Address continue button");
    }
    public void clickOnShippingAddressContinueButton() {
        click(shippingAddressContinueButton, "Clicked on Shipping Address continue button");
    }
    public void clickOnShippingMethodContinueButton() {
        click(shippingMethodContinueButton, "Clicked on Shipping method continue button");
    }
    public void clickOnPaymentMethodContinueButton() {
        click(paymentMethodContinueButton, "Clicked on Payment method continue button");
    }
    public void clickOnPaymentInfoContinueButton() {
        click(paymentInfoContinueButton, "Clicked on Payment info continue button");
    }

    /**
     * Selects a payment method by label (e.g. "Credit Card"), retrying with JS if needed.
     */
    public void selectPaymentMethod(String paymentMethodName) {
        WebElement label = waitUntilVisible(By.xpath("//label[normalize-space()='" + paymentMethodName + "']"), ELEMENT_WAIT_SHORT);
        String inputId = label.getAttribute("for");
        WebElement radio = waitUntilClickable(By.id(inputId), ELEMENT_WAIT_SHORT);

        try {
            scrollIntoView(radio);
            radio.click();
        } catch (ElementClickInterceptedException e) {
            log("Radio button not clickable directly, trying JavaScript click as fallback.");
            jsClick(radio);
        }
        boolean selected = false;
        for (int i = 0; i < 5; i++) {
            if (radio.isSelected()) { selected = true; break; }
            pause(500);
        }
        if (!selected) {
            jsClick(radio); pause(500);
            if (!radio.isSelected())
                throw new RuntimeException("Radio button '" + paymentMethodName + "' could not be selected!");
        }
        log("Payment method '" + paymentMethodName + "' successfully selected.");
    }

    /** Clicks the checkout button on cart page. */
    public void clickCheckoutButton() {
        click(checkoutButton, "Checkout button clicked");
    }

    /** Verifies the order success message is displayed. */
    public void verifyOrderSuccessMessage() {
        waitUntilVisible(successMessage);
        waitUntilTextPresent(successMessage, "Your order has been successfully processed!", ELEMENT_WAIT_SHORT);
        log("Order success message verified");
    }

    // ─────── Private/Helper Methods ──────────────────────────────────────────────

    /**
     * Selects the state for the address, or a random state if blank.
     */
    private void selectStateOption(String state) {
        Select select = new Select(stateDropdown);
        pause(3000);
        List<WebElement> opts = select.getOptions();
        if (state != null && !state.isBlank()) {
            select.selectByVisibleText(state);
            log("Explicitly selected state: " + state);
        } else {
            List<String> stateOptions = opts.stream().map(WebElement::getText).filter(s -> !s.isBlank()).toList();
            if (!stateOptions.isEmpty()) {
                String random = stateOptions.get(new Random().nextInt(stateOptions.size()));
                select.selectByVisibleText(random);
                log("Random state selected: " + random);
            }
        }
    }

    /** Handles alert confirmation and refreshes page. */
    private void handleAlertAndRefresh(String logMsg) {
        Alert alert = driver.switchTo().alert();
        log("Alert displayed: " + alert.getText());
        alert.accept();
        log("Alert accepted");
        pause(3000);
        log(logMsg);
        driver.navigate().refresh();
        waitForPageToLoad();
        pause(1000);
        boolean deleted = driver.findElements(By.xpath("//input[@value='Delete']")).isEmpty();
        log(deleted ? "Address deleted successfully." : "Delete button still visible after delete attempt.");
    }

    /** Standardized pause method for all waits. */
    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /**
     * Complete product order end-to-end workflow using Credit Card.
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
