package com.MyridiusUAF.ai.openai;

import com.MyridiusUAF.config.ConfigReader;

/**
 * Centralized OpenAI configuration.
 * All values come from ConfigReader (which already handles -D overrides and ${ENV_VAR} expansion).
 */
public final class OpenAiConfig {
    private OpenAiConfig() {}

    public static boolean enabled() {
        return ConfigReader.getBoolean("ai.openai.enabled", true);
    }

    public static String apiKey() {
        // Supports ${OPENAI_API_KEY} in config.properties
        String v = ConfigReader.getProperty("openai.apiKey");
        return (v == null || v.isBlank()) ? null : v;
    }

    public static String baseUrl() {
        String v = ConfigReader.getProperty("openai.baseUrl", "https://api.openai.com");
        return v;
    }

    public static String model() {
        String v = ConfigReader.getProperty("openai.model", "gpt-4.1-mini");
        return v;
    }

    public static String fallbackModel() {
        String v = ConfigReader.getProperty("openai.fallbackModel", "gpt-4o-mini");
        return v;
    }

    public static int timeoutSeconds() {
        return ConfigReader.getInt("openai.timeoutSeconds", 20);
    }

    /**
     * Returns a summary of the current AI configuration for logging.
     */
    public static String configSummary() {
        return String.format("Model=%s, FallbackModel=%s, BaseUrl=%s, Timeout=%ds",
                model(), fallbackModel(), baseUrl(), timeoutSeconds());
    }
}
