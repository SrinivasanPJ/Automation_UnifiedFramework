package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Page object for handling login functionality.
 * Includes actions for entering credentials, clicking login,
 * and validating login success.
 */
public class LoginPage extends BasePage {

    //@FindBy(css = "//a[@class='ico-login']")
    //private WebElement loginLink;

    @FindBy(name = "j_username")
    private WebElement email;

    @FindBy(name = "j_password")
    private WebElement password;

    @FindBy(xpath = "/html/body/div[1]/div[1]/form/input[3]")
    private WebElement loginButton;

    @FindBy(xpath = "//div[@class='wdg-section-1-header']")
    private WebElement homeCataloguePage;

    //@FindBy (xpath = "//*[@id='page1']/div/div/div/div[2]/div[2]/div/div[5]/div/div[1]/div/div[3]/div/fieldset/div[1]/div[1]/div/div/div/div[1]/div[1]/a/img")
    //private WebElement checkingSavings;

    /**
     * Performs the login action using provided user credentials.
     *
     * @param user email or username
     * @param pass password
     */
    public void login(String user, String pass) {
       // click(loginLink, "Clicked Main Login button");
       // log("Attempting login with email: " + user);

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
            return homeCataloguePage.isDisplayed();
        } catch (Exception e) {
            return false;
        }
    }


}
