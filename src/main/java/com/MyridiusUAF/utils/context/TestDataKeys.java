package com.AutoPOC.utils.context;

/**
 * Constant keys used for accessing common test data fields from Excel.
 * Ensures consistent usage of test data headers across the framework.
 */
public class TestDataKeys {

    public static final String TEST_ID = "TestID";
    public static final String USERNAME = "Username";
    public static final String PASSWORD = "Password";
    public static final String BROWSER = "Browser";
    public static final String URL = "URL";
    public static final String APPLICATION = "Application";

    private TestDataKeys() {
        // Prevent instantiation
    }
}