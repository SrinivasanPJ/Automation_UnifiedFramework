package com.AutoPOC.config;

import com.AutoPOC.utils.reporting.LogUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Properties;

/**
 * Utility class for reading configuration properties from config.properties file.
 */
public class ConfigReader {

    private static final Properties properties = new Properties();

    // Static block to load properties at class initialization
    static {
        try (FileInputStream fis = new FileInputStream("src/test/resources/config.properties")) {
            properties.load(fis);
            LogUtil.log(ConfigReader.class, "Loaded config.properties successfully.");
        } catch (IOException e) {
            LogUtil.error(ConfigReader.class, "Failed to load config.properties", e);
            throw new RuntimeException("Failed to load config.properties file", e);
        }
    }

    /**
     * Retrieves the property value for a given key.
     *
     * @param key the property key
     * @return trimmed value if found, empty string if not
     */
    public static String getProperty(String key) {
        return properties.getProperty(key, "").trim();
    }

    /**
     * Retrieves the property value for a given key, or returns a default value if the key is missing.
     *
     * @param key          the property key
     * @param defaultValue the default value to return if the key is not found
     * @return trimmed property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        String val = properties.getProperty(key);
        if (val == null) {
            LogUtil.warn(ConfigReader.class, "Missing config key '" + key + "', using default '" + defaultValue + "'");
        }
        return (val == null ? defaultValue : val).trim();
    }

    /**
     * Reads a property and Base64-decodes it.
     */
    public static String getDecryptedProperty(String key) {
        String encodedValue = properties.getProperty(key);
        if (encodedValue != null) {
            return new String(Base64.getDecoder().decode(encodedValue));
        }
        return null;
    }
}
