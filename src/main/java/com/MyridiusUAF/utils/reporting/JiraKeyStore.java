package com.MyridiusUAF.utils.reporting;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, lightweight keystore that maintains a mapping of
 * {@code testMethodFqn -> JIRA key}. Backed by a JSON file under {@code reports/jira-keys.json}.
 * <p>
 * Design goals:
 * <ul>
 *   <li>Non-intrusive: never throws on I/O; failures are silently ignored.</li>
 *   <li>Thread-safe: in-memory {@link ConcurrentHashMap}; atomic file writes.</li>
 *   <li>Drop-in: preserves current file location and JSON schema.</li>
 * </ul>
 */
public final class JiraKeyStore {

    private static final Path FILE = Paths.get("reports", "jira-keys.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();

    static {
        load();
    }

    private JiraKeyStore() { /* no instances */ }

    /**
     * Returns the JIRA issue key for a given fully-qualified test method name, or {@code null}.
     */
    public static String get(String methodFqn) {
        return CACHE.get(methodFqn);
    }

    /**
     * Stores/overwrites the JIRA key for a given fully-qualified test method name and persists to disk.
     * No-ops if any parameter is {@code null}.
     */
    public static void put(String methodFqn, String issueKey) {
        if (methodFqn == null || issueKey == null) return;
        CACHE.put(methodFqn, issueKey);
        save();
    }

    /**
     * Loads the cache from disk (best-effort).
     */
    private static void load() {
        try {
            if (Files.exists(FILE)) {
                byte[] bytes = Files.readAllBytes(FILE);
                Map<String, String> m = MAPPER.readValue(bytes, new TypeReference<>() {
                });
                CACHE.clear();
                CACHE.putAll(m);
            }
        } catch (Exception ignored) {
            // intentionally silent
        }
    }

    /**
     * Persists the current cache to disk using an atomic replace (best-effort).
     */
    private static void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = Files.createTempFile(FILE.getParent(), "jira-keys", ".json.tmp");
            byte[] payload = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(CACHE);
            Files.write(tmp, payload, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // intentionally silent
        }
    }

    // Kept for future use; not used directly today.
    @SuppressWarnings("unused")
    private static void writeString(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.writeString(p, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
