package com.MyridiusUAF.ai;

import com.MyridiusUAF.config.ConfigReader;

public final class AiSwitches {
    private AiSwitches() {
    }

    public static boolean selfHealingEnabled() {
        return Boolean.parseBoolean(ConfigReader.getProperty("ai.selfhealing.enabled", "false"));
    }

    public static boolean healingUseLlm() {
        return Boolean.parseBoolean(ConfigReader.getProperty("ai.healing.use.llm", "false"));
    }

    public static boolean triageEnabled() {
        return Boolean.parseBoolean(ConfigReader.getProperty("ai.triage.enabled", "false"));
    }

    public static boolean dataGenEnabled() {
        return Boolean.parseBoolean(ConfigReader.getProperty("ai.datagen.enabled", "false"));
    }

    public static boolean authoringEnabled() {
        return Boolean.parseBoolean(ConfigReader.getProperty("ai.authoring.enabled", "true"));
    }

    public static boolean openAiGloballyEnabled() {
        // must be on + have a usable API key in env
        if (!Boolean.parseBoolean(ConfigReader.getProperty("ai.openai.enabled", "true"))) return false;
        String k = System.getenv("OPENAI_API_KEY");
        return k != null && !k.isBlank();
    }
}
