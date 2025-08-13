package com.MyridiusUAF.config;

import com.MyridiusUAF.utils.reporting.LogUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;

/**
 * Centralized configuration reader for the framework.
 * <p>
 * Loading strategy (in order):
 * <ol>
 *   <li>Classpath resource {@code config.properties} (works in JARs and test runs)</li>
 *   <li>Fallback file {@code src/test/resources/config.properties} (for current project layout)</li>
 * </ol>
 * JVM system properties ({@code -Dkey=value}) transparently override loaded values.
 * <p>
 * Thread-safety: properties are loaded once at class init and treated as read-only.
 */
public final class ConfigReader {

    private static final Properties PROPS = new Properties();

    private static final String CLASSPATH_RESOURCE = "config.properties";
    private static final String TEST_RESOURCE_PATH = "src/test/resources/" + CLASSPATH_RESOURCE;

    private ConfigReader() {
        // no instances
    }

    // ---- bootstrap ----

    static {
        loadProperties();
    }

    private static void loadProperties() {
        // 1) Try classpath first (works under surefire + packaged jars)
        try (InputStream in = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(CLASSPATH_RESOURCE)) {

            if (in != null) {
                PROPS.load(in);
                LogUtil.log(ConfigReader.class, "Loaded " + CLASSPATH_RESOURCE + " from classpath.");
                return;
            }
        } catch (IOException e) {
            // Keep going to fallback; also log for visibility
            LogUtil.warn(ConfigReader.class, "Classpath load failed for " + CLASSPATH_RESOURCE + ": " + e.getMessage());
        }

        // 2) Fallback to explicit path used in the project
        try (FileInputStream fis = new FileInputStream(TEST_RESOURCE_PATH)) {
            PROPS.load(fis);
            LogUtil.log(ConfigReader.class, "Loaded " + CLASSPATH_RESOURCE + " from " + TEST_RESOURCE_PATH + ".");
        } catch (IOException e) {
            LogUtil.error(ConfigReader.class, "Failed to load " + CLASSPATH_RESOURCE + " from classpath and fallback path.", e);
            throw new RuntimeException("Could not load " + CLASSPATH_RESOURCE + " (classpath or " + TEST_RESOURCE_PATH + ")", e);
        }
    }

    // ---- public API (backward compatible) ----

    /**
     * Returns the property for {@code key}. If absent, returns an empty string.
     * <p>System properties (-Dkey=value) take precedence over file values.</p>
     */
    public static String getProperty(String key) {
        String sys = System.getProperty(key);
        if (sys != null) return sys.trim();
        return PROPS.getProperty(key, "").trim();
    }

    /**
     * Returns the property for {@code key}. If absent, returns {@code defaultValue}.
     * <p>System properties (-Dkey=value) take precedence over file values.</p>
     */
    public static String getProperty(String key, String defaultValue) {
        String sys = System.getProperty(key);
        if (sys != null) return sys.trim();

        String val = PROPS.getProperty(key);
        if (val == null) {
            LogUtil.warn(ConfigReader.class,
                    "Missing config key '" + key + "', using default '" + defaultValue + "'");
            return defaultValue;
        }
        return val.trim();
    }

    /**
     * Reads the raw property for {@code key} and Base64-decodes it.
     * <p>
     * If the value is missing, returns {@code null}. If the value is not valid Base64,
     * the original (trimmed) string is returned and a warning is logged—this preserves
     * existing behavior while surfacing bad inputs.
     * </p>
     */
    public static String getDecryptedProperty(String key) {
        // NOTE: intentionally not using getProperty(..) to avoid applying defaults here.
        String raw = System.getProperty(key); // allow -D to override secrets if needed
        if (raw == null) raw = PROPS.getProperty(key);
        if (raw == null) return null;

        String val = raw.trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(val);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            LogUtil.warn(ConfigReader.class,
                    "Value for key '" + key + "' is not valid Base64; returning raw value.");
            return val;
        }
    }

    // ---- optional conveniences (non-breaking additions) ----

    /**
     * Returns the property for {@code key} or throws if missing/blank.
     * Useful for required settings (e.g., base URL).
     */
    public static String getRequiredProperty(String key) {
        String v = getProperty(key);
        if (v.isBlank()) {
            throw new IllegalStateException("Required config key '" + key + "' is missing or blank.");
        }
        return v;
    }

    /** Parses a boolean property, defaulting to {@code defaultValue} when missing/invalid. */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String v = getProperty(key);
        if (v.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(v);
    }

    /** Parses an integer property, defaulting to {@code defaultValue} when missing/invalid. */
    public static int getInt(String key, int defaultValue) {
        String v = getProperty(key);
        if (v.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException nfe) {
            LogUtil.warn(ConfigReader.class,
                    "Invalid int for key '" + key + "' -> '" + v + "', using default '" + defaultValue + "'");
            return defaultValue;
        }
    }

    /**
     * Returns a password-like value where an environment variable can override the property.
     * If the environment variable is missing, this method falls back to {@link #getDecryptedProperty(String)}.
     * <p>Example: {@code getPassword("smtp.password", "SMTP_PASSWORD")}</p>
     */
    public static String getPassword(String propertyKey, String envKey) {
        String env = System.getenv(Objects.requireNonNull(envKey, "envKey"));
        if (env != null && !env.isBlank()) return env.trim();
        return getDecryptedProperty(Objects.requireNonNull(propertyKey, "propertyKey"));
    }
}
