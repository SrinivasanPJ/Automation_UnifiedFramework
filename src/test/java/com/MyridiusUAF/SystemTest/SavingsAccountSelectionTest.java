package com.MyridiusUAF.SystemTest;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.testng.ITestContext;
import org.testng.annotations.Test;

public class SavingsAccountSelectionTest extends BaseTest {

    @Test(description = "Create Checking Account with Prove Data for a specific synthetic data row")
    public void CheckingAccount(ITestContext context) throws InterruptedException {
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        checkingAccount.homepage();
        checkingAccount.selectMaxCheckingPlan();
        checkingAccount.bundlePage();
        checkingAccount.startApplicationPage();
        checkingAccount.detailsPage();
        checkingAccount.personalInformationPage();
        checkingAccount.alsDonationsPage();
        checkingAccount.encaPage();
        checkingAccount.w9Attestation();
        checkingAccount.dnaPage();
        checkingAccount.accountOptionsPage();
        checkingAccount.addSomeFundsPage();
        checkingAccount.initialPage();
        checkingAccount.fillCardPaymentDetails("4111111111111111", "1230", "222");
        checkingAccount.summaryPage();
        checkingAccount.paymentConfirmationPage();
        checkingAccount.finalSuccessMessage();

        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                ConfigReader.getProperty("Transactional_Data_Sheet_Name")
        );
        //int rowIndex = ExcelUtil.findNextAvailableRow(sheet, ExcelColumnIndex.RUN_ID, 2);
        //context.setAttribute("ExcelRowIndex", rowIndex); // store in context
        //orderInformationPage.saveDetailsToExcel(rowIndex);
    }
}
