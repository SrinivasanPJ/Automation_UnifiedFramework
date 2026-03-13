package com.MyridiusUAF.ai.openai;

import com.MyridiusUAF.ai.LlmClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class OpenAiLlmClient implements LlmClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);
    private static final ObjectMapper OM = new ObjectMapper();

    // Session-level token tracking
    private int sessionPromptTokens = 0;
    private int sessionCompletionTokens = 0;
    private int sessionTotalTokens = 0;
    private int sessionCallCount = 0;

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public OpenAiLlmClient(String baseUrl, String apiKey, String model, Duration timeout) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public static OpenAiLlmClient fromEnvOrNull() {
        String key = OpenAiConfig.apiKey();
        if (key == null || key.isBlank()) return null;

        return new OpenAiLlmClient(
                OpenAiConfig.baseUrl(),
                key,
                OpenAiConfig.model(),
                Duration.ofSeconds(OpenAiConfig.timeoutSeconds())
        );
    }

    /**
     * Returns a summary of token usage for this session.
     */
    public String getSessionUsageSummary() {
        return String.format("SessionCalls=%d, TotalPromptTokens=%d, TotalCompletionTokens=%d, TotalTokens=%d",
                sessionCallCount, sessionPromptTokens, sessionCompletionTokens, sessionTotalTokens);
    }

    /**
     * Logs the session usage summary. Call this at the end of execution.
     */
    public void logSessionUsage() {
        log.info("AI Session Summary: {}", getSessionUsageSummary());
    }


    @Override
    public String chat(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        // First attempt with configured model, then a single retry with fallback model for access/model errors.
        String primary = model;
        String fallback = OpenAiConfig.fallbackModel();

        try {
            ChatResult result = chatOnce(primary, systemPrompt, userPrompt);
            logSuccess(primary, result, false);
            return result.content;
        } catch (IOException e) {
            if (fallback != null && !fallback.isBlank() && !fallback.equals(primary) && looksLikeModelAccessProblem(e)) {
                log.warn("Primary model '{}' failed ({}). Retrying with fallback model '{}'...",
                        primary, e.getMessage().split(":")[0], fallback);
                ChatResult result = chatOnce(fallback, systemPrompt, userPrompt);
                logSuccess(fallback, result, true);
                return result.content;
            }
            throw e;
        }
    }

    private void logSuccess(String modelUsed, ChatResult result, boolean wasFallback) {
        // Accumulate session totals
        sessionCallCount++;
        sessionPromptTokens += result.promptTokens;
        sessionCompletionTokens += result.completionTokens;
        sessionTotalTokens += result.totalTokens;

        String fallbackTag = wasFallback ? " [FALLBACK]" : "";
        log.info("LLM call succeeded{}: Model={}, BaseUrl={}, PromptTokens={}, CompletionTokens={}, TotalTokens={}",
                fallbackTag, modelUsed, baseUrl,
                result.promptTokens, result.completionTokens, result.totalTokens);
    }

    private ChatResult chatOnce(String modelToUse, String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        ObjectNode root = OM.createObjectNode();
        root.put("model", modelToUse);

        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userPrompt);

        root.put("temperature", 0.2);

        String body = OM.writeValueAsString(root);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("OpenAI HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 2000));
        }

        JsonNode json = OM.readTree(resp.body());

        // Extract content
        JsonNode contentNode = json.at("/choices/0/message/content");
        String content = contentNode.isMissingNode() ? null : contentNode.asText(null);

        // Extract token usage
        int promptTokens = json.at("/usage/prompt_tokens").asInt(0);
        int completionTokens = json.at("/usage/completion_tokens").asInt(0);
        int totalTokens = json.at("/usage/total_tokens").asInt(0);

        return new ChatResult(content, promptTokens, completionTokens, totalTokens);
    }

    /** Simple holder for chat response + token usage. */
    private static class ChatResult {
        final String content;
        final int promptTokens;
        final int completionTokens;
        final int totalTokens;

        ChatResult(String content, int promptTokens, int completionTokens, int totalTokens) {
            this.content = content;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
    }

    private static boolean looksLikeModelAccessProblem(IOException e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        // Typical OpenAI responses contain these indicators
        String m = msg.toLowerCase();
        return m.contains("model_not_found")
                || m.contains("does not have access")
                || m.contains("model `")
                || m.contains("http 403")
                || m.contains("http 404")
                || m.contains("http 400");
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
