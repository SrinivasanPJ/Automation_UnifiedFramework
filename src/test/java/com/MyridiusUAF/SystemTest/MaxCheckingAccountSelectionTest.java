package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class MaxCheckingAccountSelectionTest extends BaseTest {

    @Test(description = "Create Checking Account with Prove Data for a specific synthetic data row")
    public void createMaxCheckingAccount(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        performLogin("1");
        accountSelectionPage.homepage();
        accountSelectionPage.selectMaxCheckingPlan();
        accountSelectionPage.withBundleConfirmation();
        startApplicationPage.startApplicationButton();
        startApplicationPage.enterDetailsManuallyPage();
        startApplicationPage.addressEnteredDetails();
        termsConditionsPage.alsDonationsPage();
        termsConditionsPage.employerDetails();
        termsConditionsPage.encaPage();
        termsConditionsPage.w9Attestation();
        termsConditionsPage.dnaPage();
        accountPaymentPage.accountOptionsPage();
        accountPaymentPage.addSomeFundsPage();
        accountPaymentPage.initialPage();
        accountPaymentPage.fillCardPaymentDetails("4111111111111111", "1230", "222");
        accountPaymentPage.summaryPage();
        accountPaymentPage.paymentConfirmationPage();
        accountPaymentPage.finalSuccessMessage();

        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        //int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        //context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        //orderInformationPage.saveDetailsToExcel(rowIndex);
    }
}
