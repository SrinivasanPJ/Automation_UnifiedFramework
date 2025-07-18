package com.AutoPOC.pages;

import com.AutoPOC.base.BasePage;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Page object for handling login functionality.
 * Includes actions for entering credentials, clicking login,
 * and validating login success.
 */
public class LoginPage extends BasePage {

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
     *
     * @param user email or username
     * @param pass password
     */
    public void login(String user, String pass) {
        click(loginLink, "Clicked Main Login button");
        //log("Attempting login with email: " + user);

        sendKeys(email, user);
        log("Entered Email: " + user);

        sendKeys(password, pass);
        log("Entered Password: [Protected]");

        click(loginButton, "Clicked Login button");

        if (!isLoginSuccessful()) {
            throw new AssertionError("Login was not successful for user: " + user);
        }
        log("Login successful for user: " + user);
    }

    /**
     * Checks if the login was successful by verifying the presence of the logout link.
     *
     * @return true if logout link is displayed, false otherwise
     */
    public boolean isLoginSuccessful() {
        try {
            return logoutLink.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }
}
