package com.MyridiusUAF.utils.reporting;

import com.aventstack.extentreports.Status;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for unified output to console, automation.log, and Extent Report.
 * <p>
 * All log methods route to both SLF4J (console/file) and Extent Report.
 * Use the appropriate log level for information, warnings, errors, pass/fail/skip, etc.
 * </p>
 */
public final class LogUtil {

    private LogUtil() {
    } // Prevent instantiation

    /**
     * Logs an informational message (alias for {@link #info(Class, String)}).
     */
    public static void log(Class<?> clazz, String message) {
        info(clazz, message);
    }

    /**
     * Logs an informational message to SLF4J and Extent Report.
     */
    public static void info(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
        ExtentReportManager.INSTANCE.log(Status.INFO, message, clazz);
    }

    /**
     * Logs a warning message to SLF4J and Extent Report.
     */
    public static void warn(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).warn(message);
        ExtentReportManager.INSTANCE.log(Status.WARNING, message, clazz);
    }

    /**
     * Logs an error message to SLF4J and Extent Report.
     */
    public static void error(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).error(message);
        ExtentReportManager.INSTANCE.log(Status.FAIL, message, clazz);
    }

    /**
     * Logs an error message and stack trace to SLF4J and Extent Report.
     */
    public static void error(Class<?> clazz, String message, Throwable t) {
        LoggerFactory.getLogger(clazz).error(message, t);
        ExtentReportManager.INSTANCE.log(Status.FAIL, message + " - " + t.getMessage(), clazz);
    }

    /**
     * Logs a test PASS status to Extent Report and as info in SLF4J.
     */
    public static void pass(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
        ExtentReportManager.INSTANCE.log(Status.PASS, message, clazz);
    }

    /**
     * Logs a test SKIP status to Extent Report and as warn in SLF4J.
     */
    public static void skip(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).warn(message);
        ExtentReportManager.INSTANCE.log(Status.SKIP, message, clazz);
    }
}
