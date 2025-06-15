package com.AutoPOC.pages;

import com.AutoPOC.base.BasePage;
import com.AutoPOC.utils.data.OrderDataUtil;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.testng.Assert;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Page object to represent the Order Information screen.
 * Responsible for retrieving Order ID and Date after checkout
 * and saving them to the Excel result sheet.
 */
public class OrderInformationPage extends BasePage {

    @FindBy(xpath = "//a[normalize-space()='Click here for order details.']")
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
}
