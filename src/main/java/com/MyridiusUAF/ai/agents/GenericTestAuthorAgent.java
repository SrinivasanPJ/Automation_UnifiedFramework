package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.LlmClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generic (domain-neutral) TestNG test authoring agent.
 * Unlike TestAuthorAgent, this does NOT hard-code Demo Web Shop methods or flows.
 */
public class GenericTestAuthorAgent {

    private static final String EMBEDDED_AUTHORING_PROMPT_NEUTRAL = """
            You are a senior Java TestNG automation engineer working on an
            enterprise-grade, domain-agnostic automation framework called
            "Myridius Unified Automation Framework (MyridiusUAF)".

            Your job is to generate a single Java TestNG test class that:
            - Fits into the existing framework structure.
            - Uses the provided package name and class name exactly.
            - Extends com.MyridiusUAF.base.BaseTest.
            - Follows good automation design with a clear Arrange–Act–Assert flow.

            HARD RULES:
            - Output ONLY one Java class, no additional text or markdown.
            - Use these imports (no wildcards, no other test frameworks):
              import org.testng.annotations.Test;
              import org.testng.ITestContext;
              import org.testng.Assert;
              import com.MyridiusUAF.base.BaseTest;
            - Create exactly one @Test method named orderAiTest with signature:
              public void orderAiTest(final ITestContext context)
            - Never create or manage WebDriver directly; assume BaseTest controls it.
            - If the story mentions pages or actions, call methods on page objects or
              helper methods with clear, intention-revealing names
              (for example: loginPage.login(...), dashboardPage.openSettings(), etc.).
            - Do not create main methods, inner classes, or extra public classes.
            - Do not write comments, TODOs, or documentation; keep the class clean.

            STYLE:
            - One blank line after imports, then the class declaration.
            - Method names should be in camelCase and describe actions clearly.
            - Keep the test logic generic and domain-neutral; rely only on the story
              that is provided and normal web-testing patterns.
            """;

    private final LlmClient llm;

    public GenericTestAuthorAgent(LlmClient llm) {
        this.llm = llm;
    }

    // ---------- small helpers ----------

    private static String stripCodeFences(String s) {
        if (s == null) return "";
        String cleaned = s.replaceAll("(?s)```\\s*java\\s*", "")
                .replaceAll("(?s)```", "")
                .trim();
        return cleaned;
    }

    private static String ensurePackageHeader(String code, String pkg) {
        String t = code.stripLeading();
        if (t.startsWith("package ")) return code;
        return "package " + pkg + ";\n\n" + code;
    }

    /**
     * Very simple, generic post-processing:
     * - Remove existing imports
     * - Insert canonical imports
     * - Ensure class name and extends BaseTest
     */
    private static String postProcessGeneric(String code, String className) {
        // Remove all import lines
        code = code.replaceAll("(?m)^import\\s+[^;]+;\\s*\\n", "");

        // Find end of package line
        int pkgEnd = code.indexOf(';');
        if (pkgEnd < 0) {
            // No package? Just prepend imports
            String imports = """
                    import org.testng.annotations.Test;
                    import org.testng.ITestContext;
                    import org.testng.Assert;
                    import com.MyridiusUAF.base.BaseTest;

                    """;
            code = imports + code;
        } else {
            String header = code.substring(0, pkgEnd + 1);
            String rest = code.substring(pkgEnd + 1);
            String imports = """
                    
                    import org.testng.annotations.Test;
                    import org.testng.ITestContext;
                    import org.testng.Assert;
                    import com.MyridiusUAF.base.BaseTest;
                    
                    """;
            code = header + imports + rest.replaceFirst("^\\s*", "");
        }

        // Ensure class name and extends BaseTest
        code = code.replaceAll(
                "(?s)public\\s+class\\s+\\w+\\s+extends\\s+BaseTest",
                "public class " + className + " extends BaseTest"
        );

        // Ensure @Test method signature
        code = code.replaceAll(
                "(?s)@Test\\s*public\\s+void\\s+\\w+\\s*\\([^)]*\\)",
                "@Test\n    public void orderAiTest(final ITestContext context)"
        );

        // Prefer Assert over fully-qualified org.testng.Assert
        code = code.replace("org.testng.Assert.", "Assert.");

        // Normalize extra blank lines
        code = code.replaceAll("\\n{3,}", "\n\n");

        return code;
    }

    /**
     * Main entry: generate a generic TestNG test class.
     */
    public Path generateTest(String packageName, String className, String userStory, Path testSrcRoot) {
        try {
            String system = EMBEDDED_AUTHORING_PROMPT_NEUTRAL;
            String user = """
                    You are generating a TestNG test class for the following story.

                    Package: %s
                    Class: %s

                    Story:
                    %s

                    Implement this story as a single Java TestNG test class that follows
                    the hard rules in the system prompt and integrates with the
                    MyridiusUAF framework.
                    """.formatted(packageName, className, userStory);

            String code = llm.chat(system, user);
            code = stripCodeFences(code);
            code = ensurePackageHeader(code, packageName);
            code = postProcessGeneric(code, className);

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
