package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.LlmClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a TestNG test class from a plain-English user story.
 * - Uses an embedded authoring prompt (no filesystem/classpath dependency).
 * - Writes to: {testSrcRoot}/{packagePath}/{ClassName}.java
 * - Post-processes the LLM output to enforce imports, signature, assertion, spacing.
 */
public class TestAuthorAgent {

    // --- Public TRIAGE guide remains available for listeners ---
    private static final String EMBEDDED_TRIAGE_GUIDE = """
            # TRIAGE GUIDELINES
            
            **Objective**
            Produce a concise, actionable triage for a failing TestNG UI test. Favor precision over length.
            
            ## Inputs
            - Test name and fully qualified method
            - Java stack trace (always)
            - Optional console/driver logs and last screenshot path
            
            ## Output (<=220 words)
            ### Summary
            ### Root Cause (locator drift | timing/sync | windowing/modal | test data/env | product bug | infra)
            ### Evidence
            - Quote exact stack lines, e.g. `NoSuchElementException at LoginPage.java:57 (By.id: "email")`
            ### Fix Suggestion
            - One concrete code/locator change; prefer CSS.
            """;
    // --- Authoring rules the LLM must follow (kept short & strict) ---
    private static final String EMBEDDED_AUTHORING_PROMPT = """
            You are a senior Automation Engineer. Output ONE Java TestNG class only.
            
            HARD RULES
            - Package: as provided.
            - Imports: ONLY these four (no wildcards, no page imports):
              import org.testng.annotations.Test;
              import org.testng.ITestContext;
              import org.testng.Assert;
              import com.MyridiusUAF.base.BaseTest;
            - Class extends com.MyridiusUAF.base.BaseTest.
            - Exactly one @Test method named: orderAiTest
              Signature: public void orderAiTest(final ITestContext context)
            - Never instantiate WebDriver or page objects; use BaseTest fields.
            - Arrange:
                initializeTestContext("1","Ip1", context);
                performLogin("1");
            - Act (allowed methods only on addProductsToCartAndPlaceOrderPage):
                deleteAddress(); selectProductIfNoAddressesExist(); addToCartAndGoToCart();
                clickOnEstimateShippingButton(); clickTermsOfServiceButton(); clickCheckoutButton();
                waitForCheckoutPageVisible(); fillBillingDetailsFromInput();
                clickOnBillingAddressContinueButton(); clickOnShippingAddressContinueButton();
                clickOnShippingMethodContinueButton(); selectPaymentMethod("Credit Card");
                clickOnPaymentMethodContinueButton(); fillPaymentInformation("4485564059489345","123");
                clickOnPaymentInfoContinueButton(); checkoutConfirmation(); verifyOrderSuccessMessage();
            - Assert: String orderId = orderInformationPage.getOrderId();
              Assert.assertTrue(orderId != null && !orderId.isBlank(), "Order ID should be present after placing an order.");
            - Style: One blank line after imports, then class. No comments or extra sections.
            - Do not import com.MyridiusUAF.pages.* or use fully-qualified org.testng.Assert.
            """;
    private final LlmClient llm;

    public TestAuthorAgent(LlmClient llm) {
        this.llm = llm;
    }

    public static String getTriageGuide() {
        return EMBEDDED_TRIAGE_GUIDE;
    }

    private static String stripCodeFences(String s) {
        if (s == null) return "";
        String cleaned = s.replaceAll("(?s)```\\s*java\\s*", "")
                .replaceAll("(?s)```", "")
                .trim();
        return cleaned;
    }

    // ---------- helpers ----------

    private static String ensurePackageHeader(String code, String pkg) {
        String t = code.stripLeading();
        if (t.startsWith("package ")) return code;
        return "package " + pkg + ";\n\n" + code;
    }

    /**
     * Enforce imports, method signature, assertion, CC values, spacing, and the order-details click.
     */
    private static String postProcess(String code, String className) {
        // Remove any page imports or wildcards; normalize imports to exactly the 4 we want
        code = code.replaceAll("(?m)^import\\s+com\\.MyridiusUAF\\.pages\\.[^;]+;\\s*\\n", "");
        code = code.replaceAll("(?m)^import\\s+org\\.testng\\.[^;]*\\*;\\s*\\n", "");
        code = code.replaceAll("(?m)^import\\s+com\\.MyridiusUAF\\.base\\.BaseTest;\\s*$", ""); // we'll re-add clean block

        // Replace entire import block with canonical imports
        code = code.replaceFirst(
                "(?s)package\\s+[^;]+;\\s*\\n+((?:import\\s+[^;]+;\\s*\\n)*)",
                "package $0".contains("package ") ? code.substring(0, code.indexOf(';') + 1) : ""
        );
        // Safer: rebuild header
        String header = code.substring(0, code.indexOf(';') + 1);
        String rest = code.substring(code.indexOf(';') + 1);
        String imports = """
                
                import org.testng.annotations.Test;
                import org.testng.ITestContext;
                import org.testng.Assert;
                import com.MyridiusUAF.base.BaseTest;
                
                """;
        code = header + imports + rest.replaceFirst("^\\s*", "");

        // Ensure class name and method signature are consistent
        code = code.replaceAll("(?s)public\\s+class\\s+\\w+\\s+extends\\s+BaseTest", "public class " + className + " extends BaseTest");
        code = code.replaceAll(
                "(?s)@Test\\s*public\\s+void\\s+\\w+\\s*\\([^)]*\\)",
                "@Test\n    public void orderAiTest(final ITestContext context)"
        );

        // Force CC number (16) and CVV (3)
        code = code.replaceAll(
                "fillPaymentInformation\\s*\\(\\s*\"[^\"]+\"\\s*,\\s*\"[^\"]+\"\\s*\\)",
                "fillPaymentInformation(\"4485564059489345\", \"123\")"
        );

        // Use Assert (not fully-qualified)
        code = code.replace("org.testng.Assert.", "Assert.");

        // Fix "&&" that sometimes arrives Unicode/HTML-escaped
        code = code
                .replace("\\u0026\\u0026", "&&")   // literal Java-escaped
                .replace("u0026u0026", "&&")       // raw text
                .replace("&amp;&amp;", "&&");      // HTML-escaped

        // >>> Ensure we click the order details link before reading the ID <<<
        if (!code.contains("orderInformationPage.clickOrderDetailsLink()")) {
            // Prefer right after verifyOrderSuccessMessage();
            String afterVerify = code.replaceFirst(
                    "(?m)(\\bverifyOrderSuccessMessage\\s*\\(\\s*\\)\\s*;)",
                    "$1\n        orderInformationPage.clickOrderDetailsLink();"
            );
            if (!afterVerify.equals(code)) {
                code = afterVerify;
            } else {
                // Fallback: just before we get the orderId
                code = code.replaceFirst(
                        "(?m)^\\s*(String\\s+orderId\\s*=\\s*orderInformationPage\\.getOrderId\\s*\\(\\s*\\)\\s*;)",
                        "        orderInformationPage.clickOrderDetailsLink();\n        $1"
                );
            }
        }

        // Exactly one blank line between imports and class
        code = code.replaceAll("\\n{3,}", "\n\n");

        return code;
    }

    public Path generateTest(String packageName, String className, String userStory, Path testSrcRoot) {
        try {
            String system = EMBEDDED_AUTHORING_PROMPT;
            String user = """
                    Package: %s
                    Class: %s
                    Story: %s
                    """.formatted(packageName, className, userStory);

            String code = llm.chat(system, user);
            code = stripCodeFences(code);
            code = ensurePackageHeader(code, packageName);
            code = postProcess(code, className); // <- enforce your exact rules

            Path dir = testSrcRoot.resolve(packageName.replace('.', '/'));
            Files.createDirectories(dir);
            Path out = dir.resolve(className + ".java");
            Files.writeString(out, code, StandardCharsets.UTF_8);
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
