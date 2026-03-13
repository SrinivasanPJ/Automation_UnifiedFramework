package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.LlmClient;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelfHealingLocator {

    private static final Logger log = LoggerFactory.getLogger(SelfHealingLocator.class);

    private final WebDriver driver;
    private final LlmClient llm; // can be null if you only want heuristic healing

    public SelfHealingLocator(WebDriver driver, LlmClient llm) {
        this.driver = driver;
        this.llm = llm;
    }

    private static String cssEsc(String s) {
        return s.replace("'", "\\'");
    }

    private static String xpathEsc(String s) {
        return s.replace("'", "\\'");
    }

    public WebElement find(By by) {
        try {
            return driver.findElement(by);
        } catch (NoSuchElementException | InvalidSelectorException ex) {   // catch both
            // 1) Heuristic fallbacks
            WebElement healed = tryHeuristics(by);
            if (healed != null) return healed;

            // 2) Ask LLM (optional)
            if (llm != null) {
                try {
                    String dom = (String) ((JavascriptExecutor) driver)
                            .executeScript("return document.documentElement.outerHTML;");
                    String suggestion = llm.chat(
                            "You are an expert UI test engineer. Given the DOM and the original locator, you return ONE safe CSS selector that finds the same element.",
                            "Original locator: " + by + "\n" +
                                    "Constraints: Prefer CSS, be strict (avoid //), target same text/label, keep within same section.\n" +
                                    "DOM:\n" + dom.substring(0, Math.min(15000, dom.length()))
                    );
                    if (suggestion != null && !suggestion.isBlank()) {
                        String cleaned = suggestion.trim().replace("```", "").replace("css", "").trim();
                        log.info("[HEALING-LLM] Suggested CSS: {}", cleaned);
                        By css = By.cssSelector(cleaned);
                        return new FluentWait<>(driver)
                                .withTimeout(Duration.ofSeconds(5))
                                .pollingEvery(Duration.ofMillis(200))
                                .ignoring(NoSuchElementException.class)
                                .until(d -> d.findElement(css));
                    }
                } catch (Throwable t) {
                    log.debug("[HEALING-LLM] Suggestion failed: {}", t.toString());
                }
            }
            throw ex;
        }
    }

    private WebElement tryHeuristics(By by) {
        try {
            String s = by.toString();

            // ---- CSS branch (kept + your order-number fallback) ----
            if (s.startsWith("By.cssSelector: ")) {
                String css = s.substring("By.cssSelector: ".length());

                if (css.contains("order-number")) {
                    List<By> cands = List.of(
                            By.cssSelector(".order-number strong"),
                            By.cssSelector("div.order-number > strong"),
                            By.xpath("//*[contains(@class,'order-number')]//strong"),
                            By.xpath("//strong[contains(normalize-space(),'Order number')]")
                    );
                    WebElement e = tryCandidates(cands);
                    if (e != null) return e;
                }

                // Generic CSS → id/class relaxations
                Matcher id = Pattern.compile("#([A-Za-z0-9_-]+)").matcher(css);
                if (id.find()) {
                    String val = id.group(1);
                    List<By> cands = List.of(
                            By.cssSelector("#" + cssEsc(val)),
                            By.cssSelector("[id$='" + cssEsc(val) + "']"),
                            By.cssSelector("[id*='" + cssEsc(val) + "']")
                    );
                    WebElement e = tryCandidates(cands);
                    if (e != null) return e;
                }
                Matcher clazz = Pattern.compile("\\.([A-Za-z0-9_-]+)").matcher(css);
                if (clazz.find()) {
                    String val = clazz.group(1);
                    List<By> cands = List.of(
                            By.cssSelector("." + cssEsc(val)),
                            By.cssSelector("[class*='" + cssEsc(val) + "']")
                    );
                    WebElement e = tryCandidates(cands);
                    if (e != null) return e;
                }
            }

            // ---- XPath branch (handles malformed attribute predicate) ----
            if (s.startsWith("By.xpath: ")) {
                String xp = s.substring("By.xpath: ".length());

                // Convert //tag[@attr='val'] *or* //tag[@attr 'val']  -> CSS tag[attr='val']
                Matcher simpleAttr = Pattern.compile("//\\s*([a-zA-Z][\\w-]*)\\s*\\[\\s*@([\\w:-]+)\\s*=?\\s*'([^']+)'\\s*]").matcher(xp);
                if (simpleAttr.find()) {
                    String tag = simpleAttr.group(1);
                    String attr = simpleAttr.group(2);
                    String val = simpleAttr.group(3);
                    List<By> cands = List.of(
                            By.cssSelector(tag + "[" + attr + "='" + cssEsc(val) + "']"),
                            By.cssSelector(tag + "[" + attr + "*='" + cssEsc(val) + "']")
                    );
                    WebElement e = tryCandidates(cands);
                    if (e != null) return e;
                }

                // Special-case: common login button/value text
                if (xp.contains("Log in")) {
                    List<By> cands = List.of(
                            By.cssSelector("input[value='Log in']"),
                            By.cssSelector("input[type='submit'][value*='Log in']"),
                            By.xpath("//button[normalize-space()='Log in']"),
                            By.xpath("//*[self::input or self::button][contains(normalize-space(@value),'Log in') or normalize-space(.)='Log in']")
                    );
                    WebElement e = tryCandidates(cands);
                    if (e != null) return e;
                }

                // Relax valid XPath predicates (only if xp was valid)
                try {
                    String relaxed = xp
                            .replaceAll("@id\\s*=\\s*'([^']+)'", "contains(@id,'$1')")
                            .replaceAll("@name\\s*=\\s*'([^']+)'", "contains(@name,'$1')")
                            .replaceAll("text\\(\\)\\s*=\\s*'([^']+)'", "contains(normalize-space(),'$1')");
                    if (!Objects.equals(relaxed, xp)) {
                        try {
                            return driver.findElement(By.xpath(relaxed));
                        } catch (NoSuchElementException ignored) {
                        }
                    }
                } catch (Exception ignored) { /* xp may be malformed */ }
            }

            // ---- id/name/linkText fallbacks (unchanged) ----
            if (s.startsWith("By.id: ")) {
                String id = s.substring("By.id: ".length());
                List<By> cands = List.of(
                        By.cssSelector("[id='" + cssEsc(id) + "']"),
                        By.cssSelector("[id$='" + cssEsc(id) + "']"),
                        By.cssSelector("[id*='" + cssEsc(id) + "']"),
                        By.xpath("//*[@id='" + xpathEsc(id) + "']"),
                        By.xpath("//*[contains(@id,'" + xpathEsc(id) + "')]")
                );
                return tryCandidates(cands);
            }
            if (s.startsWith("By.name: ")) {
                String name = s.substring("By.name: ".length());
                List<By> cands = List.of(
                        By.cssSelector("[name='" + cssEsc(name) + "']"),
                        By.cssSelector("[name$='" + cssEsc(name) + "']"),
                        By.cssSelector("[name*='" + cssEsc(name) + "']")
                );
                return tryCandidates(cands);
            }
            if (s.startsWith("By.linkText: ")) {
                String txt = s.substring("By.linkText: ".length());
                List<By> cands = List.of(
                        By.xpath("//a[normalize-space()='" + xpathEsc(txt) + "']"),
                        By.xpath("//a[contains(normalize-space(),'" + xpathEsc(txt) + "')]")
                );
                return tryCandidates(cands);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private WebElement tryCandidates(List<By> candidates) {
        for (By b : candidates) {
            try {
                log.debug("[HEALING] Trying candidate: {}", b);
                WebElement el = driver.findElement(b);
                log.info("[HEALING] Healed with: {}", b);
                return el;
            } catch (NoSuchElementException | InvalidSelectorException ignored) {
                // keep trying
            }
        }
        return null;
    }
}
