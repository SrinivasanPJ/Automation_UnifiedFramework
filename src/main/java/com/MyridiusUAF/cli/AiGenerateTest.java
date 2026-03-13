package com.MyridiusUAF.cli;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.ai.agents.TestAuthorAgent;
import com.MyridiusUAF.ai.openai.OpenAiLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Usage examples (no fragile quoting needed):
 * <p>
 * mvn -q -DskipTests exec:java \
 * -Dexec.mainClass=com.MyridiusUAF.cli.AiGenerateTest \
 * -Dexec.args="As a registered user, I can purchase a product and see a thank-you page" PlaceOrderAiTest com.MyridiusUAF.tests.ai
 * <p>
 * # If you omit the package, it defaults to com.MyridiusUAF.tests.ai:
 * -Dexec.args="As a guest I can register" RegisterAiTest
 */
public class AiGenerateTest {

    private static final Logger log = LoggerFactory.getLogger(AiGenerateTest.class);

    public static void main(String[] args) {

        if (!AiSwitches.authoringEnabled()) {
            log.info("AI authoring is disabled by config (ai.authoring.enabled=false).");
            return;
        }

        if (args.length < 2) {
            log.error("Usage: AiGenerateTest <user story words...> <ClassName> [<packageName>=com.MyridiusUAF.tests.ai]");
            return;
        }

        // Detect optional package: if the last token contains a dot, it's the package.
        final String defaultPkg = "com.MyridiusUAF.tests.ai";
        final boolean hasPkg = args[args.length - 1].contains(".");
        final String pkg = hasPkg ? args[args.length - 1] : defaultPkg;
        final String className = hasPkg ? args[args.length - 2] : args[args.length - 1];

        // Story is "everything before the class (and optional package)"
        final int storyEndExclusive = hasPkg ? args.length - 2 : args.length - 1;
        final String story = Arrays.stream(args, 0, storyEndExclusive)
                .collect(Collectors.joining(" "));

        if (!AiSwitches.authoringEnabled()) {
            log.error("AI test authoring is disabled. Set ai.authoring.enabled=true in config.properties.");
            System.exit(1);
        }

        LlmClient llm = LlmClientFactory.maybeCreate();
        if (llm == null) {
            log.error("Unable to create LLM client. Check openai.apiKey/openai.model in config.properties.");
            System.exit(1);
        }

        var agent = new TestAuthorAgent(llm);
        Path testRoot = Path.of("src/test/java");
        Path out = agent.generateTest(pkg, className, story, testRoot);

        log.info("Generated test at: {}", out.toAbsolutePath());

        // Log token usage if using OpenAI client
        if (llm instanceof OpenAiLlmClient openAiClient) {
            openAiClient.logSessionUsage();
        }
    }
}
