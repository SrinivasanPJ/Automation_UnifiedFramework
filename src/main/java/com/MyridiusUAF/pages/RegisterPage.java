package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.utils.data.RandomDataGenerator;
import com.MyridiusUAF.utils.data.TestDataUpdater;
import org.openqa.selenium.*;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;

import java.util.Arrays;
import java.util.List;

/**
 * Page object for handling user registration workflow on the Demo Web Shop site.
 * <p>
 * Encapsulates interactions with registration fields, random data population,
 * and verification of successful registration. Updates the test data sheet
 * with generated email and password for reusability.
 * </p>
 * <b>Enterprise Standards Covered:</b>
 * <ul>
 *   <li>Field encapsulation and workflow methods</li>
 *   <li>Clear action/result logging (without exposing sensitive info)</li>
 *   <li>Input validation for key fields</li>
 *   <li>Consistent JavaDoc documentation</li>
 *   <li>Hardcoded values refactored to constants where appropriate</li>
 * </ul>
 */
public class RegisterPage extends BasePage {

    // --- Constants ---
    private static final int DEFAULT_TIMEOUT_SEC = 20;
    private static final String REGISTRATION_SUCCESS_MSG = "Your registration completed";
    private static final List<String> ALLOWED_GENDERS = Arrays.asList("male", "female");

    // --- WebElements ---
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

    // --- Page Actions ---

    /**
     * Clicks the Register link to open the registration form.
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
     * Selects the user's gender radio button.
     * @param gender "male" or "female" (case-insensitive)
     */
    public void selectGender(String gender) {
        if (gender == null || gender.isBlank()) {
            throw new IllegalArgumentException("Gender cannot be null or blank");
        }
        String normalized = gender.trim().toLowerCase();
        if (!ALLOWED_GENDERS.contains(normalized)) {
            throw new IllegalArgumentException("Invalid gender: " + gender + ". Allowed: " + ALLOWED_GENDERS);
        }
        String genderId = "gender-" + normalized;
        By genderLocator = By.id(genderId);
        click(genderLocator, "Selected gender: " + normalized);
    }

    /**
     * Enters the user's first name.
     */
    public void enterFirstName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            throw new IllegalArgumentException("First name cannot be null or blank");
        }
        sendKeys(firstNameInput, firstName);
        log("Entered first name: " + firstName);
    }

    /**
     * Enters the user's last name.
     */
    public void enterLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Last name cannot be null or blank");
        }
        sendKeys(lastNameInput, lastName);
        log("Entered last name: " + lastName);
    }

    /**
     * Enters the user's email address.
     */
    public void enterEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }
        sendKeys(emailInput, email);
        log("Entered email: " + email);
    }

    /**
     * Enters the user's password (masked in logs).
     */
    public void enterPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank");
        }
        sendKeys(passwordInput, password);
        log("Entered password: [PROTECTED]");
    }

    /**
     * Enters the user's password confirmation (masked in logs).
     */
    public void enterConfirmPassword(String confirmPassword) {
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new IllegalArgumentException("Confirm password cannot be null or blank");
        }
        sendKeys(confirmPasswordInput, confirmPassword);
        log("Entered confirm password: [PROTECTED]");
    }

    /**
     * Clicks the Register button to submit the form.
     */
    public void clickRegisterButton() {
        click(registerButton, "Clicked Register button");
    }

    /**
     * Verifies registration was successful, with timeout.
     * @param timeoutSec Timeout in seconds.
     */
    public void verifyRegistrationSuccess(int timeoutSec) {
        try {
            waitUntilVisible(registerSuccessMessage, timeoutSec);
            String actual = registerSuccessMessage.getText().trim();
            log("Registration message: " + actual);
            Assert.assertEquals(actual, REGISTRATION_SUCCESS_MSG, "Registration success message not found!");
        } catch (TimeoutException e) {
            log("Registration success message did not appear.");
            Assert.fail("Registration success message did not appear within timeout!");
        }
    }

    /**
     * Full registration workflow, including random data generation and Excel update.
     *
     * @param gender     Gender ("male" or "female")
     * @param firstName  User first name
     * @param lastName   User last name
     * @param timeoutSec Timeout for registration success message
     */
    public void register(String gender, String firstName, String lastName, int timeoutSec) {
        clickRegisterLink();
        waitForRegisterPage();
        selectGender(gender);
        enterFirstName(firstName);
        enterLastName(lastName);

        // Generate and log random test data (email/password)
        String randomEmail = RandomDataGenerator.getRandomEmail();
        String randomPassword = RandomDataGenerator.getRandomPassword(10);

        // Update Excel before registration for traceability
        TestDataUpdater.updateUsernameAndPassword("1", randomEmail, randomPassword);

        enterEmail(randomEmail);
        enterPassword(randomPassword);
        enterConfirmPassword(randomPassword);
        clickRegisterButton();
        verifyRegistrationSuccess(timeoutSec);
    }
}
