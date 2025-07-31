package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.data.OrderDataUtil;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.Sheet;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;
import org.testng.ITestContext;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Page object to represent the Order Information screen.
 * Responsible for retrieving Order ID and Date after checkout
 * and saving them to the Excel result sheet.
 */
public class OrderInformationPage extends BasePage {

    @FindBy(xpath = "//a[normalize-space()='Click here for order details.'] | (//input[@value='Details'])[1]")
    private WebElement orderDetailsLink;

    @FindBy(css = "div.order-number strong")
    private WebElement orderIdElem;

    @FindBy(css = "div.order-overview span:nth-child(1)")
    private WebElement orderDateElem;

    /**
     * Clicks the link to navigate to the order details section.
     */
    public void clickOrderDetailsLink() {
        click(orderDetailsLink, "Order details link clicked");
    }

    /**
     * Retrieves the Order ID from the order confirmation page.
     *
     * @return String order ID without the '#' prefix
     */
    public String getOrderId() {
        waitUntilVisible(orderIdElem);
        String txt = orderIdElem.getText().trim();
        int i = txt.indexOf('#');
        return i >= 0 ? txt.substring(i + 1).trim() : txt;
    }

    /**
     * Parses and formats the order date into MM/dd/yyyy format.
     *
     * @return Formatted order date
     */
    public String getOrderDate() {
        try {
            waitUntilVisible(orderDateElem);
            String raw = orderDateElem.getText().replaceAll("Order Date: \\w+, ", "").trim();
            Date dt = new SimpleDateFormat("MMMM d, yyyy").parse(raw);
            return new SimpleDateFormat("MM/dd/yyyy").format(dt);
        } catch (Exception e) {
            Assert.fail("Invalid order date", e);
            return "";
        }
    }

    /**
     * Writes Order ID and Date to the Excel transactional sheet for tracking.
     *
     * @param rowIndex Row index where order details should be written
     */
    public void saveDetailsToExcel(int rowIndex) {
        String orderId = getOrderId();
        String orderDate = getOrderDate();
        log("Saving to Excel → ID=" + orderId + "  Date=" + orderDate);
        OrderDataUtil.writeOrderData(orderId, orderDate, rowIndex);
    }

    /**
     * Verifies order details by navigating to the order details link.
     *
     * @param context TestNG context (currently unused, but kept for possible future tracking)
     */
    public void verifyOrderDetails(ITestContext context) {
        clickOrderDetailsLink();
        // You can enhance this to check that details are visible/valid
    }

    /**
     * Writes the order ID and order date to the result Excel file for tracking.
     *
     * @param context TestNG context for Excel row index storage
     */
    public void writeDataToFile(ITestContext context) {
        String sheetName;
        int runIdCol;
        int startRowIndex;

        if (TestTypeUtil.isFromSystemTestPackage()) {
            sheetName = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
            runIdCol = ExcelColumnIndex.RUN_ID;
            startRowIndex = 2;
        } else if (TestTypeUtil.isFromE2ETestPackage()) {
            sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");
            runIdCol = E2EBindingColumnIndex.RUN_ID;
            startRowIndex = 1;
        } else {
            LogUtil.log(getClass(), "Skipping Excel row tracking: Unsupported test type");
            return;
        }

        Sheet sheet = ExcelReaderUtil.getSheet(
                ConfigReader.getProperty("Test_Data_File_Path"),
                sheetName
        );
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRowIndex);
        context.setAttribute("ExcelRowIndex", rowIndex);
        saveDetailsToExcel(rowIndex);
    }
}
