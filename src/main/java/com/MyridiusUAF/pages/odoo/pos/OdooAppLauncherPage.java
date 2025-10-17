package com.MyridiusUAF.pages.odoo.pos;

import com.MyridiusUAF.base.BasePage;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

/**
 * Page object for the Odoo App Launcher screen (home after login).
 * <p>
 * Provides a single, resilient action to open the <em>Point of Sale</em> app.
 * The locator handles both common launcher variants:
 * <ul>
 *   <li>App tile located by its caption text: <b>Point of Sale</b></li>
 *   <li>App tile located by its POS href</li>
 * </ul>
 */
public class OdooAppLauncherPage extends BasePage {

    /**
     * Union XPath that matches the POS app tile by caption or by href.
     * The first match is clicked.
     */
    private static final By POS_TILE = By.xpath(
            "(" +
                    // Caption variant: <a class="o_app"> <div class="o_caption">Point of Sale</div> …
                    "//a[contains(@class,'o_app')]" +
                    "[.//div[contains(@class,'o_caption')][normalize-space()='Point of Sale']]" +
                    " | " +
                    // Href variant: <a class="o_app" href=".../odoo/point-of-sale…">
                    "//a[contains(@class,'o_app') and contains(@href,'/odoo/point-of-sale')]" +
                    ")[1]"
    );

    public OdooAppLauncherPage(WebDriver driver) {
        super(driver);
    }

    /**
     * Opens the Point of Sale application from the launcher.
     * <p>Robust to minor DOM/theme differences thanks to a union locator.</p>
     */
    public void openPointOfSaleFromTile() {
        // If your BasePage has an explicit wait helper, you can add it here
        // waitUntilVisible(POS_TILE, 10);
        click(POS_TILE, "Open POS from launcher");
        log("Clicked POS tile");
    }
}