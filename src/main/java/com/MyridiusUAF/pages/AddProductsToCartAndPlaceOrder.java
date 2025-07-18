package com.AutoPOC.pages;

import com.AutoPOC.base.BasePage;
import com.AutoPOC.utils.core.ScreenshotUtil;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.AutoPOC.utils.core.DriverFactory.getDriver;
import static java.lang.Thread.sleep;

/**
 * Page Object Model class for managing all actions related to adding products to the cart,
 * completing checkout, and placing orders on the demo e-commerce platform.
 * <p>
 * Encapsulates workflow and field interactions for address management, product selection,
 * checkout navigation, payment information entry, and order verification.
 * </p>
 */
public class AddProductsToCartAndPlaceOrder extends BasePage {

    private static final Logger logger = LoggerFactory.getLogger(AddProductsToCartAndPlaceOrder.class);

    // WebElements Declaration

    @FindBy(css = "div.header-links a.account")
    private WebElement accountLink;

    @FindBy(xpath = "//a[contains(@class, 'active') or contains(@class, 'inactive')][normalize-space()='Addresses']")
    private WebElement addressesLink;

    @FindBy(xpath = "//input[@value='Delete']")
    private WebElement deleteAddressButton;

    @FindBy(xpath = "//input[@name='estimateshipping']")
    private WebElement estimateShippingButton;

    @FindBy(xpath = "//input[@id='termsofservice']")
    private WebElement termsOfService;

//    @FindBy(xpath = "//input[starts-with(@onclick,'Billing.save') or "
//            + "starts-with(@onclick,'Shipping.save') or "
//            + "starts-with(@onclick,'ShippingMethod.save') or "
//            + "starts-with(@onclick,'PaymentMethod.save') or "
//            + "starts-with(@onclick,'ConfirmOrder.save') or "
//            + "starts-with(@onclick,'PaymentInfo.save')]")
//    private List<WebElement> continueButtons;
//
//    public List<WebElement> getContinueButtons() {
//        return continueButtons;
//    }

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

    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentMethod.save')]")
    private WebElement confirmOrderContinueButton;

    @FindBy(xpath = "//input[starts-with(@onclick,'PaymentInfo.save')"
            + " or starts-with(@onclick,'ConfirmOrder.save')]")
    private List<WebElement> paymentInfoAndConfirmOrderContinueButtons;

    public List<WebElement> getPaymentInfoAndConfirmOrderContinueButtons() {
        return paymentInfoAndConfirmOrderContinueButtons;
    }

    @FindBy(xpath = "//input[@name='CardholderName']")
    private WebElement cardholderNameInput;

    @FindBy(xpath = "//input[@name='CardNumber']")
    private WebElement cardNumberInput;

    @FindBy(xpath = "//input[@name='CardCode']")
    private WebElement cardCodeInput;

    @FindBy(xpath = "//select[@name='ExpireMonth']")
    private WebElement expireMonthDropdown;

    @FindBy(xpath = "//select[@name='ExpireYear']")
    private WebElement expireYearDropdown;

    @FindBy(xpath = "//strong[text()='Your order has been successfully processed!']")
    private WebElement successMessage;

    @FindBy(xpath = "//input[starts-with(@id, 'add-to-cart-button')]")
    private WebElement addToCartButton;

    @FindBy(xpath = "//span[normalize-space()='Shopping cart']")
    private WebElement shoppingCartButton;

    @FindBy(xpath = "//select[@id='BillingNewAddress_CountryId']")
    private WebElement countryDropdown;

    @FindBy(xpath = "//select[@id='BillingNewAddress_StateProvinceId']")
    private WebElement stateDropdown;

    @FindBy(xpath = "//div[@class='checkout-buttons']")
    private WebElement checkoutButton;

    @FindBy(xpath = "//h1[text()='Checkout']")
    private WebElement checkoutHeader;

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

    @FindBy(xpath = "//input[contains(@class,'confirm-order-next-step-button') and @value='Confirm']")
    private WebElement confirmOrderButton;

    @FindBy(xpath = "//input[@id='small-searchterms']")
    private WebElement searchInput;

    @FindBy(css = "input.search-box-button")
    private WebElement searchButton;

    @FindBy(xpath = "//a[contains(@class, 'active') or contains(@class, 'inactive')][normalize-space()='Orders']")
    private WebElement ordersLink;

    @FindBy(xpath = "//input[@value='Re-order']")
    private WebElement reOrderButton;

    // ─── Workflow Methods ───────────────────────────────────────────────

    /**
     * Clicks on the Orders link in the My Account sidebar, waits for "My account - Orders" page,
     * and retries the click once if needed.
     */
    public void clickOnOrdersLink() {
        By sidebarLocator = By.cssSelector("div.block-account-navigation");
        By ordersLink = By.xpath("//a[@href='/customer/orders' and normalize-space()='Orders']");
        By ordersPageHeader = By.xpath("//h1[normalize-space()='My account - Orders']");

        // Wait for sidebar to appear
        waitUntilVisible(sidebarLocator, 15);
        log("Account sidebar is visible.");

        int attempts = 0;
        boolean isOnOrdersPage = false;
        while (attempts < 2 && !isOnOrdersPage) {
            try {
                WebElement orders = waitUntilClickable(ordersLink, 10);
                scrollIntoView(orders);
                orders.click();
                log("Clicked on Orders link (attempt " + (attempts + 1) + ").");

                // Wait for the Orders page header
                waitUntilVisible(ordersPageHeader, 8);
                isOnOrdersPage = true;
                log("Orders page loaded successfully.");
            } catch (TimeoutException e) {
                log("Timeout waiting for Orders link or Orders page header. Retrying...");
                attempts++;
                if (attempts >= 2) {
                    throw new RuntimeException("Failed to open Orders page after retries.", e);
                }
            }
        }
    }



    public void clickOnReorderButton() {
        scrollIntoView(reOrderButton);
        click(reOrderButton, "ReOrder button clicked");
    }

    public void clickOnAccountLink() {
        click(accountLink, "Clicked Account link");
    }

    /**
     * Deletes any saved address for the logged-in user, if available.
     * <p>
     * Navigates to the account's address section, deletes the address after user confirmation,
     * refreshes the page, and proceeds to product selection if no address remains.
     *
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    public void deleteAddress() throws InterruptedException {
        // Step 1: Navigate to Account → Addresses page
        click(accountLink, "Clicked Account link");
        click(addressesLink, "Clicked Addresses link");

        // Step 2: Check if delete address button is displayed
        if (isDisplayed(deleteAddressButton)) {
            click(deleteAddressButton,"Clicked delete address button");

            // Step 3: Handle the browser alert (confirmation dialog)
            Alert alert = driver.switchTo().alert();
            log("Alert displayed: " + alert.getText());
            alert.accept();
            log("Alert accepted");
            sleep(3000);
            // Step 4: Refresh the page to ensure UI reflects the deleted address
            driver.navigate().refresh();
            log("Page refreshed after deleting address");

            Actions actions = new Actions(driver);
            actions.sendKeys(Keys.F5).perform();

            // Step 5: Wait for DOM to stabilize after refresh
            waitForPageToLoad();
            sleep(1000); // Optional: slight buffer before verifying

            // Step 6: Re-check using a fresh element locator to avoid stale reference
            boolean deleted = driver.findElements(By.xpath("//input[@value='Delete']")).isEmpty();
            log(deleted ? "Address deleted successfully." : "Delete button still visible after delete attempt.");
        }

        // Step 7: Continue to product selection only if no addresses remain
        if (getNumberOfAddresses() == 0) {
            log("No address found. Proceeding to product selection.");
            selectProductBasedOnInputData();
        } else {
            log("Address still present, skipping product selection.");
        }
    }

    /**
     * Clicks the "Terms of Service" checkbox during checkout.
     */
    public void clickTermsOfServiceButton() {
        click(termsOfService, "Terms of service accepted");
    }

    /**
     * Completes the final order confirmation step and verifies order placement.
     * <p>
     * Waits for the Confirm button or order success message, attempts to click Confirm,
     * then ensures the success message is displayed.
     */
    public void checkoutConfirmation() {
        By confirmButtonLocator = By.xpath("//input[contains(@class,'confirm-order-next-step-button') and @value='Confirm']");
        By orderSuccessLocator = By.cssSelector("div.section.order-completed");

        log("Waiting for Confirm button or Success message...");

        WebDriverWait wait = new WebDriverWait(getDriver(), Duration.ofSeconds(20));

        try {
            // First: Wait for either Confirm or Success
            wait.until(driver -> {
                try {
                    return driver.findElements(confirmButtonLocator).stream().anyMatch(e -> {
                        try {
                            return e.isDisplayed();
                        } catch (StaleElementReferenceException ignored) {
                            return false;
                        }
                    }) || driver.findElements(orderSuccessLocator).stream().anyMatch(e -> {
                        try {
                            return e.isDisplayed();
                        } catch (StaleElementReferenceException ignored) {
                            return false;
                        }
                    });
                } catch (Exception ignored) {
                    return false;
                }
            });

            // Second: Try to click Confirm if it's still there
            List<WebElement> confirmButtons = getDriver().findElements(confirmButtonLocator);
            if (!confirmButtons.isEmpty()) {
                try {
                    WebElement confirmBtn = confirmButtons.getFirst();
                    scrollIntoView(confirmBtn);
                    waitUntilClickable(confirmButtonLocator, 10);
                    click(confirmButtonLocator, "Clicked Confirm button.");
                } catch (StaleElementReferenceException | TimeoutException e) {
                    log("Confirm button became stale or was removed. Likely clicked automatically.");
                }
            } else {
                log("Confirm button skipped, success message already shown.");
            }

            // Final verification: wait for the order success page
            waitUntilVisible(orderSuccessLocator, 15);
            log("Order confirmation page loaded successfully.");

        } catch (Exception e) {
            String screenshotPath = ScreenshotUtil.saveScreenshotAsPNG(getDriver(), "checkoutConfirmation_Failure");
            log("Screenshot captured at: " + screenshotPath);
            throw new RuntimeException("Checkout Confirmation failed: Neither Confirm button nor Success message reliably appeared", e);
        }
    }

    /**
     * Selects the desired category, sub-category, and product based on the test input data.
     * <p>
     * Uses values from the synthetic data sheet for navigation.
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
            throw e;  // Rethrow for TestNG to capture and retry if needed
        }
    }

    /**
     * Waits until the checkout page is fully loaded and visible.
     */
    public void waitForCheckoutPageVisible() {
        waitUntilVisible(checkoutHeader);
        log("Checkout page is visible");
    }

    /**
     * Adds the product to the cart and navigates to the shopping cart page.
     * <p>
     * Handles the notification banner after adding the product.
     */
    public void addToCartAndGoToCart() {
        scrollIntoView(addToCartButton);
        click(addToCartButton, "Add to Cart clicked");

        // Wait for the success notification bar to appear
        By notificationBar = By.id("bar-notification");
        waitUntilVisible(notificationBar, 5);

        // Close the notification (if close button is shown)
        closeNotificationIfPresent();

        // Wait until the bar is fully gone before proceeding
        waitUntilGone(notificationBar, 10);

        scrollIntoView(shoppingCartButton);
        click(shoppingCartButton, "Shopping Cart clicked");
    }

    /**
     * Enters a keyword in the search box, waits for suggestions, then selects the first suggestion with Down+Enter.
     * @param keyword the search string
     */
    public void searchAndSelectSuggestion(String keyword) {
        // Step 1: Wait and type search keyword
        waitUntilVisible(searchInput, 10);
        sendKeys(searchInput, keyword);
        log("Entered search keyword: " + keyword);

        // Step 2: Wait for suggestions to appear (wait for dropdown; you may tune the locator as per actual suggestion)
        // Example: wait until the suggestion dropdown is visible, if it exists in the DOM
        try {
            By suggestionLocator = By.xpath("//ul[contains(@class, 'ui-autocomplete') and contains(@style, 'display: block')]");
            waitUntilVisible(suggestionLocator, 5);
            log("Search suggestion dropdown is visible.");
        } catch (TimeoutException e) {
            log("Suggestion dropdown did not appear, proceeding with keyboard actions anyway.");
        }

        // Step 3: Press Arrow Down and Enter to select suggestion
        Actions actions = new Actions(driver);
        actions.moveToElement(searchInput).pause(500)
                .sendKeys(Keys.ARROW_DOWN)
                .pause(300)
                .sendKeys(Keys.ENTER)
                .perform();
        log("Selected the first search suggestion and submitted.");
    }

    /**
     * Fills in the payment information form during checkout using the specified card number and code.
     * <p>
     * Cardholder name is generated randomly using Java Faker.
     *
     * @param cardNumber the card number to use (e.g., "4485564059489345")
     * @param cardCode   the card security code to use (e.g., "123")
     */
    public void fillPaymentInformation(String cardNumber, String cardCode) {
        String cardholderName = generateRandomCardholderName();
        // String expireMonth = "01";   // Adjust as needed
        // String expireYear = "2025";  // Adjust as needed
        waitUntilVisible(cardholderNameInput, 10);
        sendKeys(cardholderNameInput, cardholderName);
        sendKeys(cardNumberInput, cardNumber);
        sendKeys(cardCodeInput, cardCode);

        // Select expiration date
        // selectByVisibleText(expireMonthDropdown, expireMonth);
        // selectByVisibleText(expireYearDropdown, expireYear);

        log("Filled payment information with random cardholder name: " + cardholderName);
    }

    /**
     * Clicks the "Estimate Shipping" button to calculate shipping charges during checkout.
     */
    public void clickOnEstimateShippingButton() {
        click(estimateShippingButton, "Estimate shipping button clicked");
    }

    /**
     * Fills in billing (and shipping, if required) details from the synthetic input data.
     *
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    public void fillBillingDetailsFromInput() throws InterruptedException {
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

//    /**
//     * Proceeds through each step of the checkout process by clicking all relevant "Continue" buttons.
//     * <p>
//     * Handles scrolling, waits, JavaScript fallback for click, and robust error handling for dynamic UI elements.
//     *
//     * @param continueButtons list of "Continue" buttons for the current checkout steps
//     * @throws InterruptedException if the thread is interrupted during sleep
//     */
//    public void proceedThroughCheckout(List<WebElement> continueButtons) throws InterruptedException {
//        int step = 1;
//
//        for (WebElement button : continueButtons) {
//            try {
//                waitForPageToLoad();
//                scrollIntoView(button);
//
//                // Wait explicitly for visibility and clickability
//                WebElement visibleBtn = waitUntilVisible(button, 15);
//                WebElement clickableBtn = waitUntilClickable(visibleBtn, 10);
//
//                try {
//                    clickableBtn.click();
//                } catch (ElementClickInterceptedException e) {
//                    log("Standard click failed at step " + step + ". Retrying with JS click.");
//                    jsClick(clickableBtn);
//                }
//
//                log("Checkout - Continue button (Step " + step + ")");
//                waitForPageToLoad();
//                sleep(1000); // brief buffer between steps
//
//                step++;
//            } catch (TimeoutException e) {
//                logger.error("Checkout continue button at step {} was not clickable due to timeout: {}", step, e.getMessage());
//                throw new RuntimeException("Timeout waiting for checkout button at step " + step, e);
//            } catch (StaleElementReferenceException e) {
//                logger.error("Stale element at checkout step {}. Re-fetching continue button.", step);
//                throw new RuntimeException("Stale element at checkout step " + step + ", consider re-fetching the button list.", e);
//            } catch (Exception e) {
//                logger.error("Unexpected error during checkout at step {}: {}", step, e.getMessage(), e);
//                throw new RuntimeException("Unexpected error at checkout step " + step, e);
//            }
//        }
//    }

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
     * Selects a payment method radio button based on label, waits for selection, retries with JS if needed.
     * Fails if not selected after retry.
     *
     * @param paymentMethodName e.g., "Credit Card"
     */
    public void selectPaymentMethod(String paymentMethodName) throws InterruptedException {
        // 1. Wait for label and get the "for" attribute
        WebElement label = waitUntilVisible(By.xpath("//label[normalize-space()='" + paymentMethodName + "']"), 10);
        String inputId = label.getAttribute("for");
        WebElement radio = waitUntilClickable(By.id(inputId), 10);

        // 2. Try standard click
        try {
            scrollIntoView(radio);
            radio.click();
        } catch (ElementClickInterceptedException e) {
            log("Radio button not clickable directly, trying JavaScript click as fallback.");
            jsClick(radio);
        }

        // 3. Verify selected, wait up to 5s if needed
        boolean selected = false;
        for (int i = 0; i < 5; i++) {
            if (radio.isSelected()) {
                selected = true;
                break;
            }
            sleep(500); // slight wait, let browser update
        }

        // 4. Final check
        if (!selected) {
            // Last attempt: force JS click
            jsClick(radio);
            sleep(500);
            if (!radio.isSelected()) {
                throw new RuntimeException("Radio button '" + paymentMethodName + "' could not be selected!");
            }
        }
        log("Payment method '" + paymentMethodName + "' successfully selected.");
    }

    /**
     * Clicks the "Checkout" button to initiate the checkout process from the cart page.
     */
    public void clickCheckoutButton() {
        click(checkoutButton, "Checkout button clicked");
    }

    /**
     * Verifies that the order was successfully placed by checking for the confirmation message.
     */
    public void verifyOrderSuccessMessage() {
        waitUntilVisible(successMessage);
        waitUntilTextPresent(successMessage, "Your order has been successfully processed!", 10);
        log("Order success message verified");
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    /**
     * Selects the provided state from the dropdown during address entry.
     * If no state is provided, selects a random available state.
     *
     * @param state the state to select (maybe null or blank to select randomly)
     * @throws InterruptedException if the thread is interrupted during sleep
     */
    private void selectStateOption(String state) throws InterruptedException {
        var select = new Select(stateDropdown);
        sleep(3000);
        var opts = select.getOptions();

        if (state != null && !state.isBlank()) {
            select.selectByVisibleText(state);
            log("Explicitly selected state: " + state);
        } else {
            List<String> stateOptions = opts.stream()
                    .map(WebElement::getText)
                    .filter(s -> !s.isBlank())
                    .toList();
            if (!stateOptions.isEmpty()) {
                String random = stateOptions.get(new Random().nextInt(stateOptions.size()));
                select.selectByVisibleText(random);
                log("Random state selected: " + random);
            }
        }
    }
}
