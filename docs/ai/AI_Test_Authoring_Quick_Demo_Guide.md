````md
# AI Test Authoring From User Stories — Quick Demo Guide

Turn a plain-English user story into a compilable TestNG class that fits this project’s rules.

## 1) What this does

- CLI entrypoint **`com.MyridiusUAF.cli.AiGenerateTest`** calls **`TestAuthorAgent`** (LLM via `OpenAiClient`).
- The agent enforces **imports, class signature, allowed actions, assertions**.
- Writes the file to:  
  `src/test/java/<package>/<ClassName>.java`

If no model/key is set the run will fail fast (authoring is model-dependent). For zero-config demos, use the
Self-Healing or Triage guides instead.

## 2) Prereqs

- Java 17+ and Maven
- Internet access to your LLM endpoint
- These env vars set in your shell (examples below):
    - `OPENAI_API_KEY`
    - `LLM_MODEL` (e.g., `llama-3.1-8b-instant` or any Chat Completions model you use)
    - `OPENAI_ENDPOINT` (e.g., `https://api.groq.com/openai/v1/chat/completions`)

> The project does **not** rely on `config.properties` for authoring—env vars are enough.

## 3) One-time env setup

### Windows (PowerShell)

```powershell
$env:OPENAI_API_KEY="YOUR_KEY"
$env:LLM_MODEL="llama-3.1-8b-instant"
$env:OPENAI_ENDPOINT="https://api.groq.com/openai/v1/chat/completions"
````

### macOS/Linux (bash/zsh)

```bash
export OPENAI_API_KEY="YOUR_KEY"
export LLM_MODEL="llama-3.1-8b-instant"
export OPENAI_ENDPOINT="https://api.groq.com/openai/v1/chat/completions"
```

## 4) Run the demo command

### Example A — Story + Class + Package

```bash
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=com.MyridiusUAF.cli.AiGenerateTest \
  "-Dexec.args=As a registered user, I can purchase a product and see a thank-you page PlaceOrderAiTest com.MyridiusUAF.tests.ai"
```

### Example B — Story + Class (package defaults to `com.MyridiusUAF.tests.ai`)

```bash
mvn -q -DskipTests exec:java \
  -Dexec.mainClass=com.MyridiusUAF.cli.AiGenerateTest \
  "-Dexec.args=As a guest I can register RegisterAiTest"
```

## 5) What you’ll see

* **Console**:
  `Generated: <ABSOLUTE_PATH>/src/test/java/com/MyridiusUAF/tests/ai/PlaceOrderAiTest.java`
* **File** `PlaceOrderAiTest.java` that:

    * Extends `BaseTest`
    * Has exactly one method `orderAiTest(final ITestContext context)`
    * Calls the allowed page-object actions in order
    * Asserts order id is non-blank

> The agent’s post-processor enforces imports, signature, allowed actions, payment test values, and inserts
`orderInformationPage.clickOrderDetailsLink()` before grabbing the Order ID.

## 6) Sample output (trimmed)

```java
package com.MyridiusUAF.tests.ai;

import org.testng.annotations.Test;
import org.testng.ITestContext;
import org.testng.Assert;
import com.MyridiusUAF.base.BaseTest;

public class PlaceOrderAiTest extends BaseTest {

    @Test
    public void orderAiTest(final ITestContext context) {
        initializeTestContext("1", "Ip1", context);
        performLogin("1");

        addProductsToCartAndPlaceOrderPage.deleteAddress();
        addProductsToCartAndPlaceOrderPage.selectProductIfNoAddressesExist();
        addProductsToCartAndPlaceOrderPage.addToCartAndGoToCart();
        addProductsToCartAndPlaceOrderPage.clickOnEstimateShippingButton();
        addProductsToCartAndPlaceOrderPage.clickTermsOfServiceButton();
        addProductsToCartAndPlaceOrderPage.clickCheckoutButton();
        addProductsToCartAndPlaceOrderPage.waitForCheckoutPageVisible();
        addProductsToCartAndPlaceOrderPage.fillBillingDetailsFromInput();
        addProductsToCartAndPlaceOrderPage.clickOnBillingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingAddressContinueButton();
        addProductsToCartAndPlaceOrderPage.clickOnShippingMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.selectPaymentMethod("Credit Card");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentMethodContinueButton();
        addProductsToCartAndPlaceOrderPage.fillPaymentInformation("4485564059489345", "123");
        addProductsToCartAndPlaceOrderPage.clickOnPaymentInfoContinueButton();
        addProductsToCartAndPlaceOrderPage.checkoutConfirmation();
        addProductsToCartAndPlaceOrderPage.verifyOrderSuccessMessage();
        orderInformationPage.clickOrderDetailsLink();

        String orderId = orderInformationPage.getOrderId();
        Assert.assertTrue(orderId != null && !orderId.isBlank(),
                "Order ID should be present after placing an order.");
    }
}
```

## 7) How it works (under the hood)

* `AiGenerateTest` parses CLI args: `<story> <ClassName> [<package>]`.
* Calls `TestAuthorAgent.generateTest(...)` which:

    1. Prompts the model with a strict authoring spec (imports, signature, allowed actions).
    2. Strips code fences and **post-processes** output to enforce rules.
    3. Writes to `src/test/java/<package>/<ClassName>.java`.

## 8) Troubleshooting

* **401/403**: Check `OPENAI_API_KEY`, `OPENAI_ENDPOINT`, `LLM_MODEL`.
* **Proxy/SSL**: Run behind org proxy? Set `-Dhttps.proxyHost`/`-Dhttps.proxyPort`.
* **Wrong imports/signature**: The post-processor fixes most issues. If the file looks off, delete it and rerun.
* **Overwrite protection**: If the class already exists, delete/rename it before regenerating to avoid confusion.
* **Compilation errors**: Ensure your page objects have the methods referenced above.

## 9) Clean up

Remove generated files under `src/test/java/com/MyridiusUAF/tests/ai/` if you don’t want to keep them. Commit to a
feature branch for review if you do.

```
If you’d like, I can also drop ready-made `.md` files for the other modules (Self-Healing, Smart Test Data, AI Triage) in `docs/ai/` using the same style.
::contentReference[oaicite:0]{index=0}
```
