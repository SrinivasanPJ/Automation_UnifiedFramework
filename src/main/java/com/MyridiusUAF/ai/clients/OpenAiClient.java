package com.MyridiusUAF.ai.clients;

import com.MyridiusUAF.ai.LlmClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Minimal OpenAI Chat Completions client (no 3rd-party JSON deps).
 * Robustly extracts choices[0].message.content (handles escaped quotes/newlines),
 * so long code blocks aren't truncated.
 */
public class OpenAiClient implements LlmClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final String apiKey;
    private final String model;
    private final String endpoint;

    public OpenAiClient() {
        this.apiKey = System.getenv("OPENAI_API_KEY");
        this.model = System.getenv("LLM_MODEL") != null ? System.getenv("LLM_MODEL") : "gpt-4o-mini";
        this.endpoint = System.getenv("OPENAI_ENDPOINT") != null
                ? System.getenv("OPENAI_ENDPOINT")
                : "https://api.openai.com/v1/chat/completions";
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY env var is not set.");
        }
    }

    /**
     * JSON-escape a Java string.
     */
    private static String json(String s) {
        if (s == null) s = "";
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /**
     * Extracts choices[0].message.content while honoring JSON string escaping.
     * We scan from the `"choices"` section to the first `"content"` occurrence.
     */
    private static String extractAssistantContent(String json) {
        if (json == null || json.isEmpty()) return "";
        int choices = json.indexOf("\"choices\"");
        if (choices < 0) return json;

        int contentKey = json.indexOf("\"content\"", choices);
        if (contentKey < 0) return json;

        int colon = json.indexOf(':', contentKey);
        if (colon < 0) return json;

        // Find the opening quote of the string value
        int open = json.indexOf('"', colon + 1);
        if (open < 0) return json;

        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = open + 1; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (escape) {
                // minimal unescape for readability
                switch (ch) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(ch);
                }
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                // end of string
                break;
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String body = """
                    {
                      "model": %s,
                      "messages": [
                        {"role":"system","content":%s},
                        {"role":"user","content":%s}
                      ],
                      "temperature": 0.2,
                      "max_tokens": 2000
                    }
                    """.formatted(json(model), json(systemPrompt), json(userPrompt));

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new RuntimeException("OpenAI error " + res.statusCode() + ": " + res.body());
            }

            return extractAssistantContent(res.body());
        } catch (Exception e) {
            throw new RuntimeException("LLM call failed: " + e.getMessage(), e);
        }
    }
}
