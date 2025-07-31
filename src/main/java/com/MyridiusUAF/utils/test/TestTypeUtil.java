package com.MyridiusUAF.utils.test;

/**
 * Utility for determining test type (SystemTest, EndToEnd) based on class/package name.
 * Used for dynamic logic such as Excel writing or conditional flows.
 */
public final class TestTypeUtil {

    private static final String SYSTEM_TEST_PACKAGE = "com.MyridiusUAF.SystemTest.";
    private static final String E2E_TEST_PACKAGE = "com.MyridiusUAF.EndToEnd.";

    private TestTypeUtil() {} // Prevent instantiation

    /**
     * Returns true if the calling stack contains a class in the SystemTest package.
     * <p>
     * Used for Excel logic and reporting based on dynamic package detection.
     * </p>
     */
    public static boolean isFromSystemTestPackage() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className != null && className.startsWith(SYSTEM_TEST_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the calling stack contains a class in the EndToEnd package.
     * <p>
     * Used for Excel logic and reporting based on dynamic package detection.
     * </p>
     */
    public static boolean isFromE2ETestPackage() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();
            if (className != null && className.startsWith(E2E_TEST_PACKAGE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the given className is part of the SystemTest package.
     *
     * @param className Fully-qualified class name
     * @return true if it belongs to the SystemTest package
     */
    public static boolean isSystemTestClass(String className) {
        return className != null && className.startsWith(SYSTEM_TEST_PACKAGE);
    }
}
