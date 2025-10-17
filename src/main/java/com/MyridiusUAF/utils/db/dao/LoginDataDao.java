package com.MyridiusUAF.utils.db.dao;

import com.MyridiusUAF.base.BasePage;
import com.MyridiusUAF.utils.db.Sql;
import com.MyridiusUAF.utils.db.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * DAO for login-related test data backed by the {@code common_testdata} table.
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Lookup by test id returning a header-compatible map for existing Excel flows</li>
 *   <li>Retrieval of all test ids for test data providers</li>
 *   <li>Upsert of credentials/registration fields</li>
 * </ul>
 *
 * <p><b>Notes:</b> Uses {@link DataSourceProvider} for connections and
 * {@link Sql} for SQL statements. Exceptions are wrapped in {@link RuntimeException}
 * to keep calling code concise, matching existing behavior.</p>
 */
public class LoginDataDao {

    /** Shared DataSource (existing behavior: obtain via DataSourceProvider). */
    private final DataSource ds = DataSourceProvider.get();
    private static final Logger LOGGER = LoggerFactory.getLogger(BasePage.class);

    /**
     * Fetches a single row by {@code test_id} and maps it to an Excel-style header map.
     *
     * <p>Keys are capitalized to remain compatible with existing Excel-based consumers:</p>
     * <pre>
     * TestID, URL, Username, Password, Browser, Application, Register Date, Register Time
     * </pre>
     *
     * @param testId test identifier
     * @return a {@link LinkedHashMap} of header → value if found; otherwise {@code Collections.emptyMap()}
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public Map<String, String> byTestId(final String testId) {
        LOGGER.info("Login data source = DATABASE (table=common_testdata, test_id={})", testId);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.LOGIN_BY_ID)) {

            LOGGER.debug("DB[common_testdata]: SQL={}, param1={}", Sql.LOGIN_BY_ID, testId);
            ps.setString(1, testId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    LOGGER.warn("DB[common_testdata]: NO ROW for test_id={}", testId);
                    return java.util.Collections.emptyMap();
                }

                // Maintain Excel header compatibility and predictable iteration order.
                Map<String, String> m = new java.util.LinkedHashMap<>();
                m.put("TestID",        rs.getString("test_id"));
                m.put("URL",           rs.getString("url"));
                m.put("Username",      rs.getString("username"));
                m.put("Password",      rs.getString("password"));
                m.put("Browser",       rs.getString("browser"));
                m.put("Application",   rs.getString("application"));
                m.put("Register Date", toStr(rs.getDate("register_date")));
                m.put("Register Time", toStr(rs.getTime("register_time")));

                // Log key fields without exposing secrets (only length of password).
                String username   = m.get(com.MyridiusUAF.utils.context.TestDataKeys.USERNAME);      // "Username"
                String application= m.get(com.MyridiusUAF.utils.context.TestDataKeys.APPLICATION);   // "Application"
                String browser    = m.get(com.MyridiusUAF.utils.context.TestDataKeys.BROWSER);       // "Browser"
                int pwdLen = java.util.Optional.ofNullable(
                        m.get(com.MyridiusUAF.utils.context.TestDataKeys.PASSWORD)).map(String::length).orElse(0);

                LOGGER.info("DB[common_testdata]: found row test_id={}, username={}, application={}, browser={}, password_length={}",
                        testId, username, application, browser, pwdLen);

                return m;
            }
        } catch (SQLException e) {
            LOGGER.error("DB[common_testdata]: byTestId failed for test_id={}", testId, e);
            throw new RuntimeException("LoginDataDao.byTestId failed", e);
        }
    }

    /**
     * Returns all {@code test_id} values as a two-dimensional array for TestNG data providers.
     *
     * @return {@code Object[][]} where each row contains a single {@code String} test id
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public Object[][] allTestIdsForDataProvider() {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.LOGIN_ALL_IDS);
             ResultSet rs = ps.executeQuery()) {

            // Each row: one column (test_id) wrapped as Object[]{ test_id }
            while (rs.next()) {
                rows.add(new Object[]{rs.getString(1)});
            }
        } catch (SQLException e) {
            throw new RuntimeException("LoginDataDao.allTestIdsForDataProvider failed", e);
        }
        return rows.toArray(new Object[0][]);
    }

    /**
     * Upserts credentials and registration timestamps for a given {@code test_id}.
     *
     * @param testId       test identifier
     * @param username     credential username
     * @param password     credential password
     * @param registerDate registration date (local)
     * @param registerTime registration time (local; nanos truncated to match SQL precision)
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public void upsertCredentials(final String testId, final String username, final String password,
                                  final java.time.LocalDate registerDate, final java.time.LocalTime registerTime) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.UPSERT_LOGIN_CREDS)) {

            ps.setString(1, testId);
            ps.setString(2, username);
            ps.setString(3, password);
            ps.setDate(4, java.sql.Date.valueOf(registerDate));
            ps.setTime(5, java.sql.Time.valueOf(registerTime.withNano(0))); // ensure JDBC-compatible precision
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("LoginDataDao.upsertCredentials failed", e);
        }
    }

    // ---------
    // Helpers
    // ---------

    /** Null-safe stringification for SQL/Util Date types (kept simple for logging/serialization). */
    private static String toStr(final java.util.Date d) { return d == null ? null : d.toString(); }

    /** Null-safe stringification for SQL Time type. */
    private static String toStr(final java.sql.Time t) { return t == null ? null : t.toString(); }
}
