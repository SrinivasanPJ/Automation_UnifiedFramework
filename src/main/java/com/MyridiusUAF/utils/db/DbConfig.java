package com.MyridiusUAF.utils.db;

import com.MyridiusUAF.config.ConfigReader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Centralized database configuration resolved from environment variables and/or application properties.
 *
 * <p><strong>Password resolution order (first match wins):</strong></p>
 * <ol>
 *   <li>Env {@code MYSQL_PASSWORD_B64} (base64-encoded)</li>
 *   <li>Property {@code db.password.base64} (base64-encoded)</li>
 *   <li>Env {@code MYSQL_PASSWORD} (plain)</li>
 *   <li>Property {@code db.password} (plain)</li>
 * </ol>
 *
 * <p>Other settings are read from {@link ConfigReader} with sane defaults and are intentionally
 * kept static/final to allow eager, thread-safe access across the application.</p>
 */
public final class DbConfig {

    // ---------------------------------------------------------------------
    // Public configuration (resolved at class-load time)
    // ---------------------------------------------------------------------

    /** JDBC URL (e.g., {@code jdbc:mysql://host:port/schema}). */
    public static final String URL = ConfigReader.getProperty("db.url");

    /**
     * Database user. Resolved as the first non-empty of:
     * <ul>
     *   <li>Env {@code MYSQL_USER}</li>
     *   <li>Property {@code db.user}</li>
     * </ul>
     */
    public static final String USER = firstNonEmpty(
            env("MYSQL_USER"),
            ConfigReader.getProperty("db.user")
    );

    /**
     * Database password. See resolution order documented above.
     * Throws {@link IllegalStateException} if no source provides a value.
     */
    public static final String PASS = resolvePassword();

    /** Maximum pool size for HikariCP; defaults to {@code 5}. */
    public static final int POOL_MAX =
            Integer.parseInt(ConfigReader.getProperty("db.pool.max", "5"));

    /** When {@code true}, apply {@code db/schema.sql} automatically on startup; defaults to {@code true}. */
    public static final boolean AUTO_CREATE =
            Boolean.parseBoolean(ConfigReader.getProperty("db.autoCreate", "true"));

    /** When {@code true}, enable SQL logging (caller decides how to use this flag); defaults to {@code false}. */
    public static final boolean LOG_SQL =
            Boolean.parseBoolean(ConfigReader.getProperty("db.log.sql", "false"));

    // ---------------------------------------------------------------------
    // Construction
    // ---------------------------------------------------------------------

    /** Utility class; no instances. */
    private DbConfig() {}

    // ---------------------------------------------------------------------
    // Resolution helpers (package-private for test visibility if needed)
    // ---------------------------------------------------------------------

    /**
     * Resolve password using the documented precedence.
     *
     * @return decoded or plain password, depending on the source
     * @throws IllegalStateException if no password could be resolved
     */
    private static String resolvePassword() {
        String b64Env  = env("MYSQL_PASSWORD_B64");
        if (notBlank(b64Env)) return decodeB64(b64Env);

        String b64Prop = ConfigReader.getProperty("db.password.base64");
        // Guard against placeholders like ${VAR}
        if (notBlank(b64Prop) && !b64Prop.startsWith("${")) return decodeB64(b64Prop);

        String plainEnv = env("MYSQL_PASSWORD");
        if (notBlank(plainEnv)) return plainEnv;

        String plainProp = ConfigReader.getProperty("db.password");
        // Guard against unresolved placeholders
        if (notBlank(plainProp) && !plainProp.startsWith("${")) return plainProp;

        throw new IllegalStateException("DB password not configured. "
                + "Set MYSQL_PASSWORD_B64 / db.password.base64 (base64) "
                + "or MYSQL_PASSWORD / db.password (plain).");
    }

    /**
     * Decodes a base64-encoded string as UTF-8.
     *
     * @param s base64 string
     * @return decoded UTF-8 string
     */
    private static String decodeB64(String s) {
        return new String(Base64.getDecoder().decode(s.trim()), StandardCharsets.UTF_8);
    }

    /**
     * Reads an environment variable and trims it; returns {@code null} if absent.
     *
     * @param k environment key
     * @return trimmed value or {@code null}
     */
    private static String env(String k) {
        String v = System.getenv(k);
        return v == null ? null : v.trim();
    }

    /**
     * Checks string for non-null and non-blank.
     *
     * @param s string to check
     * @return {@code true} if non-null and not blank
     */
    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Returns the first non-blank string from the provided values, or {@code null} if none.
     *
     * @param vals candidates in order of precedence
     * @return first non-blank or {@code null}
     */
    private static String firstNonEmpty(String... vals) {
        for (String v : vals) if (notBlank(v)) return v;
        return null;
    }
}

//    public static final String PASS =
//            ConfigReader.getBase64Property("db.password.base64") != null
//                    ? ConfigReader.getBase64Property("db.password.base64")
//                    : ConfigReader.getProperty("db.password"); // optional fallback for local/dev
