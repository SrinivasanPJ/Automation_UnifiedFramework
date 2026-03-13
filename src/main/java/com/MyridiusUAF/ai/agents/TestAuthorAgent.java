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

    // --- Authoring rules the LLM must follow (generic for any flow) ---
    private static final String EMBEDDED_AUTHORING_PROMPT = """
        You are a senior Automation Engineer working on the Tricentis Demo Web Shop
        UI regression suite. Output ONE Java TestNG class only.

        APPLICATION UNDER TEST
        - Public demo e-commerce site: "Tricentis Demo Web Shop".
        - Base URL: https://demowebshop.tricentis.com/

        FRAMEWORK CONSTRAINTS
        - Test framework: Myridius Unified Automation Framework (MyridiusUAF).
        - Base class: com.MyridiusUAF.base.BaseTest.
        - Available page objects (protected fields on BaseTest):
          - LoginPage loginPage;
          - RegisterPage registerPage;
          - ProductListPage productListPage;
          - AddProductsToCartAndPlaceOrderPage addProductsToCartAndPlaceOrderPage;
          - OrderInformationPage orderInformationPage;

        AVAILABLE METHODS ON ProductListPage:
          - deleteAddress()
          - selectProductIfNoAddressesExist()
          - selectProductBasedOnInputData()
          - addToCartAndGoToCart()
          - clickOnEstimateShippingButton()
          - clickTermsOfServiceButton()
          - clickCheckoutButton()
          - waitForCheckoutPageVisible()
          - fillBillingDetailsFromInput()
          - clickOnBillingAddressContinueButton()
          - clickOnShippingAddressContinueButton()
          - clickOnShippingMethodContinueButton()
          - clickOnPaymentMethodContinueButton()
          - clickOnPaymentInfoContinueButton()
          - checkoutConfirmation()
          - verifyCheckoutCompletedUrl()

        AVAILABLE METHODS ON LoginPage:
          - enterEmail(String email)
          - enterPassword(String password)
          - clickLoginButton()
          - clickLogoutLink()
          - isLoggedIn() : boolean
          - isLoggedOut() : boolean
          - getWelcomeMessage() : String

        AVAILABLE METHODS ON BaseTest:
          - initializeTestContext(String testId, String inputId, ITestContext context)
          - performLogin(String testId) - logs in using test data
          - getInputData() : Map<String,String> - returns current test data

        HARD RULES
        - Package: use the package provided in the input.
        - Class: use the class name provided in the input.
        - Class MUST extend com.MyridiusUAF.base.BaseTest.
        - Imports: ONLY these four (no wildcards, no extra imports):
          import org.testng.annotations.Test;
          import org.testng.ITestContext;
          import org.testng.Assert;
          import com.MyridiusUAF.base.BaseTest;
        - Create exactly one @Test method with a descriptive name based on the story.
        - Method signature must include (final ITestContext context) parameter.
        - Never create or manage WebDriver; rely on BaseTest.
        - Use the existing helper methods from BaseTest and page objects.
        - Include at least one Assert statement to verify the expected outcome.
        - If the story includes product listing/cart/checkout flow, use ProductListPage methods only.
        - If the story does not provide a testId/inputId, default to "1" and "Ip1".

        STYLE
        - One blank line after imports, then the class.
        - No comments, no TODOs, no extra helper classes.
        - Do not import com.MyridiusUAF.pages.*.
        - Do not use fully-qualified org.testng.Assert; always use Assert.
        """;

    private final LlmClient llm;

    public TestAuthorAgent(LlmClient llm) {
        this.llm = llm;
    }

    @SuppressWarnings("unused")
    public static String getTriageGuide() {
        return EMBEDDED_TRIAGE_GUIDE;
    }

    private static String stripCodeFences(String s) {
        if (s == null) return "";
        return s.replaceAll("(?s)```\\s*java\\s*", "")
                .replaceAll("(?s)```", "")
                .trim();
    }

    // ---------- helpers ----------

    private static String ensurePackageHeader(String code, String pkg) {
        String t = code.stripLeading();
        if (t.startsWith("package ")) return code;
        return "package " + pkg + ";\n\n" + code;
    }

    /**
     * Enforce imports, class name, assertion formatting, and spacing.
     */
    private static String postProcess(String code, String className) {
        // Remove any page imports or wildcards; normalize imports to exactly the 4 we want
        code = code.replaceAll("(?m)^import\\s+com\\.MyridiusUAF\\.pages\\.[^;]+;\\s*\\n", "");
        code = code.replaceAll("(?m)^import\\s+org\\.testng\\.[^;]*\\*;\\s*\\n", "");
        code = code.replaceAll("(?m)^import\\s+com\\.MyridiusUAF\\.base\\.BaseTest;\\s*$", ""); // we'll re-add clean block

        // Rebuild header with canonical imports
        String header = code.substring(0, code.indexOf(';') + 1);
        String rest = code.substring(code.indexOf(';') + 1);
        String imports = """
                
                import org.testng.annotations.Test;
                import org.testng.ITestContext;
                import org.testng.Assert;
                import com.MyridiusUAF.base.BaseTest;
                
                """;
        code = header + imports + rest.replaceFirst("^\\s*", "");

        // Ensure class name is correct
        code = code.replaceAll("(?s)public\\s+class\\s+\\w+\\s+extends\\s+BaseTest", "public class " + className + " extends BaseTest");

        // Use Assert (not fully-qualified)
        code = code.replace("org.testng.Assert.", "Assert.");

        // Fix "&&" that sometimes arrives Unicode/HTML-escaped
        code = code
                .replace("\\u0026\\u0026", "&&")
                .replace("u0026u0026", "&&")
                .replace("&amp;&amp;", "&&");

        // Exactly one blank line between imports and class
        code = code.replaceAll("\\n{3,}", "\n\n");

        return code;
    }

    public Path generateTest(String packageName, String className, String userStory, Path testSrcRoot) {
        try {
            String user = """
                    Package: %s
                    Class: %s
                    Story: %s
                    """.formatted(packageName, className, userStory);

            String code = llm.chat(EMBEDDED_AUTHORING_PROMPT, user);
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
