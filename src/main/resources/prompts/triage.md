# TRIAGE GUIDELINES

**Objective**
Produce a concise, **actionable** triage for a failing TestNG UI test. Favor precision over length.

---

## Inputs (what you will receive)

- Test name and fully qualified method
- Java stack trace (always)
- Optional snippets from console/driver logs and last screenshot path

---

## Output Contract (strict Markdown)

Write **only** the sections below, in this order. Keep total under **220 words**.

### Summary

- 1–2 sentences in plain English: what failed and why (your best judgment).

### Root Cause (choose one bucket)

- **locator drift** | **timing/sync** | **windowing/modal** | **test data/env** | **product bug** | **infra**
- Short justification.

### Evidence

- Quote the **exact stack lines** (class:line, exception) or log tokens that support your call.
- Example: `org.openqa.selenium.NoSuchElementException at LoginPage.java:57 (By.id: "email")`

### Fix Suggestion

- One concrete **code or locator** change. Prefer CSS over XPath when possible.
- If relevant, show a **minimal diff** or snippet, e.g.:

```java
// before
By email = By.id("Email");
// after (more robust)
By email = By.cssSelector("input#Email, input[name='Email']");
