package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.ai.agents.GenericTestAuthorAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * CLI for generic (domain-neutral) AI TestNG test generation.
 *
 * Usage examples (from IntelliJ Run Configuration args):
 *
 * 1) Story + package + class:
 *    "End-to-end login and logout flow for a generic web portal com.MyridiusUAF.SystemTest LoginLogoutAiTest"
 *
 * 2) Story + class (package defaults to com.MyridiusUAF.tests.ai):
 *    "Verify user can update profile information ProfileUpdateAiTest"
 */
public class AiGenerateGenericTest {

    private static final Logger log = LoggerFactory.getLogger(AiGenerateGenericTest.class);

    public static void main(String[] args) {
        if (!AiSwitches.openAiGloballyEnabled() || !AiSwitches.authoringEnabled()) {
            log.error("AI test authoring is disabled. " +
                    "Set ai.openai.enabled=true and ai.authoring.enabled=true in config.properties.");
            System.exit(1);
        }

        if (args.length < 2) {
            log.error("Usage: AiGenerateGenericTest <user story words...> <ClassName> [<packageName>=com.MyridiusUAF.tests.ai]");
            System.exit(1);
        }

        boolean hasPkg = args.length >= 3;
        String className = hasPkg ? args[args.length - 2] : args[args.length - 1];
        String packageName = hasPkg ? args[args.length - 1] : "com.MyridiusUAF.tests.ai";

        int storyEndExclusive = hasPkg ? args.length - 2 : args.length - 1;
        String story = Arrays.stream(args, 0, storyEndExclusive)
                .collect(Collectors.joining(" "));

        LlmClient llm = LlmClientFactory.maybeCreate();
        if (llm == null) {
            log.error("Unable to create LLM client. Check OPENAI_API_KEY / ai.openai.enabled.");
            System.exit(1);
        }

        GenericTestAuthorAgent agent = new GenericTestAuthorAgent(llm);
        Path testRoot = Path.of("src/test/java");
        Path out = agent.generateTest(packageName, className, story, testRoot);

        log.info("Generated generic test at: {}", out.toAbsolutePath());
    }
}
