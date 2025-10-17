package com.MyridiusUAF.flows;

import com.MyridiusUAF.pages.AddProductsToCartAndPlaceOrderPage;
import com.MyridiusUAF.pages.OrderInformationPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Encapsulates the Reorder business workflow.
 * <p>
 * This is a pure flow class (no TestNG concerns, no Excel writes). It orchestrates
 * the existing page objects to execute a “reorder” scenario end-to-end.
 * <p>
 * Steps performed:
 * <ol>
 *   <li>Delete address (cleanup to avoid stale state)</li>
 *   <li>Open Orders list</li>
 *   <li>Open order details</li>
 *   <li>Open latest R-series order details</li>
 *   <li>Click Reorder</li>
 *   <li>Checkout using Credit Card</li>
 *   <li>Open details page of the placed order</li>
 * </ol>
 */
public class ReorderFlow {

    private static final Logger LOG = LoggerFactory.getLogger(ReorderFlow.class);

    private final AddProductsToCartAndPlaceOrderPage addPage;
    private final OrderInformationPage orderInfoPage;

    /**
     * Creates a new flow using the provided page objects.
     *
     * @param addPage       page for cart & checkout actions (must not be {@code null})
     * @param orderInfoPage page for order-information actions (must not be {@code null})
     * @throws NullPointerException if any argument is {@code null}
     */
    public ReorderFlow(AddProductsToCartAndPlaceOrderPage addPage,
                       OrderInformationPage orderInfoPage) {
        this.addPage = Objects.requireNonNull(addPage, "addPage");
        this.orderInfoPage = Objects.requireNonNull(orderInfoPage, "orderInfoPage");
    }

    /**
     * Executes the reorder flow using existing page APIs.
     * <p>
     * This method is intentionally {@code void}; callers can perform their own
     * assertions or persistence after the final navigation to the order details page.
     * Any runtime exception from the underlying page methods is allowed to propagate.
     */
    public void perform() {
        LOG.info("Reorder flow: start");

        addPage.deleteAddress();
        addPage.clickOnOrdersLink();
        orderInfoPage.clickOrderDetailsLink();
        orderInfoPage.openLatestOrderDetailsFromBackend();
        addPage.clickOnReorderButton();
        addPage.addProductToCartAndCheckoutWithCC();
        orderInfoPage.clickOrderDetailsLink();

        LOG.info("Reorder flow: done");
    }
}
