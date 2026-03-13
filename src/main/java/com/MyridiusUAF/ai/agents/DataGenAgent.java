package com.MyridiusUAF.ai.agents;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataGenAgent {
    private final LlmClient llm;
    private final ObjectMapper om = new ObjectMapper();

    public DataGenAgent(LlmClient llm) {
        this.llm = llm;
    }

    public DataGenAgent() {
        this(AiSwitches.dataGenEnabled()
                ? com.MyridiusUAF.ai.LlmClientFactory.maybeCreate()
                : null);
    }

    private static void putStr(Map<String, String> out, String key, Map<String, Object> src, String... aliases) {
        for (String a : aliases) {
            Object v = src.get(a);
            if (v == null) v = src.get(a.toLowerCase());
            if (v == null) v = src.get(cap(a));
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) {
                    out.put(key, s);
                    return;
                }
            }
        }
    }

    private static String cap(String s) {
        return s == null ? null : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static boolean isValid(Map<String, String> m) {
        return m != null
                && notBlank(m.get("firstName"))
                && notBlank(m.get("lastName"))
                && emailLike(m.get("email"));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static boolean emailLike(String s) {
        return notBlank(s) && s.contains("@") && s.indexOf('@') > 0;
    }

    /**
     * Extract first balanced JSON object; also strips ```json fences if present.
     */
    private static String extractJson(String s) {
        if (s == null) return "{}";
        s = s.trim().replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        int start = s.indexOf('{');
        if (start < 0) return "{}";
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        return "{}";
    }

    /**
     * Returns a strict profile or a deterministic fallback when the model is absent/invalid.
     */
    public Map<String, String> userProfile() {
        Map<String, String> m = (llm == null) ? null : askLlm();
        if (!isValid(m)) return fallback();
        return m;
    }

    // ── internals ────────────────────────────────────────────────────────
    private Map<String, String> askLlm() {
        try {
            String sys = "You are a test data generator for a generic web application. "
                    + "Output ONLY compact JSON with the keys "
                    + "firstName,lastName,email,phone,address. "
                    + "Be completely domain-neutral. No prose, no comments.";

            String user = "Generate a realistic but fictional user profile. "
                    + "Keys MUST be exactly: firstName,lastName,email,phone,address. "
                    + "Email must be syntactically valid and clearly fake. "
                    + "Phone must be a realistic-looking phone number (digits, optional separators), "
                    + "but do not assume any specific country format. "
                    + "Return JSON only.";

            String raw = llm.chat(sys, user);
            String json = extractJson(raw);

            Map<String, Object> src = om.readValue(json, new TypeReference<>() {});
            Map<String, String> out = new LinkedHashMap<>();

            putStr(out, "firstName", src, "firstName", "first_name", "givenName", "given_name", "first");
            putStr(out, "lastName", src, "lastName", "last_name", "surname", "family_name", "last");
            putStr(out, "email", src, "email", "mail", "emailAddress", "email_address");
            putStr(out, "phone", src, "phone", "phoneNumber", "phone_number", "mobile");
            putStr(out, "address", src, "address", "street", "address", "streetAddress", "street_address");

            return out;
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Deterministic fallback (good for pipelines + easy to spot in logs).
     */
    private Map<String, String> fallback() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("firstName", "Alex");
        m.put("lastName", "Miller");
        long suffix = System.currentTimeMillis() % 100000;
        m.put("email", "alex.miller+" + suffix + "@example.com");
        m.put("phone", "555-010-2345");
        m.put("address", "123 Example Street, Sample City, Sample State 000000");
        return m;
    }
}
