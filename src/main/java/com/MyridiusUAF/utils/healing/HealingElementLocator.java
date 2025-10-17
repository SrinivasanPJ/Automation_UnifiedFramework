package com.MyridiusUAF.utils.healing;

import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.agents.SelfHealingLocator;
import org.openqa.selenium.*;
import org.openqa.selenium.support.pagefactory.ElementLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Wraps the original ElementLocator and applies SelfHealingLocator on failures.
 */
public class HealingElementLocator implements ElementLocator {

    private static final Logger log = LoggerFactory.getLogger(HealingElementLocator.class);

    private final WebDriver driver;
    private final ElementLocator original;
    private final LlmClient llm;

    public HealingElementLocator(WebDriver driver, ElementLocator original, LlmClient llm) {
        this.driver = driver;
        this.original = original;
        this.llm = llm;
    }

    private static By extractBy(ElementLocator loc) {
        // Most DefaultElementLocator implementations include "By.xxx: ..." in toString
        String s = String.valueOf(loc);
        if (s == null) return null;
        // Normalize common wrappers from different PF implementations
        s = s.replace("Proxy element for: ", "")
                .replace("DefaultElementLocator '", "")
                .replace("'", "")
                .trim();

        if (s.startsWith("By.id: ")) return By.id(s.substring("By.id: ".length()));
        if (s.startsWith("By.name: ")) return By.name(s.substring("By.name: ".length()));
        if (s.startsWith("By.xpath: ")) return By.xpath(s.substring("By.xpath: ".length()));
        if (s.startsWith("By.cssSelector: ")) return By.cssSelector(s.substring("By.cssSelector: ".length()));
        if (s.startsWith("By.linkText: ")) return By.linkText(s.substring("By.linkText: ".length()));
        return null;
    }

    @Override
    public WebElement findElement() {
        try {
            return original.findElement();
        } catch (NoSuchElementException | InvalidSelectorException ex) {
            // Try to introspect the By using toString() of the proxy impl (best-effort).
            By guessed = extractBy(original);
            if (guessed != null) {
                log.info("[HEALING] Delegating to SelfHealingLocator for: {}", guessed);
                return new SelfHealingLocator(driver, llm).find(guessed);
            }
            throw ex;
        }
    }

    @Override
    public List<WebElement> findElements() {
        try {
            return original.findElements();
        } catch (NoSuchElementException | InvalidSelectorException ex) {
            By guessed = extractBy(original);
            if (guessed != null) {
                log.info("[HEALING] Delegating list to SelfHealingLocator for: {}", guessed);
                WebElement single = new SelfHealingLocator(driver, llm).find(guessed);
                return List.of(single);
            }
            throw ex;
        }
    }
}
