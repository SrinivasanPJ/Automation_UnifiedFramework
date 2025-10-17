package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Page Object for authentication workflows.
 * <p>Responsibilities:
 * <ul>
 *   <li>Open login form</li>
 *   <li>Enter credentials & submit</li>
 *   <li>Detect authenticated state (via Logout link)</li>
 * </ul>
 * <p><b>Note:</b> Method signatures and behavior preserved intentionally.</p>
 */
public class LoginPage extends BasePage {

    // ---- Timeouts (seconds) -------------------------------------------------
    private static final int WAIT_SHORT = 10;
    private static final int WAIT_MEDIUM = 20;

    // ---- Locators (PageFactory) --------------------------------------------
    @FindBy(xpath = "//a[@class='ico-login']")
    private WebElement loginLink;

    @FindBy(xpath = "//input[@class='email']")
    private WebElement email;

    @FindBy(xpath = "//input[@class='password']")
    private WebElement password;

    @FindBy(xpath = "//input[@value='Log in']")
    private WebElement loginButton;

    @FindBy(xpath = "//a[@class='ico-logout']")
    private WebElement logoutLink;

    /**
     * Performs the login action using provided user credentials.
     * <p>Waits for fields to be interactable, submits the form, then verifies
     * success by presence of the Logout link.</p>
     *
     * @param user email/username
     * @param pass password (masked in logs)
     * @throws AssertionError when the login is not successful
     */
    public void login(String user, String pass) {
        // Open form
        waitUntilClickable(loginLink, WAIT_SHORT);
        click(loginLink, "Open Login form");

        // Fill credentials
        waitUntilVisible(email, WAIT_SHORT);
        sendKeys(email, user);
        log("Entered Email: " + user);

        waitUntilVisible(password, WAIT_SHORT);
        sendKeys(password, pass);
        log("Entered Password: [PROTECTED]");

        // Submit
        waitUntilClickable(loginButton, WAIT_SHORT);
        click(loginButton, "Submit Login");

        // Verify
        if (!isLoginSuccessful()) {
            throw new AssertionError("Login was not successful for user: " + user);
        }
        log("Login successful for user: " + user);
    }

    /**
     * Returns {@code true} if the Logout link becomes visible (authenticated state).
     * Uses a short explicit wait for resiliency.
     */
    public boolean isLoginSuccessful() {
        try {
            waitUntilVisible(logoutLink, WAIT_SHORT);
            return logoutLink.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clicks the Logout link and returns {@code true} if the Login link becomes visible again.
     */
    public boolean clickLogoutLink() {
        waitUntilClickable(logoutLink, WAIT_MEDIUM);
        click(logoutLink, "Clicked Logout Link");
        try {
            waitUntilVisible(loginLink, WAIT_SHORT);
            return loginLink.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
}
