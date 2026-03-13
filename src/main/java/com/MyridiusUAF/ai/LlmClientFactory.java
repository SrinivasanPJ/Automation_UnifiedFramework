package com.MyridiusUAF.ai;

import com.MyridiusUAF.ai.openai.OpenAiLlmClientFactory;

public final class LlmClientFactory {
    private LlmClientFactory() {}

    public static LlmClient maybeCreate() {
        return OpenAiLlmClientFactory.fromConfigOrNull();
    }
}
