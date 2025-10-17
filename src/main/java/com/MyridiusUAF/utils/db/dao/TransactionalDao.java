package com.MyridiusUAF.utils.db.dao;

import com.MyridiusUAF.utils.db.DataSourceProvider;
import com.MyridiusUAF.utils.db.Sql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * DAO for transactional execution data.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Generate next run id in {@code R#} numeric sequence</li>
 *   <li>Insert/Upsert execution rows</li>
 *   <li>Update execution status and/or order linkage for a run</li>
 *   <li>Optional housekeeping: keep {@code AUTO_INCREMENT} in sync with {@code MAX(id)+1}</li>
 * </ul>
 *
 * <p><strong>Notes:</strong> Uses {@link DataSourceProvider} for connections and SQL from {@link Sql}.
 * All JDBC resources are handled via try-with-resources. Logging kept as-is.</p>
 */
public class TransactionalDao {

    // ---------------------------------------------------------------------
    // Constants
    // ---------------------------------------------------------------------

    /** Logger for DAO operations (unchanged category). */
    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionalDao.class);

    /** Formatter used for yyyyMMdd if/when needed (kept for compatibility). */
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    /** Local SQL: most recent non-null order_id from transactional_data (by id DESC). */
    private static final String FIND_LATEST_ORDER_ID_SQL = """
        SELECT order_id
          FROM myridius_uaf.transactional_data
         WHERE order_id IS NOT NULL
         ORDER BY id DESC
         LIMIT 1
        """;

    // ---------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------

    /** Shared DataSource from the application-level provider (existing behavior). */
    private final DataSource ds = DataSourceProvider.get();

    // ---------------------------------------------------------------------
    // Housekeeping / AI guard (kept optional; does not throw on failure)
    // ---------------------------------------------------------------------

    /**
     * Ensures the table's {@code AUTO_INCREMENT} equals {@code MAX(id)+1}.
     *
     * <p>This is a non-fatal safeguard to maintain Excel-like continuity (e.g., 247 → 248).
     * Any SQL error is logged as a warning and does not fail the caller.</p>
     */
    public void ensureAutoIncrementSequential() {
        long maxId = 0L;
        Long currentAi = null;

        try (Connection c = ds.getConnection()) {
            // 1) Fetch MAX(id) from transactional_data
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(MAX(id),0) FROM transactional_data");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) maxId = rs.getLong(1);
            }

            // 2) Fetch current AUTO_INCREMENT value for the table
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT AUTO_INCREMENT " +
                            "FROM information_schema.tables " +
                            "WHERE table_schema = DATABASE() AND table_name = 'transactional_data'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long ai = rs.getLong(1);           // 0 if NULL
                    currentAi = rs.wasNull() ? null : ai;
                }
            }

            long shouldBe = maxId + 1;
            long aiNow = (currentAi == null ? 1L : currentAi);

            if (aiNow != shouldBe) {
                try (Statement st = c.createStatement()) {
                    st.execute("ALTER TABLE transactional_data AUTO_INCREMENT = " + shouldBe);
                }
                LOGGER.info("DAO[AUTO_INCREMENT]: adjusted from {} to {} (MAX(id)={})", aiNow, shouldBe, maxId);
            } else {
                LOGGER.info("DAO[AUTO_INCREMENT]: already sequential (AUTO_INCREMENT={}, MAX(id)={})", aiNow, maxId);
            }
        } catch (SQLException e) {
            // Do not fail the test startup because of this housekeeping
            LOGGER.warn("DAO[AUTO_INCREMENT]: check/adjust skipped due to error: {}", e.toString());
        }
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Produces the next run id in the {@code R#} series (e.g., R247 → R248).
     *
     * @param isE2E flag used only for logging context; behavior remains the same
     * @return next run id string (e.g., {@code "R1"} if none exist)
     * @throws SQLException if the underlying lookup fails
     */
    public String nextRunId(boolean isE2E) throws SQLException {
        if (isE2E) {
            LOGGER.info("DAO[nextRunId]: E2E requested → returning R-series style for SYSTEM runs.");
        }
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.LAST_RUN_ID_NUMERIC);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String last = rs.getString(1); // e.g., "R247"
                int n = Integer.parseInt(last.substring(1));
                String next = "R" + (n + 1);
                LOGGER.info("DAO[nextRunId]: last={} → next={}", last, next);
                return next;
            }
        }
        LOGGER.info("DAO[nextRunId]: no previous R* rows → starting at R1");
        return "R1";
    }

    /**
     * Upserts an execution row with current date/time.
     *
     * @param application   application name
     * @param testType      test type/category
     * @param functionality functional area
     * @param scenario      scenario identifier
     * @param testCase      test case name/identifier
     * @param runId         run identifier (e.g., R248)
     * @param status        execution status
     * @param failureReason failure reason (nullable)
     * @throws SQLException if the upsert fails
     */
    public void upsertExecutionRow(String application, String testType, String functionality,
                                   String scenario, String testCase, String runId,
                                   String status, String failureReason) throws SQLException {
        // Capture execution timestamp using system clock; truncate nanos for JDBC compatibility
        Date execDate = Date.valueOf(LocalDate.now());
        Time execTime = Time.valueOf(LocalTime.now().withNano(0));

        LOGGER.info("DAO[UPSERT_TXN]: runId={}, app={}, type={}, func={}, scen={}, tc={}, status={}",
                runId, application, testType, functionality, scenario, testCase, status);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.UPSERT_TXN)) {
            // Parameter order aligns to Sql.UPSERT_TXN
            ps.setString(1,  application);
            ps.setString(2,  testType);
            ps.setString(3,  functionality);
            ps.setString(4,  scenario);
            ps.setString(5,  testCase);
            ps.setString(6,  runId);
            ps.setDate(7,    execDate);
            ps.setTime(8,    execTime);
            ps.setString(9,  status);
            ps.setString(10, failureReason);

            int n = ps.executeUpdate();
            LOGGER.info("DAO[UPSERT_TXN]: affectedRows={} for run_id={}", n, runId);
        }
    }

    /**
     * Inserts an execution row. Delegates to {@link #upsertExecutionRow(String, String, String, String, String, String, String, String)}
     * to preserve behavior.
     *
     * @throws SQLException if the operation fails
     */
    public void insertExecutionRow(String application, String testType, String functionality,
                                   String scenario, String testCase, String runId,
                                   String status, String failureReason) throws SQLException {
        LOGGER.debug("DAO[insertExecutionRow]: delegating to upsertExecutionRow(runId={})", runId);
        upsertExecutionRow(application, testType, functionality, scenario, testCase, runId, status, failureReason);
    }

    /**
     * Updates the execution status and failure reason for a given run id.
     *
     * @param runId         run identifier
     * @param status        new status
     * @param failureReason new failure reason (nullable)
     * @throws SQLException if the update fails
     */
    public void updateStatusForRun(String runId, String status, String failureReason) throws SQLException {
        LOGGER.info("DAO[updateStatusForRun]: runId={}, status={}, failureReason={}", runId, status, failureReason);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.UPDATE_TXN_STATUS)) {
            ps.setString(1, status);
            ps.setString(2, failureReason);
            ps.setString(3, runId);
            ps.executeUpdate();
        }
    }

    /**
     * Sets order id/date for a given run id.
     *
     * @param runId     run identifier
     * @param orderId   external/business order id
     * @param orderDate order date (nullable)
     * @throws SQLException if the update fails
     */
    public void updateOrderForRun(String runId, String orderId, java.time.LocalDate orderDate) throws SQLException {
        LOGGER.info("DAO[updateOrderForRun]: runId={}, orderId={}, orderDate={}", runId, orderId, orderDate);
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.UPDATE_TXN_ORDER)) {
            ps.setString(1, orderId);
            ps.setDate(2, (orderDate == null ? null : Date.valueOf(orderDate)));
            ps.setString(3, runId);
            ps.executeUpdate();
        }
    }

    /**
     * Returns the most recent non-null {@code order_id} from {@code transactional_data}.
     *
     * @return latest {@code order_id} or {@code null} if none exist
     * @throws SQLException if the query fails
     */
    public String findLatestOrderId() throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(FIND_LATEST_ORDER_ID_SQL);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
