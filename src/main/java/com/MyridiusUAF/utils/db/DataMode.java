package com.MyridiusUAF.utils.db;

import com.MyridiusUAF.config.ConfigReader;

/**
 * Source mode for data-driven tests.
 *
 * <p>Resolves to either {@link #EXCEL} or {@link #DATABASE} based on the
 * {@code data-driven} property from {@link ConfigReader}.</p>
 *
 * <p><strong>Resolution rule:</strong> If the property value equals
 * {@code "database"} (case-insensitive, trimmed), the mode is {@link #DATABASE};
 * otherwise it defaults to {@link #EXCEL}. The default property value used by
 * {@link #current()} is {@code "Excel"} to preserve existing behavior.</p>
 */
public enum DataMode {
    EXCEL, DATABASE;

    /**
     * Determines the active data mode from configuration.
     *
     * <p>Looks up {@code data-driven} via {@link ConfigReader#getProperty(String, String)}
     * with a default of {@code "Excel"} and returns {@link #DATABASE} only when the
     * trimmed value equals {@code "database"} (case-insensitive); otherwise {@link #EXCEL}.</p>
     *
     * @return the resolved {@link DataMode}
     */
    public static DataMode current() {
        String v = ConfigReader.getProperty("data-driven", "Excel");
        return "database".equalsIgnoreCase(v.trim()) ? DATABASE : EXCEL;
    }

    /**
     * Convenience: true when the current mode is {@link #EXCEL}.
     *
     * @return {@code true} if {@link #current()} is {@link #EXCEL}, else {@code false}
     */
    public static boolean isExcel() { return current() == EXCEL; }

    /**
     * Convenience: true when the current mode is {@link #DATABASE}.
     *
     * @return {@code true} if {@link #current()} is {@link #DATABASE}, else {@code false}
     */
    public static boolean isDb()    { return current() == DATABASE; }
}
