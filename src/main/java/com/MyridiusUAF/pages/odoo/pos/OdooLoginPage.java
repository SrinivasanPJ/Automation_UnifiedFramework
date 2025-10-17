package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

/**
 * Page Object: Odoo login screen.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Open the login URL</li>
 *   <li>Submit username/password</li>
 * </ul>
 *
 * <p>Notes:
 * <ul>
 *   <li>Selectors use standard Odoo attributes (stable across themes).</li>
 *   <li>Minimal logging; secrets are not logged.</li>
 *   <li>Waits are limited to field visibility to avoid flakiness without changing flow.</li>
 * </ul>
 */
public class OdooLoginPage extends BasePage {

    /* ------------ Locators (PageFactory) ------------ */

    @FindBy(name = "login")
    private WebElement emailInput;

    @FindBy(name = "password")
    private WebElement passwordInput;

    @FindBy(css = "button[type='submit']")
    private WebElement submitBtn;

    /* ------------ Construction ------------ */

    public OdooLoginPage(WebDriver driver) {
        super(driver);
    }

    /* ------------ Actions ------------ */

    /**
     * Navigates to the Odoo login page.
     *
     * @param loginUrl absolute login URL (e.g., {@code https://<tenant>/web/login})
     */
    public void open(String loginUrl) {
        driver.get(loginUrl);
        log("Opened Odoo Login: " + loginUrl);
    }

    /**
     * Performs a credential-based login.
     *
     * @param username user email/identifier
     * @param password plain text password (never logged)
     */
    public void login(String username, String password) {
        waitUntilVisible(emailInput, 15);
        sendKeys(emailInput, username);
        sendKeys(passwordInput, password);
        click(submitBtn, "Click Log in");
    }
}
