package com.AutoPOC.utils.reporting;

import com.aventstack.extentreports.Status;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for controlled output to console, automation.log, and Extent Report.
 */
public class LogUtil {

    public static void log(Class<?> clazz, String message) {
        info(clazz, message);
    }

    public static void info(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
        ExtentReportManager.INSTANCE.log(Status.INFO, message, clazz); // <-- Correct
    }

    public static void warn(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).warn(message);
        ExtentReportManager.INSTANCE.log(Status.WARNING, message, clazz); // <-- Correct
    }

    public static void error(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).error(message);
        ExtentReportManager.INSTANCE.log(Status.FAIL, message, clazz); // <-- Correct
    }

    public static void error(Class<?> clazz, String message, Throwable t) {
        LoggerFactory.getLogger(clazz).error(message, t);
        ExtentReportManager.INSTANCE.log(Status.FAIL, message + " - " + t.getMessage(), clazz);
    }

    public static void pass(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).info(message);
        ExtentReportManager.INSTANCE.log(Status.PASS, message, clazz);
    }

    public static void skip(Class<?> clazz, String message) {
        LoggerFactory.getLogger(clazz).warn(message);
        ExtentReportManager.INSTANCE.log(Status.SKIP, message, clazz);
    }
}
