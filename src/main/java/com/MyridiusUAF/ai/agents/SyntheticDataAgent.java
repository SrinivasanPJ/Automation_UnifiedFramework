package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent that asks the LLM to generate synthetic test data rows
 * for the Synthetic_Data sheet.
 *
 * It is intentionally defensive:
 * - Extracts JSON even if wrapped in ``` fences.
 * - Tolerates extra keys.
 * - Always returns at least fallback rows if the model misbehaves.
 */
public class SyntheticDataAgent {

    private static final Logger log = LoggerFactory.getLogger(SyntheticDataAgent.class);

    private final LlmClient llm;
    private final ObjectMapper om = new ObjectMapper();

    public SyntheticDataAgent(LlmClient llm) {
        this.llm = llm;
    }

    /**
     * Generate up to rowCount rows for the given headers + scenario.
     *
     * @param headers  All header names from the Synthetic_Data sheet (in order)
     * @param rowCount Number of rows requested
     * @param scenario Natural-language scenario description
     */
    public List<Map<String, String>> generateRows(List<String> headers, int rowCount, String scenario) {
        if (llm == null) {
            log.warn("LLM client is null; cannot generate synthetic data.");
            return Collections.emptyList();
        }
        if (rowCount <= 0) {
            log.warn("RowCount <= 0; nothing to generate.");
            return Collections.emptyList();
        }

        // Normalise & prepare header list for the prompt
        List<String> cleanedHeaders = headers.stream()
                .map(h -> h == null ? "" : h.trim())
                .collect(Collectors.toList());
        String nonBlankHeaders = cleanedHeaders.stream()
                .filter(h -> !h.isBlank())
                .collect(Collectors.joining(", "));

        try {
            String system = """
        You are a senior QA engineer generating synthetic test data
        for an Excel sheet used by an automation framework.

        You MUST output ONLY JSON – no prose, no explanations.

        The JSON MUST be an array of objects.
        - The array length must be exactly the requested row count.
        - Each object represents one test data row.
        - The keys of each object MUST be chosen from the header list
          provided by the user. Do NOT invent new column names.

        If the sheet has columns named 'Category' and 'Sub-Category',
        use domain-meaningful values based on the test case semantics:
        - For login / authentication / credentials / password flows:
          Category = "Authentication", Sub-Category = "Login"
        - For logout / session timeout / invalid session flows:
          Category = "Session Management", Sub-Category = "Logout"

        If the sheet has address / product columns such as:
        Country, State, City, Zip, Product title, Address 1,
        you MUST generate realistic but diverse values:
        - Use different countries / states / cities across rows.
        - Use different product titles across rows.
        - Zip codes must look valid for the chosen country.

        Use clear, realistic values that would work against a generic
        demo e-commerce website such as a demo web shop.
        """;


            String user = """
                    We are filling the 'Synthetic_Data' Excel sheet.

                    Scenario:
                    %s

                    Number of rows to generate: %d

                    Headers (in order, some may be blank and can be ignored):
                    %s

                    Output:
                    - JSON array only
                    - Each element is an object.
                    - Object keys MUST be taken from the header list above
                      (case-sensitive).
                    - If a header is blank, you may ignore it.
                    """.formatted(scenario, rowCount, nonBlankHeaders);

            String raw = llm.chat(system, user);
            log.debug("Raw LLM response for synthetic data: {}", raw);

            String json = extractJson(raw);

            List<Map<String, Object>> rawRows = om.readValue(
                    json,
                    new TypeReference<List<Map<String, Object>>>() {}
            );

            if (rawRows == null || rawRows.isEmpty()) {
                log.warn("Parsed JSON array is null/empty. Falling back.");
                return fallbackRows(cleanedHeaders, rowCount, scenario);
            }

            List<Map<String, String>> result = new ArrayList<>();

            for (Map<String, Object> rawRow : rawRows) {
                Map<String, String> out = new LinkedHashMap<>();
                for (String header : cleanedHeaders) {
                    if (header == null || header.isBlank()) {
                        continue; // keep position but ignore value
                    }
                    Object v = findKeyCaseInsensitive(rawRow, header);
                    out.put(header, v == null ? "" : String.valueOf(v).trim());
                }
                result.add(out);
                if (result.size() >= rowCount) break;
            }

            if (result.isEmpty()) {
                log.warn("After mapping headers, all rows were empty. Falling back.");
                return fallbackRows(cleanedHeaders, rowCount, scenario);
            }

            log.info("SyntheticDataAgent generated {} row(s) from LLM.", result.size());
            return result;
        } catch (Exception e) {
            log.warn("SyntheticDataAgent failed to parse LLM response; falling back. Error: {}", e.toString());
            return fallbackRows(headers, rowCount, scenario);
        }
    }

    // ───── helpers ─────────────────────────────────────────────────────────

    private static Object findKeyCaseInsensitive(Map<String, Object> map, String key) {
        if (map.containsKey(key)) return map.get(key);
        String lower = key.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getKey() != null && e.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
                return e.getValue();
            }
        }
        return null;
    }

    /**
     * Extract first balanced JSON array or object; strips ``` fences if present.
     */
    private static String extractJson(String s) {
        if (s == null) return "[]";
        s = s.trim()
                .replaceAll("^```(?:json)?\\s*", "")
                .replaceAll("\\s*```$", "");
        int start = s.indexOf('[');
        if (start < 0) start = s.indexOf('{');
        if (start < 0) return "[]";

        int depth = 0;
        char open = s.charAt(start);
        char close = (open == '[') ? ']' : '}';
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == open) depth++;
            else if (c == close) {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return "[]";
    }

    private List<Map<String, String>> fallbackRows(List<String> headers, int rowCount, String scenario) {
        log.info("Using deterministic fallback rows for Synthetic_Data.");
        List<Map<String, String>> rows = new ArrayList<>();

        String scenarioLower = scenario == null ? "" : scenario.toLowerCase(Locale.ROOT);
        boolean logoutLike = scenarioLower.contains("logout")
                || scenarioLower.contains("session")
                || scenarioLower.contains("timeout");

        // Simple pools for diversity
        String[] countries  = { "USA", "Canada", "UK", "Germany", "Australia" };
        String[] states     = { "California", "Ontario", "England", "Bavaria", "New South Wales" };
        String[] cities     = { "Los Angeles", "Toronto", "London", "Munich", "Sydney" };
        String[] zips       = { "90001", "M5H 2N2", "SW1A 1AA", "80331", "2000" };
        String[] products   = { "Test Product A", "Test Product B", "Test Product C", "Test Product D", "Test Product E" };
        String[] addresses1 = { "123 Main St", "456 Elm St", "789 Oak St", "321 Pine St", "901 Maple St" };

        for (int i = 0; i <= rowCount - 1; i++) {
            Map<String, String> row = new LinkedHashMap<>();
            int idx = i % countries.length;

            for (String h : headers) {
                if (h == null || h.isBlank()) {
                    continue;
                }
                String key = h.trim();

                switch (key) {
                    case "Application" -> row.put(key, "Demo Web Shop");
                    case "Test Type" -> row.put(key, "Regression");
                    case "Functionality" -> {
                        if (logoutLike) {
                            row.put(key, "User Session Management");
                        } else {
                            row.put(key, "User Authentication");
                        }
                    }
                    case "Scenario" -> row.put(key, "Existing User");
                    case "Test Case" -> row.put(key,
                            logoutLike
                                    ? "Valid Logout"
                                    : (i % 2 == 0 ? "Invalid Credentials" : "Valid Credentials"));
                    case "Category" -> row.put(key,
                            logoutLike ? "Session Management" : "Authentication");
                    case "Sub-Category" -> row.put(key,
                            logoutLike ? "Logout" : "Login");

                    // Address / product diversity
                    case "Country" -> row.put(key, countries[idx]);
                    case "State" -> row.put(key, states[idx]);
                    case "City" -> row.put(key, cities[idx]);
                    case "Zip" -> row.put(key, zips[idx]);
                    case "Product title" -> row.put(key, products[idx]);
                    case "Address 1" -> row.put(key, addresses1[idx]);
                    case "Run_ID" -> row.put(key, "AI-RUN-" + (i + 1));
                    case "Execution_Flag" -> row.put(key, "Y");
                    default -> row.put(key, ""); // unknown columns remain blank
                }
            }
            rows.add(row);
        }
        return rows;
    }
}
