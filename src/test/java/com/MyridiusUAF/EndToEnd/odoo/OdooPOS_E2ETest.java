package com.MyridiusUAF.EndToEnd.odoo;

import com.MyridiusUAF.base.BaseTest;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.annotations.TestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.annotations.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import static java.math.RoundingMode.HALF_UP;

/**
 * E2E POS Test Flow:
 * 1) Login to Odoo using configured credentials
 * 2) Launch POS from App Launcher
 * 3) Handle dashboard options (Continue/New Order)
 * 4) Choose customer and open product category
 * 5) Add products (from config), verify cart items & totals
 * 6) Proceed to payment, choose method, confirm
 * 7) Verify receipt (items, totals, timestamp, company)
 * 8) Assert invoice generated and visible in Orders
 */
@TestType(TestType.Kind.E2E)
public class OdooPOS_E2ETest extends BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(OdooPOS_E2ETest.class);

    @Test(description = "POS E2E: add items, verify totals, receipt + invoice")
    public void odoo_pos_full_flow(ITestContext ctx) {
        executeTestForTestID("OD001", ctx);

        // 1) Login
        final String loginUrl = ConfigReader.getProperty("odoo.base.login.url");
        final String email    = System.getenv().getOrDefault("ODOO_EMAIL",    ConfigReader.getProperty("odoo.user.email"));
        final String pwd      = System.getenv().getOrDefault("ODOO_PASSWORD", ConfigReader.getProperty("odoo.user.password"));

        odooLoginPage.open(loginUrl);
        odooLoginPage.login(email, pwd);

        // 2) Open POS from App Launcher
        odooAppLauncherPage.openPointOfSaleFromTile();

        // 3) Handle dashboard gates (Continue/New Order)
        odooPOSDashboardPage.continueSellingIfVisible();
        odooPOSDashboardPage.clickNewOrderIfVisible();

        // 4) Register: select customer and items
        String customerCompany = odooPOSRegisterPage.chooseFirstCustomerAndReturnName();
        odooPOSRegisterPage.openDrinksCategory();

        List<String> itemsToAdd = Arrays.stream(
                        ConfigReader.getProperty("odoo.pos.items", "Water,Coca-Cola,Espresso,Green Tea")
                                .split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();

        odooPOSRegisterPage.addProductsExactly(itemsToAdd);

        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}

        // 5) Cart verification + dynamic total
        Map<String, Double> cart = odooPOSRegisterPage.readCartItems(false);
        for (String name : itemsToAdd) {
            boolean present = cart.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase(name) || k.toLowerCase().contains(name.toLowerCase()));
            Assert.assertTrue(present, "Cart missing: " + name + " | Cart: " + cart.keySet());

            double price = cart.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(name) || e.getKey().toLowerCase().contains(name.toLowerCase()))
                    .map(Map.Entry::getValue).findFirst().orElse(0.0);

            LOG.info(String.format("[1 %s %.2f]", name, price));
        }

        BigDecimal expectedSum = cart.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, HALF_UP);

        BigDecimal totalDisplayed = BigDecimal.valueOf(odooPOSRegisterPage.getDisplayedTotal()).setScale(2, HALF_UP);
        Assert.assertEquals(totalDisplayed, expectedSum, "Cart total != sum of line prices (rounded 2dp)");

        odooPOSRegisterPage.goToPayment();

        // 6) Payment
        String method = ConfigReader.getProperty("odoo.payment.method", "Cash");
        odooPOSPaymentPage.choosePaymentMethod(method);
        odooPOSPaymentPage.assertCenterPayment(method, totalDisplayed.doubleValue());
        odooPOSPaymentPage.validateAndConfirmOrderIfPrompted();

        // 7) Receipt
        odooPOSReceiptPage.assertPaymentSuccessfulWithTotal(totalDisplayed.doubleValue());
        odooPOSReceiptPage.sendReceiptByEmailAndAssertSuccess();

        Map<String, Double> receiptItems = odooPOSReceiptPage.readReceiptItems();

        for (String wanted : itemsToAdd) {
            boolean present = receiptItems.keySet().stream()
                    .anyMatch(r -> r.equalsIgnoreCase(wanted) || r.toLowerCase().contains(wanted.toLowerCase()));
            Assert.assertTrue(present, "Receipt missing item: " + wanted + " | " + receiptItems.keySet());
        }

        for (Map.Entry<String, Double> cartLine : cart.entrySet()) {
            String cartName  = cartLine.getKey();
            double cartPrice = cartLine.getValue();

            Optional<Map.Entry<String, Double>> match = receiptItems.entrySet().stream()
                    .filter(e -> e.getKey().equalsIgnoreCase(cartName) ||
                            e.getKey().toLowerCase().contains(cartName.toLowerCase()))
                    .findFirst();

            Assert.assertTrue(match.isPresent(),
                    "No receipt line matches cart item: " + cartName + " | receipt=" + receiptItems.keySet());

            BigDecimal rec = BigDecimal.valueOf(match.get().getValue()).setScale(2, HALF_UP);
            BigDecimal crt = BigDecimal.valueOf(cartPrice).setScale(2, HALF_UP);
            Assert.assertEquals(rec, crt, "Price mismatch for item '" + cartName + "' (cart vs receipt)");
        }

        BigDecimal receiptSum = receiptItems.values().stream()
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, HALF_UP);
        Assert.assertEquals(receiptSum, totalDisplayed, "Receipt sum mismatch vs POS total");

        odooPOSReceiptPage.assertCompanyOnReceipt(customerCompany);

        ZoneId zone = ZoneId.systemDefault();
        Instant receiptTs = odooPOSReceiptPage.getReceiptTimestampInstant(zone);
        Instant nowTs     = Instant.now();
        long skewSeconds  = Math.abs(Duration.between(receiptTs, nowTs).getSeconds());
        Assert.assertTrue(skewSeconds <= 5 * 60,
                "Receipt timestamp is too far from system time. diffSeconds=" + skewSeconds +
                        " receipt=" + odooPOSReceiptPage.getReceiptTimestampText());

        // 8) Orders: invoice visible
        String invoiceNo = odooPOSReceiptPage.getTaxInvoiceNumber();
        Assert.assertFalse(invoiceNo.isBlank(), "Tax Invoice number not found");
        odooPOSOrdersPage.openOrdersTab();
        odooPOSOrdersPage.assertInvoiceVisible(invoiceNo);
    }
}
