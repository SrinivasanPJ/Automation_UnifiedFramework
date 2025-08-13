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
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;
import org.testng.ITestContext;

import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Page object for the Order Information screen.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Navigate to order details</li>
 *   <li>Extract Order ID and Order Date from confirmation/details</li>
 *   <li>Persist extracted data into Excel via {@link OrderDataUtil}</li>
 *   <li>Utility to jump to the latest Transactional (R-series) order details page</li>
 * </ul>
 * <p><b>Note:</b> All method signatures and behavior are preserved intentionally.</p>
 */
public class OrderInformationPage extends BasePage {

    // ---- Page Elements ------------------------------------------------------

    @FindBy(xpath = "//a[normalize-space()='Click here for order details.'] | (//input[@value='Details'])[1]")
    private WebElement orderDetailsLink;

    @FindBy(css = "div.order-number strong")
    private WebElement orderIdElem;

    @FindBy(css = "div.order-overview span:nth-child(1)")
    private WebElement orderDateElem;

    // ---- Public API ---------------------------------------------------------

    /**
     * Clicks the link/button to navigate to the order details section.
     */
    public void clickOrderDetailsLink() {
        click(orderDetailsLink, "Order details link clicked");
    }

    /**
     * Reads the E2E Binding sheet, scans bottom-up for the newest row whose RunID
     * starts with {@code R} (Transactional run) and has a non-blank Order ID,
     * then navigates to {@code /orderdetails/{thatId}}.
     * <p>
     * If no such row is found, the method only logs a warning and keeps the current page.
     */
    public void openOrderDetailsForLatestRSeriesOrder() {
        String orderId = findLatestRSeriesOrderId();
        if (orderId == null || orderId.isBlank()) {
            LogUtil.warn(OrderInformationPage.class,
                    "No R-series Order ID found in E2E Binding; keeping current page.");
            return;
        }

        String current = driver.getCurrentUrl();
        // Replace trailing ID if we're already on an orderdetails page; otherwise build a fresh URL.
        String newUrl = current.matches("(?i).*/orderdetails/\\d+.*")
                ? current.replaceAll("(?i)(/orderdetails/)(\\d+)(.*)$", "$1" + orderId + "$3")
                : buildOrderDetailsUrlFromOrigin(current, orderId);

        LogUtil.log(OrderInformationPage.class, "Navigating to order details URL: " + newUrl);
        driver.get(newUrl);
    }

    /**
     * Returns the numeric Order ID (without the leading '#') from the confirmation/details page.
     */
    public String getOrderId() {
        waitUntilVisible(orderIdElem);
        String txt = orderIdElem.getText().trim();
        int i = txt.indexOf('#');
        return (i >= 0) ? txt.substring(i + 1).trim() : txt;
    }

    /**
     * Parses the order date text and returns it in {@code MM/dd/yyyy} format.
     * <p>
     * Expected raw pattern on the page is like:
     * <pre>Order Date: Wednesday, November 8, 2023</pre>
     * We strip the leading "Order Date: {DayOfWeek}, " and parse the remainder using
     * {@code MMMM d, yyyy}, returning {@code MM/dd/yyyy}.
     *
     * @return formatted date; fails the test if parsing is not possible
     */
    public String getOrderDate() {
        try {
            waitUntilVisible(orderDateElem);
            String raw = orderDateElem.getText()
                    .replaceAll("Order Date:\\s*\\w+,\\s*", "")
                    .trim();

            Date parsed = new SimpleDateFormat("MMMM d, yyyy").parse(raw);
            return new SimpleDateFormat("MM/dd/yyyy").format(parsed);
        } catch (Exception e) {
            Assert.fail("Invalid order date", e);
            return ""; // unreachable after Assert.fail, but keeps signature intact
        }
    }

    /**
     * Writes Order ID and Date to the Excel sheet used for tracking.
     *
     * @param rowIndex target row to write into
     */
    public void saveDetailsToExcel(int rowIndex) {
        String orderId = getOrderId();
        String orderDate = getOrderDate();
        log("Saving to Excel → ID=" + orderId + "  Date=" + orderDate);
        OrderDataUtil.writeOrderData(orderId, orderDate, rowIndex);
    }

    /**
     * Navigates to order details (placeholder for future richer assertions).
     *
     * @param context TestNG context (retained for future use; do not remove)
     */
    public void verifyOrderDetails(ITestContext context) {
        clickOrderDetailsLink();
        // In future: assert presence/visibility of known fields on details screen.
    }

    /**
     * Chooses the proper sheet, computes the next append row, stores it in the context
     * under {@code ExcelRowIndex}, and writes this order’s details to that row.
     *
     * @param context current TestNG context
     */
    public void writeDataToFile(ITestContext context) {
        final String sheetName;
        final int runIdCol;
        final int startRowIndex;

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

        // Always append to next available row for the selected sheet.
        int rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRowIndex);
        context.setAttribute("ExcelRowIndex", rowIndex);
        saveDetailsToExcel(rowIndex);
    }

    // ---- Internal Helpers ---------------------------------------------------

    /** Bottom-up scan for the newest row whose RunID starts with 'R' and has a non-blank Order ID. */
    private String findLatestRSeriesOrderId() {
        final String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        final String sheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet e2e = wb.getSheet(sheetName);
            if (e2e == null) {
                LogUtil.warn(OrderInformationPage.class, "E2E Binding sheet not found: " + sheetName);
                return null;
            }

            final int startRow = 1; // data starts after header for E2E Binding
            for (int r = e2e.getLastRowNum(); r >= startRow; r--) {
                Row row = e2e.getRow(r);
                if (row == null) continue;

                String runId   = ExcelUtil.getCellString(row, E2EBindingColumnIndex.RUN_ID);
                String orderId = ExcelUtil.getCellString(row, E2EBindingColumnIndex.ORDER_ID);

                if (!orderId.isBlank() && runId != null && runId.startsWith("R")) {
                    return orderId;
                }
            }

            LogUtil.log(OrderInformationPage.class, "No R-series rows with Order ID found in E2E Binding.");
            return null;

        } catch (Exception e) {
            LogUtil.error(OrderInformationPage.class,
                    "Failed reading E2E Binding for last R-series Order ID: " + e.getMessage(), e);
            return null;
        }
    }

    /** Builds a new {@code /orderdetails/{orderId}} URL using the origin of the current URL. */
    private String buildOrderDetailsUrlFromOrigin(String currentUrl, String orderId) {
        int schemeIdx = currentUrl.indexOf("://");
        int pathIdx = (schemeIdx > -1) ? currentUrl.indexOf('/', schemeIdx + 3) : -1;
        String origin = (pathIdx > -1) ? currentUrl.substring(0, pathIdx) : currentUrl;
        return origin + "/orderdetails/" + orderId;
    }
}
