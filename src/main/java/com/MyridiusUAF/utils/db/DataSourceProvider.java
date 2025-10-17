package com.MyridiusUAF.utils.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Provides a singleton {@link DataSource} backed by HikariCP.
 *
 * <p><strong>Behavior:</strong>
 * <ul>
 *   <li>Lazy, thread-safe initialization using double-checked locking.</li>
 *   <li>Configuration sourced from {@link DbConfig}.</li>
 *   <li>Optionally runs {@code db/schema.sql} once at startup when
 *       {@link DbConfig#AUTO_CREATE} is {@code true}.</li>
 * </ul>
 *
 * <p><strong>Notes:</strong>
 * <ul>
 *   <li>Pool name is set to {@code "UAF-MySQL"}.</li>
 *   <li>{@code schema.sql} is loaded via the context classloader
 *       and executed by splitting on semicolons. Keep statements terminated with {@code ;}.</li>
 *   <li>Any schema application error fails fast with a {@link RuntimeException}.</li>
 * </ul>
 */
public final class DataSourceProvider {

    // ---------------------------------------------------------------------
    // Singleton state
    // ---------------------------------------------------------------------

    /** Singleton Hikari DataSource instance (volatile for DCL visibility). */
    private static volatile HikariDataSource DS;

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Returns the shared {@link DataSource}. Initializes the pool on first access.
     *
     * @return lazily-initialized {@link DataSource}
     * @throws RuntimeException if optional schema application fails
     */
    public static DataSource get() {
        if (DS == null) {
            // Double-checked locking for lazy thread-safe init
            synchronized (DataSourceProvider.class) {
                if (DS == null) {
                    HikariConfig cfg = new HikariConfig();
                    cfg.setJdbcUrl(DbConfig.URL);
                    cfg.setUsername(DbConfig.USER);
                    cfg.setPassword(DbConfig.PASS);
                    cfg.setMaximumPoolSize(DbConfig.POOL_MAX);
                    cfg.setPoolName("UAF-MySQL");

                    DS = new HikariDataSource(cfg);

                    // Optionally apply schema on first initialization
                    if (DbConfig.AUTO_CREATE) runSchema(DS);
                }
            }
        }
        return DS;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Applies the DDL contained in {@code db/schema.sql} using simple semicolon splitting.
     *
     * <p>Empty/whitespace-only statements are skipped. Execution is done in sequence
     * using a single {@link Statement}. This method is intentionally simple and intended
     * for local/test bootstrap, not complex migration handling.</p>
     *
     * @param ds target data source
     * @throws RuntimeException on any I/O or SQL error
     */
    private static void runSchema(DataSource ds) {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            try (InputStream in = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("db/schema.sql")) {

                if (in != null) {
                    String ddl = new String(in.readAllBytes(), StandardCharsets.UTF_8);

                    // Naive split-by-';' — ensure statements in schema.sql end with semicolons.
                    for (String sql : ddl.split(";")) {
                        if (sql.trim().isEmpty()) continue;
                        st.execute(sql);
                    }
                }
            }
        } catch (Exception e) {
            // Preserve existing behavior: fail fast if schema cannot be applied
            throw new RuntimeException("Failed to apply schema.sql", e);
        }
    }

    /** Utility class; no instances. */
    private DataSourceProvider() {}
}
