package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.utils.data.RandomDataGenerator;
import com.MyridiusUAF.utils.data.TestDataUpdater;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;

import java.util.Set;

/**
 * Page object for the Demo Web Shop registration workflow.
 *
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li>Open registration form and populate fields</li>
 *   <li>Generate random credentials for test reuse and persist them</li>
 *   <li>Submit form and verify success message</li>
 * </ul>
 *
 * <p><b>Notes</b></p>
 * <ul>
 *   <li>All public method names and behavior are preserved intentionally.</li>
 *   <li>Sensitive values are masked in logs.</li>
 * </ul>
 */
public class RegisterPage extends BasePage {

    // ---- Constants ----------------------------------------------------------

    private static final int DEFAULT_TIMEOUT_SEC = 20;
    private static final String REGISTRATION_SUCCESS_MSG = "Your registration completed";
    private static final Set<String> ALLOWED_GENDERS = Set.of("male", "female");

    // ---- Elements -----------------------------------------------------------

    @FindBy(xpath = "//a[normalize-space()='Register']")
    private WebElement registerLink;

    @FindBy(xpath = "//h1[normalize-space()='Register']")
    private WebElement registerPage;

    @FindBy(id = "FirstName")
    private WebElement firstNameInput;

    @FindBy(id = "LastName")
    private WebElement lastNameInput;

    @FindBy(id = "Email")
    private WebElement emailInput;

    @FindBy(id = "Password")
    private WebElement passwordInput;

    @FindBy(id = "ConfirmPassword")
    private WebElement confirmPasswordInput;

    @FindBy(id = "register-button")
    private WebElement registerButton;

    @FindBy(css = "div.result")
    private WebElement registerSuccessMessage;

    // ---- Public Actions -----------------------------------------------------

    /**
     * Ensures a string is non-null and non-blank; returns the trimmed value.
     */
    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " cannot be null or blank");
        }
        return value;
    }

    /**
     * Opens the registration form.
     */
    public void clickRegisterLink() {
        waitUntilClickable(registerLink, DEFAULT_TIMEOUT_SEC);
        click(registerLink, "Clicked Register link");
    }

    /**
     * Waits until the Register page header is visible.
     */
    public void waitForRegisterPage() {
        waitUntilVisible(registerPage, DEFAULT_TIMEOUT_SEC);
        log("Register page is displayed.");
    }

    /**
     * Selects the user's gender radio.
     *
     * @param gender accepted values: "male" or "female" (case-insensitive)
     */
    public void selectGender(String gender) {
        String normalized = requireNonBlank(gender, "gender").trim().toLowerCase();
        if (!ALLOWED_GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid gender: " + gender + ". Allowed: " + ALLOWED_GENDERS);
        }
        By genderLocator = By.id("gender-" + normalized);
        click(genderLocator, "Selected gender: " + normalized);
    }

    /**
     * Enters the user's first name.
     */
    public void enterFirstName(String firstName) {
        String v = requireNonBlank(firstName, "firstName");
        sendKeys(firstNameInput, v);
        log("Entered first name: " + v);
    }

    /**
     * Enters the user's last name.
     */
    public void enterLastName(String lastName) {
        String v = requireNonBlank(lastName, "lastName");
        sendKeys(lastNameInput, v);
        log("Entered last name: " + v);
    }

    /**
     * Enters the user's email address.
     */
    public void enterEmail(String email) {
        String v = requireNonBlank(email, "email");
        sendKeys(emailInput, v);
        log("Entered email: " + v);
    }

    /**
     * Enters the user's password (masked in logs).
     */
    public void enterPassword(String password) {
        String v = requireNonBlank(password, "password");
        sendKeys(passwordInput, v);
        log("Entered password: [PROTECTED]");
    }

    /**
     * Enters the user's password confirmation (masked in logs).
     */
    public void enterConfirmPassword(String confirmPassword) {
        String v = requireNonBlank(confirmPassword, "confirmPassword");
        sendKeys(confirmPasswordInput, v);
        log("Entered confirm password: [PROTECTED]");
    }

    /**
     * Submits the registration form.
     */
    public void clickRegisterButton() {
        click(registerButton, "Clicked Register button");
    }

    /**
     * Verifies the success message appears within a timeout.
     *
     * @param timeoutSec timeout (seconds)
     */
    public void verifyRegistrationSuccess(int timeoutSec) {
        try {
            waitUntilVisible(registerSuccessMessage, timeoutSec);
            String actual = registerSuccessMessage.getText().trim();
            log("Registration message: " + actual);
            Assert.assertEquals(actual, REGISTRATION_SUCCESS_MSG, "Registration success message not found!");
        } catch (TimeoutException e) {
            log("Registration success message did not appear.");
            Assert.fail("Registration success message did not appear within timeout!", e);
        }
    }

    // ---- Internal Helpers ---------------------------------------------------

    /**
     * Full registration workflow, including random data generation and Excel update.
     *
     * @param gender     gender ("male" or "female")
     * @param firstName  first name
     * @param lastName   last name
     * @param timeoutSec timeout for the success check
     */
    public void register(String gender, String firstName, String lastName, int timeoutSec) {
        clickRegisterLink();
        waitForRegisterPage();
        selectGender(gender);
        enterFirstName(firstName);
        enterLastName(lastName);

        // Generate random credentials and persist for reuse/traceability.
        String randomEmail = RandomDataGenerator.getRandomEmail();
        String randomPassword = RandomDataGenerator.getRandomPassword(10);
        TestDataUpdater.updateUsernameAndPassword("1", randomEmail, randomPassword);

        enterEmail(randomEmail);
        enterPassword(randomPassword);
        enterConfirmPassword(randomPassword);
        clickRegisterButton();
        verifyRegistrationSuccess(timeoutSec);
    }
}
