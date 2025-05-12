package com.AutoPOC.pages;

import com.AutoPOC.base.BasePage;
import org.openqa.selenium.Alert;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Page Object Model class that manages actions related to adding products to the cart
 * and completing the checkout and order placement process on the e-commerce platform.
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

    @FindBy(xpath = "//input[starts-with(@onclick,'Billing.save')"
            + " or starts-with(@onclick,'Shipping.save')"
            + " or starts-with(@onclick,'ShippingMethod.save')"
            + " or starts-with(@onclick,'PaymentMethod.save')"
            + " or starts-with(@onclick,'PaymentInfo.save')"
            + " or starts-with(@onclick,'ConfirmOrder.save')]")
    private List<WebElement> continueButtons;

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

    // ─── Workflow Methods ───────────────────────────────────────────────

    /**
     * Deletes an existing address if available, accepts alert, refreshes the page,
     * and proceeds to product selection if no addresses are left.
     *
     * @throws InterruptedException if thread is interrupted during sleep
     */
    public void deleteAddress() throws InterruptedException {
        click(accountLink, "Clicked Account link");
        click(addressesLink, "Clicked Addresses link");

        if (isDisplayed(deleteAddressButton)) {
            deleteAddressButton.click();
            log("Clicked delete address button");

            Alert alert = driver.switchTo().alert();
            log("Alert displayed: " + alert.getText());
            alert.accept();
            log("Alert accepted");

            driver.navigate().refresh();
            log("Page refreshed after deleting address");

            Thread.sleep(2000);
            boolean deleted = waitUntilElementGone(deleteAddressButton);
            log(deleted ? "Address deleted successfully." : "Delete button still visible after delete attempt.");
        }

        if (getNumberOfAddresses() == 0) {
            log("No address found. Proceeding to product selection.");
            selectProductBasedOnInputData();
        } else {
            log("Address still present, skipping product selection.");
        }
    }

    /**
     * Accepts the Terms of Service during checkout.
     */
    public void clickTermsOfServiceButton() {
        click(termsOfService, "Terms of service accepted");
    }

    /**
     * Attempts to click the confirm order button; retries with wait if necessary.
     */
    public void checkoutConfirmation() {
        try {
            confirmOrderButton.click();
            log("Confirm order clicked immediately without wait");
        } catch (Exception e) {
            log("Immediate click failed, retrying with wait...");
            waitUntilVisible(confirmOrderButton);
            waitUntilClickable(confirmOrderButton);
            confirmOrderButton.click();
        }
    }

    /**
     * Navigates through category, subcategory, and product based on input data.
     */
    public void selectProductBasedOnInputData() {
        Map<String, String> data = getInputData();
        clickBy("Category", data.get("Category"), "//ul[@class='top-menu']//a[normalize-space()='%s']");
        clickBy("Sub-Category", data.get("Sub-Category"), "//div[@class='sub-category-item']//a[normalize-space()='%s']");
        clickBy("Product title", data.get("Product title"), "//h2[@class='product-title']/a[contains(text(),'%s')]");
    }

    /**
     * Waits until the checkout page is fully visible.
     */
    public void waitForCheckoutPageVisible() {
        waitUntilVisible(checkoutHeader);
        log("Checkout page is visible");
    }

    /**
     * Adds a product to the cart and navigates to the shopping cart page.
     */
    public void addToCartAndGoToCart() {
        scrollIntoView(addToCartButton);
        click(addToCartButton, "Add to Cart clicked");
        // Wait for either cart count to update, toast to appear, or cart button to be clickable
        waitUntilClickable(shoppingCartButton, 7);  // Custom timeout if needed
        scrollIntoView(shoppingCartButton);
        click(shoppingCartButton, "Shopping Cart clicked");
    }

    /**
     * Clicks on the 'Estimate Shipping' button during checkout.
     */
    public void clickOnEstimateShippingButton() {
        click(estimateShippingButton, "Estimate shipping button clicked");
    }

    /**
     * Fills billing and shipping details using synthetic input data.
     *
     * @throws InterruptedException if thread is interrupted during sleep
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

    /**
     * Clicks through all required 'Continue' buttons during the checkout steps.
     */
    public void proceedThroughCheckout() {
        for (WebElement btn : continueButtons) {
            click(btn, "Clicked continue button");
        }
    }

    /**
     * Clicks on the 'Checkout' button from the cart page.
     */
    public void clickCheckoutButton() {
        click(checkoutButton, "Checkout button clicked");
    }

    /**
     * Verifies that the order has been successfully placed by checking the success message.
     */
    public void verifyOrderSuccessMessage() {
        waitUntilVisible(successMessage);
        waitUntilTextPresent(successMessage, "Your order has been successfully processed!", 10);
        log("Order success message verified");
    }

    // ─── Helper Methods ────────────────────────────────────────────────

    /**
     * Selects a state from the dropdown based on input. If not provided, selects a random available state.
     *
     * @param state the state to select or null to select randomly
     * @throws InterruptedException if thread is interrupted during sleep
     */
    private void selectStateOption(String state) throws InterruptedException {
        var select = new Select(stateDropdown);
        Thread.sleep(3000);
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
