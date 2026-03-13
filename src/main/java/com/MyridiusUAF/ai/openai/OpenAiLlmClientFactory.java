package com.MyridiusUAF.ai.openai;

import java.time.Duration;

public final class OpenAiLlmClientFactory {
    private OpenAiLlmClientFactory() {}

    public static OpenAiLlmClient fromConfigOrNull() {
        if (!OpenAiConfig.enabled()) return null;

        String key = OpenAiConfig.apiKey();
        if (key == null || key.isBlank()) return null;

        return new OpenAiLlmClient(
                OpenAiConfig.baseUrl(),
                key,
                OpenAiConfig.model(),
                Duration.ofSeconds(OpenAiConfig.timeoutSeconds())
        );
    }
}
