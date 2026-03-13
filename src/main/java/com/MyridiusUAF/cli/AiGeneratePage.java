package com.MyridiusUAF.cli;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.ai.agents.PageAuthorAgent;
import com.MyridiusUAF.ai.openai.OpenAiLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * CLI: Agentic AI Page Object generator.
 * Usage:
 * mvn -q -DskipTests exec:java \
 *   -Dexec.mainClass="com.MyridiusUAF.cli.AiGeneratePage" \
 *   -Dexec.args="Login page for DemoWebShop with email, password, and login button LoginPage com.MyridiusUAF.pages"
 * If you omit the package, defaults to com.MyridiusUAF.pages.
 */
public class AiGeneratePage {

    private static final Logger log = LoggerFactory.getLogger(AiGeneratePage.class);

    public static void main(String[] args) throws Exception {
        if (!AiSwitches.openAiGloballyEnabled()) {
            log.error("OpenAI is disabled. Set ai.openai.enabled=true and OPENAI_API_KEY.");
            System.exit(1);
        }

        if (args.length < 2) {
            log.error("Usage: AiGeneratePage <page description words...> <ClassName> [<PackageName>]");
            log.error("Example: AiGeneratePage \"Demo Web Shop checkout flow page ... verifyCheckoutCompletedUrl ...\" ProductListPage com.MyridiusUAF.pages");
            System.exit(1);
        }

        boolean hasPkg = args.length >= 3;
        String className = hasPkg ? args[args.length - 2] : args[args.length - 1];
        String pkg = hasPkg ? args[args.length - 1] : "com.MyridiusUAF.pages";

        int storyEndExclusive = hasPkg ? args.length - 2 : args.length - 1;
        String pageDescription = Arrays.stream(args, 0, storyEndExclusive)
                .collect(Collectors.joining(" "));

        LlmClient llm = LlmClientFactory.maybeCreate();
        if (llm == null) {
            log.error("Unable to create LLM client. Check openai.apiKey/openai.model in config.properties.");
            System.exit(1);
        }

        PageAuthorAgent agent = new PageAuthorAgent(llm);
        Path mainRoot = Path.of("src/main/java");
        Path out = agent.generatePage(pkg, className, pageDescription, mainRoot);

        log.info("Generated page class at: {}", out.toAbsolutePath());

        // Log token usage if using OpenAI client
        if (llm instanceof OpenAiLlmClient openAiClient) {
            openAiClient.logSessionUsage();
        }
    }
}
