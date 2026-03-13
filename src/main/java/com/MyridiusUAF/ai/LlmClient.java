package com.MyridiusUAF.ai;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;

public interface LlmClient {
    /**
     * Sends a chat-style request to an LLM.
     *
     * @param systemPrompt High level instructions (role/system)
     * @param userPrompt   The concrete user message
     * @return The assistant's reply (plain text)
     */
    String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException;
}
