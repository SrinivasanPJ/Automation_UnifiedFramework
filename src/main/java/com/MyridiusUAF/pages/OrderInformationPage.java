package com.MyridiusUAF.pages;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.data.OrderDataUtil;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.E2EBindingDao;
import com.MyridiusUAF.utils.db.dao.TransactionalDao;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import com.MyridiusUAF.utils.test.TestTypeUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.Reporter;

import java.io.FileInputStream;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Page object for the Order Information screen.
 *
 * <p>Provides utilities to navigate to order details, extract the order id/date
 * from the UI, and persist them to Excel or DB depending on {@link DataMode}.</p>
 */
public class OrderInformationPage extends BasePage {

    private static final Logger LOGGER = LoggerFactory.getLogger(OrderInformationPage.class);

    // ---------------------------------------------------------------------
    // Page Elements
    // ---------------------------------------------------------------------

    @FindBy(xpath = "//a[normalize-space()='Click here for order details.'] | (//input[@value='Details'])[1]")
    private WebElement orderDetailsLink;

    /** Order number like: {@code "Order #2112962"} */
    @FindBy(xpath = "//div[@class='order-number']/strong")
    private WebElement orderIdElem;

    /** Order date text element (site-specific). */
    @FindBy(css = "div.order-overview span:nth-child(1)")
    private WebElement orderDateElem;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Clicks the order-details link on the page. */
    public void clickOrderDetailsLink() {
        click(orderDetailsLink, "Order details link clicked");
    }

    /**
     * Navigates to the order-details page for the latest R-series order id
     * found in the Excel E2E Binding sheet. No navigation if none found.
     */
    public void openOrderDetailsForLatestRSeriesOrder() {
        String orderId = findLatestRSeriesOrderId();
        if (orderId == null || orderId.isBlank()) {
            LogUtil.warn(OrderInformationPage.class,
                    "No R-series Order ID found in E2E Binding; keeping current page.");
            return;
        }
        goToOrderDetails(orderId);
    }

    /**
     * Opens the latest order-details page using the active data backend:
     * <ul>
     *   <li>DB mode: E2E → {@link E2EBindingDao#findLatestOrderId()}, SYSTEM → {@link TransactionalDao#findLatestOrderId()}</li>
     *   <li>Excel mode: last R-series order id in the E2E Binding sheet</li>
     * </ul>
     *
     * @throws RuntimeException if the lookup fails (DB mode) or no id is found
     */
    public void openLatestOrderDetailsFromBackend() {
        if (!DataMode.isDb()) {
            String orderId = findLatestRSeriesOrderId();
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("No order_id found in Excel E2E sheet.");
            }
            goToOrderDetails(orderId);
            return;
        }

        try {
            String orderId = isE2EContext()
                    ? new E2EBindingDao().findLatestOrderId()
                    : new TransactionalDao().findLatestOrderId();

            if (orderId == null || orderId.isBlank()) {
                throw new IllegalStateException("No order_id found in backend for current test type.");
            }
            goToOrderDetails(orderId);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch latest order_id from DB", e);
        }
    }

    /** @return only the numeric order id (strips any leading '#'). */
    public String getOrderId() {
        waitUntilVisible(orderIdElem);
        String txt = orderIdElem.getText().trim(); // e.g. "Order #2112962"
        int i = txt.indexOf('#');
        return (i >= 0) ? txt.substring(i + 1).trim() : txt;
    }

    /**
     * Parses the UI date and returns {@code MM/dd/yyyy} (Excel-friendly).
     *
     * @return formatted date string, or empty string (after Assert.fail)
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
            return "";
        }
    }

    /**
     * Writes order id/date to Excel at the given row (Excel flow only).
     *
     * @param rowIndex Excel row index to write to
     */
    public void saveDetailsToExcel(int rowIndex) {
        String orderId = getOrderId();
        String orderDate = getOrderDate();
        log("Saving to Excel → ID=" + orderId + "  Date=" + orderDate);
        OrderDataUtil.writeOrderData(orderId, orderDate, rowIndex);
    }

    /** Placeholder verification hook (kept for compatibility). */
    public void verifyOrderDetails(ITestContext context) {
        clickOrderDetailsLink();
    }

    /**
     * Persists the current order evidence to DB (DB mode) or Excel (Excel mode).
     * <p>DB mode computes the Excel row index but does not write to Excel.</p>
     */
    public void writeDataToFile(final ITestContext context) {
        if (DataMode.isDb()) {
            try {
                final String orderId   = getOrderId();
                final String orderDate = getOrderDate(); // MM/dd/yyyy
                persistToDatabase(context, orderId, orderDate); // unified logging inside
            } catch (Exception e) {
                LogUtil.error(getClass(), "DB mode: failed to persist order evidence: " + e.getMessage(), e);
            }
            return; // DB mode skips Excel
        }

        // ---- Excel path (unchanged) ----
        final String sheetName;
        final int runIdCol;
        final int startRowIndex;

        if (TestTypeUtil.isFromSystemTestPackage()) {
            sheetName     = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
            runIdCol      = ExcelColumnIndex.RUN_ID;
            startRowIndex = 2;
        } else if (TestTypeUtil.isFromE2ETestPackage()) {
            sheetName     = ConfigReader.getProperty("End_To_End_Sheet_Name");
            runIdCol      = E2EBindingColumnIndex.RUN_ID;
            startRowIndex = 1;
        } else {
            LogUtil.log(getClass(), "Skipping Excel row tracking: Unsupported test type");
            return;
        }

        final String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        int rowIndex;

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook wb = WorkbookFactory.create(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                LogUtil.warn(getClass(), "Sheet not found: " + sheetName + " in " + filePath);
                return;
            }

            rowIndex = ExcelUtil.findNextAvailableRow(sheet, runIdCol, startRowIndex);
        } catch (Exception e) {
            LogUtil.error(getClass(), "Failed to open workbook for row calc: " + e.getMessage(), e);
            return;
        }

        context.setAttribute("ExcelRowIndex", rowIndex);
        saveDetailsToExcel(rowIndex);
    }

    /**
     * Persist to storage immediately (DB mode honors test kind), else Excel.
     * <p>Extracts order id/date from UI first; DB mode uses {@link #persistToDatabase}.</p>
     */
    public void saveDetails() {
        // Extract from UI
        waitUntilVisible(orderIdElem);
        waitUntilVisible(orderDateElem);

        String orderNoText  = orderIdElem.getText();   // e.g. "Order #2112962"
        String orderDateTxt = orderDateElem.getText(); // e.g. "Order Date: Tuesday, October 14, 2025"

        // Normalize id (digits only)
        String orderId = (orderNoText == null) ? null : orderNoText.replaceAll("\\D+", "");
        // Tolerant date parse for DB; format to MM/dd/yyyy
        LocalDate parsedDate = OrderDataUtil.parseOrderDate(
                orderDateTxt.replaceAll("(?i)Order Date:\\s*", "")
                        .replaceAll("^\\w+,\\s*", "").trim()
        );

        if (DataMode.isDb()) {
            ITestContext ctx = Reporter.getCurrentTestResult().getTestContext();
            String orderDateStr = parsedDate.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            try {
                persistToDatabase(ctx, orderId, orderDateStr);
            } catch (Exception dbErr) {
                LOGGER.error("DB order persist failed (orderId={}, rawDate='{}')", orderId, orderDateTxt, dbErr);
            }
            return; // do not write Excel in DB mode
        }

        // Excel flow unchanged
        String excelOrderId   = getOrderId();
        String excelOrderDate = getOrderDate();
        final org.testng.ITestResult res = Reporter.getCurrentTestResult();
        Object idxObj = res.getTestContext().getAttribute("ExcelRowIndex");
        int rowIndex = (idxObj instanceof Integer) ? (Integer) idxObj : -1;
        OrderDataUtil.writeOrderData(excelOrderId, excelOrderDate, rowIndex);
    }

    // ---------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------

    /** @return true if the current TestNG context indicates an E2E test. */
    private boolean isE2EContext() {
        Object kind = Reporter.getCurrentTestResult()
                .getTestContext()
                .getAttribute("TEST_KIND");
        return "E2E".equals(kind);
    }

    /**
     * Navigates to the order details URL for the given order id, preserving origin.
     *
     * @param orderId numeric order id (no '#')
     */
    private void goToOrderDetails(String orderId) {
        String current = driver.getCurrentUrl();
        String url = current.matches("(?i).*/orderdetails/\\d+.*")
                ? current.replaceAll("(?i)(/orderdetails/)(\\d+)(.*)$", "$1" + orderId + "$3")
                : buildOrderDetailsUrlFromOrigin(current, orderId);
        LOGGER.info("Navigating to order details URL: {}", url);
        driver.get(url);
    }

    /**
     * DB-mode persistence logic:
     * <ul>
     *   <li>SYSTEM: updates {@code transactional_data} with {@code order_id}/{@code order_date} for {@code runId}</li>
     *   <li>Both: upserts into {@code e2e_binding} with the available identifiers</li>
     * </ul>
     */
    private void persistToDatabase(ITestContext ctx, String orderId, String orderDateStr) {
        try {
            LocalDate od = LocalDate.parse(orderDateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            java.sql.Date sqlOrderDate = java.sql.Date.valueOf(od);
            java.sql.Date sqlToday     = new java.sql.Date(System.currentTimeMillis());
            java.sql.Time sqlNow       = java.sql.Time.valueOf(LocalTime.now().withNano(0));

            String runId = (String) ctx.getAttribute("RunId");
            boolean e2e  = "E2E".equals(ctx.getAttribute("TEST_KIND"));

            // 1) transactional_data update → ONLY for SYSTEM tests
            if (!e2e && runId != null && !runId.isBlank()) {
                new TransactionalDao().updateOrderForRun(runId, orderId, od);
            } else if (!e2e) {
                LogUtil.warn(getClass(), "DB mode: RunId not found — transactional_data not updated.");
            }

            // 2) e2e_binding upsert (allowed for both kinds if we have orderId or runId)
            String application   = (String) ctx.getAttribute("Application");
            String testType      = (String) ctx.getAttribute("TestType");
            String functionality = (String) ctx.getAttribute("Functionality");
            String scenario      = (String) ctx.getAttribute("Scenario");
            String bindingName   = (String) ctx.getAttribute("BindingTestCaseName");
            String status        = (String) ctx.getAttribute("ExecutionStatus");
            String failureReason = (String) ctx.getAttribute("FailureReason");

            if (application   == null) application   = "demowebshop";
            if (testType      == null) testType      = e2e ? "E2E" : "SYSTEM";
            if (functionality == null) functionality = "Order Creation";
            if (scenario      == null) scenario      = "SC1 -Desktops-Order Creation";
            if (bindingName   == null) bindingName   = "SC1-TC1-E2E-Reorder";
            if (status        == null) status        = "Pass";

            if ((orderId != null && !orderId.isBlank()) || (runId != null && !runId.isBlank())) {
                new E2EBindingDao().upsertBinding(
                        application, testType, functionality, scenario,
                        bindingName, (runId == null ? "" : runId),
                        sqlToday, sqlNow, status,
                        orderId, sqlOrderDate,
                        failureReason
                );
            } else {
                LogUtil.warn(getClass(), "DB mode: neither runId nor orderId present — skipping e2e_binding.");
            }

            LogUtil.log(getClass(), "DB mode: persisted order evidence (orderId=" + orderId + ").");
        } catch (Exception e) {
            LogUtil.error(getClass(), "DB mode: persistToDatabase failed: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the latest order id from Excel E2E Binding where run id is an R-series value.
     *
     * @return order id or {@code null} if not found
     */
    public String findLatestRSeriesOrderId() {
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

                String runId = ExcelUtil.getCellString(row, E2EBindingColumnIndex.RUN_ID);
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

    /**
     * Builds the order-details URL for the current site origin.
     *
     * @param currentUrl current browser URL
     * @param orderId    numeric order id
     * @return normalized order-details URL pointing at the same origin
     */
    private String buildOrderDetailsUrlFromOrigin(String currentUrl, String orderId) {
        int schemeIdx = currentUrl.indexOf("://");
        int pathIdx = (schemeIdx > -1) ? currentUrl.indexOf('/', schemeIdx + 3) : -1;
        String origin = (pathIdx > -1) ? currentUrl.substring(0, pathIdx) : currentUrl;
        return origin + "/orderdetails/" + orderId;
    }
}
