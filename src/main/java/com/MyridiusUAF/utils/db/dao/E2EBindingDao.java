package com.MyridiusUAF.utils.db.dao;

import com.MyridiusUAF.utils.db.DataSourceProvider;
import com.MyridiusUAF.utils.db.Sql;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for {@code e2e_binding} table operations.
 *
 * <p>Responsibilities include:
 * <ul>
 *   <li>Existence checks for orders bound to execution records</li>
 *   <li>Upsert of full binding rows (including order data)</li>
 *   <li>Status updates for a given run + binding pair</li>
 *   <li>Upsert of minimal execution rows</li>
 *   <li>Fetching the latest non-null {@code order_id}</li>
 * </ul>
 * </p>
 *
 * <p><b>Notes:</b> This class uses basic JDBC with try-with-resources to ensure all
 * connections and statements are closed promptly. It keeps behavior identical to the
 * original implementation while adding documentation and minor structural tidy-up.</p>
 */
public class E2EBindingDao {

    // -------------------------
    // SQL Fragments (Local)
    // -------------------------

    /** Upsert for a minimal execution row; keeps columns and order explicit. */
    private static final String UPSERT_EXECUTION_SQL = """
        INSERT INTO myridius_uaf.e2e_binding
            (application, test_type, functionality, scenario, binding_test_case_name,
             run_id, execution_status, failure_reason)
        VALUES (?,?,?,?,?,?,?,?)
        ON DUPLICATE KEY UPDATE
            application=VALUES(application),
            test_type=VALUES(test_type),
            functionality=VALUES(functionality),
            scenario=VALUES(scenario),
            binding_test_case_name=VALUES(binding_test_case_name),
            execution_status=VALUES(execution_status),
            failure_reason=VALUES(failure_reason)
        """;

    /** Query to obtain the most recent non-null order id by descending primary key id. */
    private static final String FIND_LATEST_ORDER_ID_SQL = """
        SELECT order_id
          FROM myridius_uaf.e2e_binding
         WHERE order_id IS NOT NULL
         ORDER BY id DESC
         LIMIT 1
        """;

    /** Update statement for setting execution status and failure reason for a given run/binding. */
    private static final String UPDATE_STATUS_FOR_RUN_SQL = """
        UPDATE e2e_binding
           SET execution_status = ?, failure_reason = ?
         WHERE run_id = ? AND binding_test_case_name = ?
        """;

    // -------------------------
    // State
    // -------------------------

    /** DataSource used to obtain JDBC connections. */
    private final DataSource ds;

    // -------------------------
    // Construction
    // -------------------------

    /**
     * Creates an instance using the shared {@link DataSourceProvider}.
     * Suitable for production use.
     */
    public E2EBindingDao() {
        this(DataSourceProvider.get());
    }

    /**
     * Creates an instance using the provided {@link DataSource}.
     * Useful for tests or alternate wiring.
     *
     * @param dataSource non-null JDBC data source
     */
    public E2EBindingDao(final DataSource dataSource) {
        this.ds = dataSource;
    }

    // -------------------------
    // Public API
    // -------------------------

    /**
     * Checks whether an order with the given id and date exists.
     *
     * @param orderId   business order identifier
     * @param orderDate business order date (SQL date)
     * @return {@code true} if at least one matching row exists; {@code false} otherwise
     * @throws SQLException if the query fails
     */
    public boolean existsOrder(final String orderId, final java.sql.Date orderDate) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.EXISTS_E2E_ORDER)) {

            ps.setString(1, orderId);
            ps.setDate(2, orderDate);

            // rs.next() indicates at least one row matched.
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Inserts or updates a full binding row (includes order/date/time fields).
     *
     * @param application          application name
     * @param testType             test type/category
     * @param functionality        functional area
     * @param scenario             scenario identifier
     * @param bindingTestCaseName  binding test case name
     * @param runId                run identifier
     * @param execDate             execution date
     * @param execTime             execution time
     * @param status               execution status
     * @param orderId              associated order id
     * @param orderDate            associated order date
     * @param failureReason        failure reason (nullable)
     * @throws SQLException if the upsert fails
     */
    public void upsertBinding(final String application, final String testType, final String functionality, final String scenario,
                              final String bindingTestCaseName, final String runId,
                              final java.sql.Date execDate, final java.sql.Time execTime, final String status,
                              final String orderId, final java.sql.Date orderDate, final String failureReason) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.UPSERT_E2E)) {

            // Parameter positions align to Sql.UPSERT_E2E definition.
            ps.setString(1,  application);
            ps.setString(2,  testType);
            ps.setString(3,  functionality);
            ps.setString(4,  scenario);
            ps.setString(5,  bindingTestCaseName);
            ps.setString(6,  runId);
            ps.setDate(7,    execDate);
            ps.setTime(8,    execTime);
            ps.setString(9,  status);
            ps.setString(10, orderId);
            ps.setDate(11,   orderDate);
            ps.setString(12, failureReason);

            ps.executeUpdate();
        }
    }

    /**
     * Updates the execution status and failure reason for a specific run + binding row.
     *
     * @param runId               run identifier
     * @param bindingTestCaseName binding test case name
     * @param status              new execution status
     * @param failureReason       new failure reason (nullable)
     * @return number of rows affected (0 if no matching row)
     * @throws SQLException if the update fails
     */
    public int updateStatusForRun(final String runId,
                                  final String bindingTestCaseName,
                                  final String status,
                                  final String failureReason) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(UPDATE_STATUS_FOR_RUN_SQL)) {

            ps.setString(1, status);
            ps.setString(2, failureReason);
            ps.setString(3, runId);
            ps.setString(4, bindingTestCaseName);

            return ps.executeUpdate();
        }
    }

    /**
     * Upserts a minimal execution row (no order/date/time fields).
     *
     * @param application         application name
     * @param testType            test type/category
     * @param functionality       functional area
     * @param scenario            scenario identifier
     * @param bindingTestCaseName binding test case name
     * @param runId               run identifier
     * @param executionStatus     execution status
     * @param failureReason       failure reason (nullable)
     * @return number of rows affected
     * @throws SQLException if the upsert fails
     */
    public int upsertExecutionRow(final String application, final String testType, final String functionality,
                                  final String scenario, final String bindingTestCaseName, final String runId,
                                  final String executionStatus, final String failureReason) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(UPSERT_EXECUTION_SQL)) {

            // Use an index to clearly convey positional mapping.
            int i = 0;
            ps.setString(++i, application);
            ps.setString(++i, testType);
            ps.setString(++i, functionality);
            ps.setString(++i, scenario);
            ps.setString(++i, bindingTestCaseName);
            ps.setString(++i, runId);
            ps.setString(++i, executionStatus);
            ps.setString(++i, failureReason);

            return ps.executeUpdate();
        }
    }

    /**
     * Returns the most recent non-null {@code order_id} (by {@code id DESC}).
     *
     * @return latest order id string, or {@code null} if none exist
     * @throws SQLException if the query fails
     */
    public String findLatestOrderId() throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(FIND_LATEST_ORDER_ID_SQL);
             ResultSet rs = ps.executeQuery()) {

            // If at least one row exists, return the first column (order_id); otherwise null.
            return rs.next() ? rs.getString(1) : null;
        }
    }

    // E2EBindingDao.java
    public String nextE2ERunId() throws SQLException {
        try (var c = ds.getConnection();
             var ps = c.prepareStatement(Sql.LAST_E2E_RUN_ID_NUMERIC);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                String last = rs.getString(1);          // e.g. "E2E28"
                int n = Integer.parseInt(last.substring(3));
                return "E2E" + (n + 1);                 // -> "E2E29"
            }
        }
        return "E2E1";
    }
}
