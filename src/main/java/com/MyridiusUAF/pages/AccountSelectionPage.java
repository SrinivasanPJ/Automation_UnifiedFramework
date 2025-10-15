package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;


public class AccountSelectionPage extends BasePage {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    private static final Logger logger = LoggerFactory.getLogger(AccountSelectionPage.class);

    @FindBy(xpath = "//div[@class='wdg-section-1-header']")
    private WebElement homeCataloguePage;
    @FindBy(xpath = "//div[contains(@data-ng-click, 'click_detailsWrapper') and contains(normalize-space(.),  'Checking Accounts')]")
    private WebElement checkingSavings;

    @FindBy(xpath = "(//button[@class='btn wdg-button' and contains(normalize-space(.),'Select')])[1] ")
    private WebElement selectFreeChecking;
    @FindBy(xpath = "(//button[@class='btn wdg-button' and contains(normalize-space(.),'Select')])[2]")
    private WebElement selectMaxChecking;

    @FindBy(xpath = "(//div[@class='text-display av-optional id-textDisplay_6 avalon-form-field col-xs-12 col-sm-12 col-md-12 col-lg-12 av-item-container ng-binding'])[2]")
    private WebElement checkingPlusMemberSavingLabel;
    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Bundle')]")
    private WebElement letsBundleButton;

    @FindBy(xpath = "//div[@class='wdg-button-label wdg-label-content ng-binding']")
    private WebElement cartContinueButton;

    @FindBy(xpath = "//h1[@class='ng-binding ng-scope' and contains(normalize-space(.),'Get Started')]")
    private WebElement startApplicationPageHeader;

    /**
     * Clicks on "Checking" button to view Checking Account Options
     **/
    public void homepage() throws InterruptedException {
        if (isDisplayed(homeCataloguePage)) {
            checkingSavings.click();
            log("Home Catalogue page is displayed");
        }
    }

    /**
     * Clicks on the Free Checking Account Option
     **/
    public void selectFreeCheckingPlan() throws InterruptedException {
        waitUntilClickable(selectFreeChecking);
        click(selectFreeChecking, "Free Checking Plan Select button is clicked");
    }

    /**
     * Scrolls to the bottom of the page
     * Clicks on the Max Checking Account Option
     **/
    public void selectMaxCheckingPlan() throws InterruptedException
    {
        Thread.sleep(8000);
        scrollToBottom();
        waitUntilClickable(selectMaxChecking,80);
        click(selectMaxChecking, "Max Checking Plan Select button is clicked");
    }

    /**
     * Click "Let's Bundle" button
     * Navigates to Cart Page
     * Clicks the "Confirm" button
     **/
    public void withBundleConfirmation() throws InterruptedException {
        waitUntilClickable(letsBundleButton);
        click(letsBundleButton, "Let's Bundle is Clicked");
        waitUntilClickable(cartContinueButton, 60);
        click(cartContinueButton, "Bundle Confirmed");

        if (isDisplayed(startApplicationPageHeader)) {
            log("Start Application Page is displayed");
        }
    }

    /**
     * Click "Let's Bundle" button
     * Navigates to Cart Page
     * Clicks the "Confirm" button
     **/
    public void withoutBundleConfirmation() throws InterruptedException {
        waitUntilClickable(cartContinueButton, 60);
        click(cartContinueButton, "Bundle Confirmed");

        if (isDisplayed(startApplicationPageHeader)) {
            log("Start Application Page is displayed");
        }
    }


}