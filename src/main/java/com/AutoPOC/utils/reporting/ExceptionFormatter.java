package com.AutoPOC.utils.reporting;

import java.util.Arrays;

/**
 * Utility class to format exceptions for reporting.
 * Adds line-by-line formatting, HTML tags for Extent Report,
 * and ensures the exception is human-readable.
 */
public class ExceptionFormatter {

    /**
     * Formats the given Throwable into a structured, readable String.
     * <p>
     * Each line will be separated with HTML line breaks (<br>) to display properly
     * in Extent Reports. Only the root cause stack trace is shown.
     *
     * @param t The Throwable (Exception or Error) to format.
     * @return A nicely formatted HTML string for reporting.
     */
    public static String formatException(Throwable t) {
        if (t == null) {
            return "No exception available.";
        }

        StringBuilder sb = new StringBuilder();

        // Include exception type and message
        sb.append("<b>").append(t.getClass().getName()).append(":</b> ").append(t.getMessage()).append("<br>");

        // Include stack trace (up to 10 elements for readability)
        Arrays.stream(t.getStackTrace())
                .limit(10) // Limit to avoid huge stack dumps
                .forEach(ste -> sb.append(escapeHtml(ste.toString())).append("<br>"));

        return sb.toString();
    }

    /**
     * Escapes HTML special characters to prevent breaking the report rendering.
     *
     * @param input Input text to escape.
     * @return Escaped string.
     */
    private static String escapeHtml(String input) {
        if (input == null) return null;
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
