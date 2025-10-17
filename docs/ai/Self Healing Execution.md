# Self-Healing Execution – Quick Demo Guide (Zero Config)

> A thin wrapper around Selenium that repairs broken locators **at runtime**.

## What it is

1. **Deterministic heuristics first** – ID/class relaxations, XPath→CSS conversion, robust text/attribute fallbacks, and
   special cases like **“Log in”**.
2. **LLM second (optional)** – if heuristics fail, ask the LLM for **one safe CSS selector** using a trimmed DOM
   snapshot (first ~15k chars).
3. **Full audit trail** – healing attempts are logged as `[HEALING] …` and `[HEALING-LLM] …`.

---

## How it’s wired (one-time)

**Delegate failures to the SelfHealingLocator** from your custom ElementLocator:

```java
public class HealingElementLocator implements org.openqa.selenium.support.pagefactory.ElementLocator {
    private final SearchContext ctx;
    private final By original;
    private final SelfHealingLocator healer;

    public HealingElementLocator(WebDriver driver, SearchContext ctx, By original, LlmClient llm) {
        this.ctx = ctx;
        this.original = original;
        this.healer = new SelfHealingLocator(driver, llm); // llm may be null
    }

    @Override
    public WebElement findElement() {
        try {
            return ctx.findElement(original);
        } catch (RuntimeException e) {
            return healer.find(original); // throws if it can’t heal
        }
    }

    @Override
    public List<WebElement> findElements() {
        try {
            return ctx.findElements(original);
        } catch (RuntimeException e) {
            return List.of(healer.find(original));
        }
    }
}
```

Wrap your PageFactory init with your **HealingFieldDecorator** so `@FindBy` fields route through the locator above:

```java
PageFactory.initElements(new HealingFieldDecorator(driver, llmClient /* or null */), this);
```

---

## Zero-config defaults

* **No** properties file required.
* **Heuristics always on.**
* **AI off by default.** If you don’t pass an `LlmClient`, only heuristics run.

### Optional env (enable AI if you want)

```bash
# Groq (via your OpenAiClient wrapper)
set OPENAI_API_KEY=YOUR_KEY
set OPENAI_ENDPOINT=https://api.groq.com/openai/v1/chat/completions
set LLM_MODEL=llama-3.1-8b-instant
```

If these aren’t set (or you pass `llm = null`), the demo still runs using heuristics only.

---

## What the heuristics do (highlights)

* **CSS id/class relaxations**
  `#foo` → `[id$='foo']`, `[id*='foo']`
  `.btnPrimary` → `[class*='btnPrimary']`
* **XPath→CSS fixups**
  `//tag[@attr='val']` (or malformed `@attr 'val'`) → `tag[attr='val']` / `tag[attr*='val']`
* **Common text/value shortcuts**
  Buttons/inputs containing **“Log in”**
* **ID/Name/LinkText fallbacks**
  Convert to robust CSS/XPath with `contains(...)`
* A special case for **order-number** and DOM snapshot trimmed to ~15k chars before calling the LLM.

---

## Run the demo

1. Leave a broken locator in a page/test, e.g.:

   ```java
   By.xpath("//input[@valueLog in]") // missing '='
   ```
2. Ensure your page fields are initialized via the healing decorator.
3. Run:

   ```bash
   mvn -q -Dtest=TriageFailDemoTest test
   ```

---

## What you’ll see

Console logs similar to:

```
[HEALING] Delegating to SelfHealingLocator for: By.xpath: //input[@valueLog in]
[HEALING] ✅ Healed with: By.cssSelector: input[value='Log in']
```

(You may see repeated heals if the element is re-queried for `click`, `isDisplayed`, `sendKeys`, etc. That’s expected.)

If AI is enabled **and** heuristics fail:

```
[HEALING-LLM] Suggested CSS: input[value='Log in']
```

The test proceeds **without changing** your test code.

---

## Observability

* **Logs** via SLF4J: `[HEALING] …`, `[HEALING-LLM] …`
* **Extent (optional)** – if you want the heal in the report:

  ```java
  ExtentReportManager.INSTANCE.logInfo("[HEALING] Healed with: By.cssSelector: input[value='Log in']",
                                       SelfHealingLocator.class);
  ```

  (Not required for the demo.)

---

## Troubleshooting

* **Still not healing?**
  • The element genuinely changed/vanished.
  • Heuristic pattern didn’t match (e.g., you’re in the wrong `frame/iframe`).
  • AI disabled (no env + `llm = null`).
  • Rare: AI suggested an invalid CSS – code already guards and fails gracefully.
* **Too many duplicate logs?**
  Multiple lookups cause multiple log lines. If noisy, cache the **healed `By` per field** for the test duration.

---

## DoD (Definition of Done)

* Heuristics repair malformed XPath like `@valueLog in` into a working CSS.
* If AI is toggled on, **one** safe CSS suggestion is attempted after heuristics.
* Successful heals log:
  `[HEALING] ✅ Healed with: …`
* **No config file** required; the demo runs out-of-the-box.

---

## Sample output

```
07:24:05 [main] INFO  ...HealingElementLocator - [HEALING] Delegating to SelfHealingLocator for: By.xpath: //input[@valueLog in]
07:24:07 [main] INFO  ...SelfHealingLocator     - [HEALING] ✅ Healed with: By.cssSelector: input[value='Log in']
... (repeats for subsequent interactions) ...
```

