package com.MyridiusUAF.ai;

import com.MyridiusUAF.ai.clients.OpenAiClient;

public final class LlmClientFactory {
    private LlmClientFactory() {
    }

    /**
     * Returns a ready client or null (never throws).
     */
    public static LlmClient maybeCreate() {
        if (!AiSwitches.openAiGloballyEnabled()) return null;
        try {
            return new OpenAiClient();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
