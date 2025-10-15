package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.utils.context.TestDataStore;
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


public class StartApplicationPage extends BasePage {
    JavascriptExecutor js = (JavascriptExecutor) driver;
    private static final Logger logger = LoggerFactory.getLogger(StartApplicationPage.class);

    @FindBy(xpath = "//div[@class='wdg-button-label wdg-label-content ng-binding']")
    private WebElement startApplicationButton;

    @FindBy(xpath = "//input[@id='lastFourSSN']")
    private WebElement ssn;
    @FindBy(xpath = "//input[@id='phoneNumber']")
    private WebElement phoneNumber;
    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement proveContinue;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Enter Details Manually')]")
    private WebElement enterManualDetailsButton;
    @FindBy(xpath = "//input[@id='customers_map_primary_FirstName']")
    private WebElement firstName;
    @FindBy(xpath = "//input[@id='customers_map_primary_LastName']")
    private WebElement lastName;
    @FindBy(xpath = "//input[@id='customers_map_primary_Email']")
    private WebElement email;
    @FindBy(xpath = "//input[@id='customers_map_primary_dateOfBirth']")
    private WebElement birthDate;
    @FindBy(xpath = "//input[@id='customers_map_primary_PhoneNumber']")
    private WebElement mobileNumber;
    @FindBy(xpath = "//input[@id='customers_map_primary_Tin']")
    private WebElement tinNumber;
    @FindBy(xpath = "//input[@id='customers_map_primary_customProps_citizenResidentUSyes']")
    private WebElement citizenSelect;
    @FindBy(xpath = "//h1[normalize-space()='Personal Information']")
    private WebElement personalInfoHeader;
    @FindBy(name = "customers_map_primary_identification_typeuslicense")
    private WebElement identificationType;
    @FindBy(id = "customers_map_primary_identification_number")
    private WebElement idNumber;
    @FindBy(id = "customers_map_primary_identification_state")
    private WebElement state;
    @FindBy(id = "customers_map_primary_identification_issuedDate")
    private WebElement issueDate;
    @FindBy(id = "customers_map_primary_identification_expirationDate")
    private WebElement expiryDate;
    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement piContinue;

    @FindBy(id = "customers_map_primary_addressCurrent_street")
    private WebElement street;

    @FindBy(id = "customers_map_primary_addressCurrent_unitNumber")
    private WebElement unit;

    @FindBy(xpath = "//label[normalize-space()='City']/following-sibling::input")
    private WebElement city;

    @FindBy(id = "customers_map_primary_addressCurrent_state")
    private WebElement raState;

    @FindBy(id = "customers_map_primary_addressCurrent_postalCode")
    private WebElement zip;

    @FindBy(xpath = "//h1[@class='ng-binding ng-scope' and contains(normalize-space(.),'Residential Address')]")
    private WebElement residentialAddressHeader;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement raContinue;

    @FindBy(xpath = "//div[@class='wdg-section-2-header col-sm-12 col-xs-12 ng-binding']")
    private WebElement alsDonation;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Confirm')]")
    private WebElement alsConfirmation;

    @FindBy(id = "customers_map_primary_employment_employer")
    private WebElement employer;

    @FindBy(id = "customers_map_primary_employment_occupation")
    private WebElement occupation;

    @FindBy(xpath = "//label[@class='touch-input wdg-input wdg-unselected' and contains(normalize-space(.),'No')]")
    private WebElement securityNo;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement alsContinue;

    @FindBy(xpath = "//label[@class='touch-input wdg-input']")
    private WebElement encaCheckBox;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement encaContinue;

    @FindBy(xpath = "//label[@class='touch-input wdg-input']")
    private WebElement w9Checkbox;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement w9Continue;

    @FindBy(xpath = "//label[@class='touch-input wdg-input']")
    private WebElement dnaCheckbox;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement dnaContinue;

    @FindBy(xpath = "(//label[@class='touch-input wdg-input wdg-unselected' and contains(normalize-space(.),'No')])[2]")
    private WebElement beneficiaryNo;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Select')]")
    private WebElement accountOptionsSelect;

    @FindBy(xpath = "//label[@class='touch-input wdg-input wdg-unselected' and contains(normalize-space(.),'Opt in: I want Lake Michigan Credit Union to pay overdrafts on ATM and everyday debit card transactions. I understand that I can revoke this authorization at any time.')]")
    private WebElement accountOptionsRdoBtn;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement accountOptionsContinue;

    @FindBy(xpath = "//div[@class='av-radio wdg-input-container ng-scope col-lg-12 col-md-12 col-sm-12 col-xs-12' and contains(normalize-space(.),'Debit card')]")
    private WebElement addSomeFundsRdoBtn;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement addSomeFundsContinue;

    @FindBy(id = "shoppingCart_fundingAmountPage_products_products_amountToFund-i0")
    private WebElement initialMaxChecking;

    @FindBy(id = "shoppingCart_fundingAmountPage_products_products_amountToFund-i1")
    private WebElement initialAmount;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Continue')]")
    private WebElement initialContinue;

    @FindBy(id = "modal")
    private WebElement newModal;

    @FindBy(xpath = "//button[normalize-space()='Open Swivel']")
    private WebElement openSwivelBtn;

    @FindBy(xpath = "id-swivelBlock-frame")
    private WebElement parentIframe;

    @FindBy(xpath = "//iframe[@src='https://web.betabaconpay.com/experience']")
    private WebElement cardPaymentModal;

    @FindBy(xpath = "//h1[normalize-space()='Card Payment']")
    private WebElement cardPaymentModalHeader;

    @FindBy(id = "card-pay-card-num-input")
    private WebElement cardNumber;

    @FindBy(id = "card-pay-card-exp-input")
    private WebElement expirationDate;

    @FindBy(id = "card-pay-card-code-input")
    private WebElement securityCode;

    @FindBy(id = "card-pay-next-btn")
    private WebElement nextButton;

    @FindBy(xpath = "(//span[@class='summary-line-item-label amount'])[1]")
    private WebElement checkingEnding;

    @FindBy(xpath = "(//span[@class='summary-line-item-label amount'])[2]")
    private WebElement savingEnding;

    @FindBy(xpath = "//input[@type='checkbox']")
    private WebElement verifyPaymentCheckbox;

    @FindBy(xpath = "//h1[normalize-space()='Verify Payment']")
    private WebElement verifyPaymentHeader;

    @FindBy(id = "card-pay-submit-btn")
    private WebElement submitButton;

    @FindBy(xpath = "(//strong[@class='ng-binding'])[3]")
    private WebElement transferMaxChecking;

    @FindBy(xpath = "(//strong[@class='ng-binding'])[3]")
    private WebElement transferMemberSavings;

    @FindBy(xpath = "//button[@class='btn wdg-button' and contains(normalize-space(.),'Submit')]")
    private WebElement confirmationTransferSubmit;

    @FindBy(xpath = "//div[@class='text-display av-optional id-textDisplay_16 avalon-form-field col-xs-12 col-sm-12 col-md-12 col-lg-12 av-item-container ng-binding' and contains(normalize-space(.),'welcome to LMCU!')]")
    private WebElement lastSuccessfulMessage;

    /**
     * Scroll to the bottom of the page
     * Clicks on the "Start Application" button
     **/
    public void startApplicationButton() throws InterruptedException
    {
        scrollToBottom();
        waitUntilClickable(startApplicationButton,80);
        click(startApplicationButton, "Start Application button is Clicked");
    }

    public void enterDetailsManuallyPage() throws InterruptedException
    {
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));

        Thread.sleep(3000);
        waitUntilClickable(enterManualDetailsButton,80);
        click(enterManualDetailsButton, "Enter Manual Details Clicked");
        sendKeys(firstName, d.get("firstName"));
        sendKeys(lastName, d.get("lastName"));
        String storedValueFirstName = getAttribute(firstName,"value");
        String storedValueLastName = getAttribute(lastName,"value");
        String startAppFullName = storedValueFirstName + " " + storedValueLastName;
        logger.info("Logging full name: " + startAppFullName);


        sendKeys(email, d.get("email"));
        Thread.sleep(3000);
        sendKeys(birthDate, d.get("birthDate"));
        sendKeys(mobileNumber, d.get("mobileNumber"));
        sendKeys(tinNumber, d.get("tinNumber"));
        isDisplayed(citizenSelect,5);
        scrollToBottom();

        if (isDisplayed(identificationType,3)) {
            log("identification type is displayed");
        }
        isDisplayed(idNumber,5);
        sendKeys(idNumber, "12345678910112");
        selectByVisibleText(state, "Florida");
        sendKeys(issueDate, "05262022");
        sendKeys(expiryDate, "05262032");

        TestDataStore.setStartFullName(startAppFullName);

        click(piContinue, "Continue is clicked, going to next page");
        if (isDisplayed(residentialAddressHeader)) {
            //Thread.sleep(50000);
            log("Residential Address page is displayed");
        }
    }


    /**
     * Gets the row data from the POC_data_sheet file - Synthetic_Data tab
     * Populates the SSN and Phone Number fields using mapped values from the file
     * Clicks the "Continue" button
     * Page navigates to Personal Details page
     * Populates the ID Number, State, Issued Date, Expiry Date fields using mapped values from the file
     * Clicks the "Continue" button
     **/
    public void submitSSNandPhone() throws InterruptedException
    {
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));

        sendKeys(ssn, d.get("ssn"));
        sendKeys(phoneNumber, d.get("mobileNumber"));
        click(proveContinue, "Continue button is clicked, going to next page");

        sendKeys(idNumber, d.get("idNumber"));
        selectByVisibleText(state, d.get("state"));
        sendKeys(issueDate, d.get("issueDate"));
        sendKeys(expiryDate, d.get("expiryDate"));
        waitUntilClickable(piContinue,80);
        click(piContinue, "Personal Info Continue button is clicked, going to next page");
    }

    /**
     * Checks if the Residential Address header is displayed
     * Note: Address is pre-populated and therefore populating the fields were not required
     * Clicks the "Continue" button
     **/
    public void addressPrePopulatedDetails() throws InterruptedException
    {
        waitUntilVisible(residentialAddressHeader, 80);
        log("Residential Address page is displayed");
        waitUntilClickable(raContinue,60);
        click(raContinue, "Residential Address Continue button is clicked, going to next page");
    }

    /**
     * Checks if the Residential Address header is displayed
     * Note: Address is populated with the details from the excel file
     * Clicks the "Continue" button
     **/
    public void addressEnteredDetails() throws InterruptedException
    {
        waitUntilVisible(residentialAddressHeader, 80);
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));

        waitUntilVisible(street,60);
        sendKeys(street, d.get("street"));
        sendKeys(unit, d.get("unit"));
        sendKeys(city, d.get("city"));
        selectByVisibleText(raState, d.get("raState"));
        sendKeys(zip, d.get("zip"));
        log("Resident Address fields are populated");
        click(raContinue, "Continue is clicked, going to next page");
        Thread.sleep(10000);
    }

    /**
     * Gets the row data from the POC_data_sheet file - Synthetic_Data tab
     * Checks if the ALS Donation page is displayed showing the "Confirm" button
     * Populates the Employer and Occupation fields using mapped values from the file
     * Clicks the "No" button under the "Will deposits into this account generally be from employment or Social Security?"
     * Clicks the "Continue" button
     **/
    public void alsDonationsPage() throws InterruptedException
    {
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));

        if (isDisplayed(alsConfirmation)) {
            click(alsConfirmation, "als Donation Confirmed, Going to next page");
        }

        sendKeys(employer, d.get("employer"));
        log("Employer is entered");
        sendKeys(occupation, d.get("occupation"));
        log("Occupations is entered");
        click(securityNo,"Security Selected No");
        js.executeScript("window.scroll(0,500)");
        click(alsContinue, "Continue is Clicked, going to next page");
    }

    /**
     * Checks if the Electronic Notice and Consent Agreement page is displayed showing a checkbox
     * Clicks on checkbox for consent
     * Clicks on "Continue" button
     **/
    public void encaPage() throws InterruptedException
    {
        if (isDisplayed(encaCheckBox)) {
            waitUntilVisible(encaCheckBox,80);
            click(encaCheckBox, "Checkbox is clicked");
        }
        click(encaContinue, "Electronic Notice and Consent Agreement Continue is clicked, going to next page");
    }

    /**
     * Checks if the W9 Attestation page is displayed showing a checkbox
     * Clicks on checkbox to certify statements are true
     * Clicks on "Continue" button
     **/
    public void w9Attestation() throws InterruptedException
    {
        if (isDisplayed(w9Checkbox)) {
            click(w9Checkbox, "W9 Attestation checkbox is clicked");
        }
        click(w9Continue, "W9 Attestation Continue is clicked, going to next page");
    }

    /**
     * Checks if the "Disclosures and Agreements" page is displayed showing a checkbox
     * Clicks on checkbox to agree on the T&Cs
     * Clicks on "Continue" button
     **/
    public void dnaPage() throws InterruptedException
    {
        if (isDisplayed(dnaCheckbox)) {
            click(dnaCheckbox, "DNA Checkbox is clicked");
        }
        waitUntilClickable(dnaContinue);
        click(dnaContinue, "DNA Continue is clicked, going to next page");
    }

    /**
     * Clicks "No" button under "Designate a beneficiary for your account?" in the "Account Options" page
     * Clicks the "Select" button to select the DEBIT card
     * Clicks the Opt In radio button
     * Clicks the "Continue" button
     **/
    public void accountOptionsPage() throws InterruptedException
    {
        waitUntilClickable(beneficiaryNo,80);
        click(beneficiaryNo, "beneficiary set to No");
        scrollToBottom();
        Thread.sleep(8000);
        waitUntilClickable(accountOptionsSelect,80);
        click(accountOptionsSelect, "Card is selected");
        waitUntilVisible(accountOptionsRdoBtn);
        Thread.sleep(8000);
        click(accountOptionsRdoBtn, "Radio Button is selected");
        waitUntilClickable(accountOptionsContinue,80);
        click(accountOptionsContinue, "Account Options Continue is clicked, going to the next page");
    }

    /**
     * Clicks the "Debit Card" radio button
     * Clicks the "Continue" button
     **/
    public void addSomeFundsPage() throws InterruptedException
    {
        waitUntilClickable(addSomeFundsRdoBtn,80);
        click(addSomeFundsRdoBtn, "Debit Card is selected");
        waitUntilClickable(addSomeFundsContinue);
        click(addSomeFundsContinue, "Continue is clicked, going to next page");
    }

    /**
     * Gets the row data from the POC_data_sheet file - Synthetic_Data tab
     * Populates the Amount for Checking and Savings Account using mapped value from the file
     * Clicks the "Continue" button
     **/
    public void initialPage() throws InterruptedException
    {
        Map<String, String> d = getInputData();
        logger.info("---- synthetic inputData keys&values ----");
        d.forEach((k, v) -> logger.info("[{}] → [{}]", k, v));

        sendKeys(initialMaxChecking, d.get("initialMaxChecking"));
        log("100 is entered in the Max Checking");
        //initialMaxChecking.getAttribute("MaxCheckingValue");

        sendKeys(initialAmount, d.get("initialAmount"));
        log("100 is entered in Amount");
        //initialAmount.getAttribute("AmountValue");

        click(initialContinue, "Continue is clicked, going to the next page");
    }

    /**
     * Locates the Inner and Outer iFrames
     * Note: there are 2 iFrames nested in the Card Payment modal
     * Inner iFrame has an ID which the number updates on every session
     * Locate Card Payment Modal header, Card Number, Expiry Date and CVV fields and populate them
     * Clicks the "Submit" button
     **/
    public void fillCardPaymentDetails(String cardNumber, String expMmYy, String cv2)
    {
        // Locators for frames
        By outerFrame = By.id("id-swivelBlock-frame");
        By innerFrame = By.cssSelector("iframe[id^='service-provider-'][id$='-frame']");

        // Locators inside the inner frame
        By cardPaymentHeading = By.xpath("//h1[normalize-space()='Card Payment']");
        By cardNumberInput = By.id("card-pay-card-num-input");
        By expiryInput = By.id("card-pay-card-exp-input");
        By cv2Input = By.id("card-pay-card-code-input");

        // Always start from top-level DOM
        switchToDefaultContent();

        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(70));

        // Switch to outer iframe
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(outerFrame));

        // Switch to inner iframe (dynamic id)
        wait.until(ExpectedConditions.frameToBeAvailableAndSwitchToIt(innerFrame));

        // Verify Card Payment heading
        wait.until(ExpectedConditions.visibilityOfElementLocated(cardPaymentHeading));
        log("Verified Card Payment heading is displayed.");

        // Fill Card Number
        WebElement cn = wait.until(ExpectedConditions.visibilityOfElementLocated(cardNumberInput));
        cn.clear();
        cn.sendKeys(cardNumber);

        // Fill Expiry
        WebElement exp = wait.until(ExpectedConditions.visibilityOfElementLocated(expiryInput));
        exp.clear();
        exp.sendKeys(expMmYy);

        // Fill CV2
        WebElement cv = wait.until(ExpectedConditions.visibilityOfElementLocated(cv2Input));
        cv.clear();
        cv.sendKeys(cv2);

        log("Entered card number, expiry, and CV2.");
        click(nextButton, "submit button is clicked");
        // Leave driver inside the inner frame; call switchToDefaultContent() when done
    }

    /**
     * Checks if the Summary Page is displayed
     * Clicks the checkbox to verify the payment
     * Clicks the "Submit" button
     **/
    public void summaryPage() throws InterruptedException
    {

        if (isDisplayed(verifyPaymentHeader)) {
            log("Verify Payment modal is displayed");
        }

        click(verifyPaymentCheckbox, "Checkbox is clicked");
        click(submitButton, "Verify Payment Submit button is clicked");
    }

    /**
     * Locator moves out of the iFrame
     * Checks the Confirmation Payment page is displayed
     * Clicks the "Submit" button
     **/
    public void paymentConfirmationPage() throws InterruptedException
    {
        switchToDefaultContent();
        if (isDisplayed(confirmationTransferSubmit)) {
            log("Confirmation Payment page is displaying");
            click(confirmationTransferSubmit, "Confirm Payment Submit button is Clicked");
        }
    }

    /**
     * Checks if the Success Message is displayed
     **/
    public void finalSuccessMessage() throws InterruptedException
    {
        if (isDisplayed(lastSuccessfulMessage)) {
            log("Success Message is displaying");
        }
    }
}
