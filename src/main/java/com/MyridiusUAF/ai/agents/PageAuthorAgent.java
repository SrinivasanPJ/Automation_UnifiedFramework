package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.LlmClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Agent that generates Page Object classes aligned with MyridiusUAF BasePage style.
 */
public class PageAuthorAgent {

    private final LlmClient llm;

    public PageAuthorAgent(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * Generates a new page class file under the given src root.
     *
     * @param pkg             package name (e.g., com.MyridiusUAF.pages)
     * @param className       class name (e.g., LoginPage)
     * @param pageDescription description of the UI and behavior
     * @param mainSrcRoot     root directory for main sources (typically src/main/java)
     */
    public Path generatePage(String pkg,
                             String className,
                             String pageDescription,
                             Path mainSrcRoot) throws Exception {

        if (llm == null) {
            throw new IllegalStateException("LLM client is null; enable ai.openai.enabled and set OPENAI_API_KEY.");
        }

        String systemPrompt = """
    You are a senior Java Selenium architect working on the Myridius Unified Automation Framework.

    Framework constraints:
    - Page package root: com.MyridiusUAF.pages
    - All pages extend: com.MyridiusUAF.base.BasePage
    - Use org.openqa.selenium.support.FindBy for elements.
    - Use ONLY these methods inherited from BasePage for interactions:
      * click(WebElement element, String logMsg)
      * click(By locator, String logMsg)
      * sendKeys(WebElement element, String text)
      * selectByVisibleText(WebElement dropdown, String text)
      * waitUntilVisible(WebElement element, int timeoutSeconds)
      * waitUntilVisible(By locator, int timeoutSeconds)
      * waitUntilClickable(WebElement element, int timeoutSeconds)
      * waitUntilClickable(By locator, int timeoutSeconds)
      * waitUntilGone(By locator, int timeoutSeconds)
      * waitUntilUrlContains(String partialUrl)
      * waitUntilUrlIs(String expectedUrl)
      * waitForPageToLoad()
      * isDisplayed(WebElement element)
      * isElementPresent(By locator)
      * getText(WebElement element)
      * getAttribute(WebElement element, String attribute)
      * scrollIntoView(WebElement element)
      * scrollToBottom()
      * jsClick(WebElement element)
      * log(String message)
      * getInputData() : Map<String,String>  // synthetic test data
      * clickBy(String label, String value, String xpathTemplate)
      * getCurrentUrl() : String
      * closeNotificationIfPresentAndWait()
      * getNumberOfAddresses() : int
    - DO NOT use methods like type(), enter(), input(), setValue(), etc. They do not exist.
    - Do NOT create or manage WebDriver directly in the page.
    - You may use org.openqa.selenium.Alert for alert handling.
    - You may use org.openqa.selenium.support.ui.Select for dropdowns.

    Public API rules:
    - The ONLY public methods in the page class MUST be the ones explicitly described in the user prompt.
    - Do NOT add extra public methods, getters, setters, or constructors.
    - If you need internal helpers (e.g., pause(), selectStateOption(), waitForStateDropdownIfPresent()), make them private.
    - Do NOT add a main() method or any test methods.

    Code style rules:
    - Exactly ONE public class in the file.
    - Class name MUST match the requested className.
    - Package declaration MUST match the requested package.
    - Use camelCase for method names, starting with a verb.
    - Use concise, production-ready code.
    - No comments, no TODOs, no unused imports.
    """;

        String userPrompt = """
        Package: %s
        Class name: %s

        Page description (STRICT SPECIFICATION):
        %s

        Requirements:
        - Treat the page description as the complete and exact specification of the public API.
        - Implement ALL and ONLY the public methods that are mentioned in the description, with EXACT names.
        - Do NOT introduce any additional public methods.
        - You may create private helper methods if needed, but keep them minimal.

        Output:
        - Fully compilable Java code.
        - Exactly one public class.
        - No explanations or markdown, only the Java source.
        """.formatted(pkg, className, pageDescription);

        String javaCode = llm.chat(systemPrompt, userPrompt);

        String packagePath = pkg.replace('.', '/');
        Path outDir = mainSrcRoot.resolve(packagePath);
        Files.createDirectories(outDir);

        Path outFile = outDir.resolve(className + ".java");
        Files.writeString(outFile, javaCode, StandardCharsets.UTF_8);

        return outFile;
    }
}
