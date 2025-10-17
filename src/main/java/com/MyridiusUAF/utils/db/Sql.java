package com.MyridiusUAF.utils.db;

/**
 * Central repository for SQL statements used across DAOs.
 *
 * <p><strong>Important:</strong> This class contains only constants and no logic.
 * Queries are grouped by domain (Login, Synthetic, Transactional, E2E Binding).
 * Do not change the text of existing queries unless the corresponding schema
 * and DAO parameter bindings are updated together.</p>
 */
public final class Sql {

    /** Utility class; prevent instantiation. */
    private Sql() {}

    // ---------------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------------

    /** Selects a single login row by {@code test_id}. */
    public static final String LOGIN_BY_ID =
            "SELECT test_id, url, username, password, browser, application, register_date, register_time " +
                    "FROM common_testdata WHERE test_id=?";

    /** Lists all {@code test_id} values (ordered). */
    public static final String LOGIN_ALL_IDS =
            "SELECT test_id FROM common_testdata ORDER BY test_id";

    /**
     * Idempotent upsert of login credentials + timestamps keyed by {@code test_id}.
     * <p>On duplicate, updates: {@code username}, {@code password}, {@code register_date}, {@code register_time}.</p>
     */
    public static final String UPSERT_LOGIN_CREDS =
            "INSERT INTO common_testdata (test_id, username, password, register_date, register_time) " +
                    "VALUES (?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    " username=VALUES(username)," +
                    " password=VALUES(password)," +
                    " register_date=VALUES(register_date)," +
                    " register_time=VALUES(register_time)";

    /**
     * Returns the last R-series {@code run_id} (e.g., {@code R247}) by ordering on the numeric tail.
     * <p>Used to compute the next {@code run_id} as {@code R(n+1)}.</p>
     */
    public static final String LAST_RUN_ID_NUMERIC =
            "SELECT run_id " +
                    "FROM transactional_data " +
                    "WHERE run_id REGEXP '^R[0-9]+' " +
                    "ORDER BY CAST(SUBSTRING(run_id, 2) AS UNSIGNED) DESC " +
                    "LIMIT 1";

    public static final String LAST_E2E_RUN_ID_NUMERIC =
            "SELECT run_id " +
                    "FROM e2e_binding " +
                    "WHERE run_id REGEXP '^E2E[0-9]+' " +
                    "ORDER BY CAST(SUBSTRING(run_id,4) AS UNSIGNED) DESC " +
                    "LIMIT 1";

    /** Updates only {@code execution_status} and {@code failure_reason} for a given {@code run_id}. */
    public static final String UPDATE_TXN_STATUS =
            "UPDATE transactional_data " +
                    "SET execution_status=?, failure_reason=? " +
                    "WHERE run_id=?";

    // ---------------------------------------------------------------------
    // Synthetic
    // ---------------------------------------------------------------------

    /** Selects a single synthetic data row by {@code input_id}. */
    public static final String SYN_BY_INPUT =
            "SELECT input_id, application, test_type, functionality, scenario, test_case, " +
                    "category, sub_category, product_title, country, state, zip, billing_first_name, " +
                    "billing_last_name, email, city, address1, phone " +
                    "FROM synthetic_data WHERE input_id=?";

    /** Lists all {@code input_id} values (ordered). */
    public static final String SYN_ALL_INPUTS =
            "SELECT input_id FROM synthetic_data ORDER BY input_id";

    // ---------------------------------------------------------------------
    // Transactional (unique: run_id)
    // ---------------------------------------------------------------------

    /**
     * Fetches the latest row matching a given {@code run_id} prefix.
     * <p>Used when generating series other than the default R-series; order by {@code id DESC}.</p>
     */
    public static final String LAST_RUN_ID_PREFIX =
            "SELECT run_id FROM transactional_data WHERE run_id LIKE ? ORDER BY id DESC LIMIT 1";

    /**
     * Idempotent upsert by {@code run_id}.
     * <p>{@code order_id} and {@code order_date} are set separately via {@link #UPDATE_TXN_ORDER}.</p>
     */
    public static final String UPSERT_TXN =
            "INSERT INTO transactional_data (" +
                    " application, test_type, functionality, scenario, test_case," +
                    " run_id, execution_date, execution_time, execution_status, failure_reason" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    " application=VALUES(application)," +
                    " test_type=VALUES(test_type)," +
                    " functionality=VALUES(functionality)," +
                    " scenario=VALUES(scenario)," +
                    " test_case=VALUES(test_case)," +
                    " execution_date=VALUES(execution_date)," +
                    " execution_time=VALUES(execution_time)," +
                    " execution_status=VALUES(execution_status)," +
                    " failure_reason=VALUES(failure_reason)";

    /** Sets {@code order_id} and {@code order_date} for a given {@code run_id}. */
    public static final String UPDATE_TXN_ORDER =
            "UPDATE transactional_data SET order_id=?, order_date=? WHERE run_id=?";

    /**
     * Retrieves the most recent transactional row (by {@code id DESC}) where both
     * {@code order_id} and {@code order_date} are not null.
     */
    public static final String LATEST_TXN_WITH_ORDER =
            "SELECT application, test_type, functionality, scenario, test_case, run_id, execution_date, execution_time," +
                    " execution_status, order_id, order_date FROM transactional_data " +
                    "WHERE order_id IS NOT NULL AND order_date IS NOT NULL ORDER BY id DESC LIMIT 1";

    // ---------------------------------------------------------------------
    // E2E binding (unique: run_id, and unique: order_id)
    // ---------------------------------------------------------------------

    /** Fast existence check for an E2E binding row by {@code order_id} and {@code order_date}. */
    public static final String EXISTS_E2E_ORDER =
            "SELECT 1 FROM e2e_binding WHERE order_id=? AND order_date=? LIMIT 1";

    /**
     * Idempotent upsert into {@code e2e_binding}, keyed by either {@code run_id} or {@code order_id}.
     * <p>On duplicate, updates all mutable fields to the incoming values.</p>
     */
    public static final String UPSERT_E2E =
            "INSERT INTO e2e_binding (" +
                    " application, test_type, functionality, scenario, binding_test_case_name," +
                    " run_id, execution_date, execution_time, execution_status, order_id, order_date, failure_reason" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    " application=VALUES(application)," +
                    " test_type=VALUES(test_type)," +
                    " functionality=VALUES(functionality)," +
                    " scenario=VALUES(scenario)," +
                    " binding_test_case_name=VALUES(binding_test_case_name)," +
                    " run_id=VALUES(run_id)," +
                    " execution_date=VALUES(execution_date)," +
                    " execution_time=VALUES(execution_time)," +
                    " execution_status=VALUES(execution_status)," +
                    " order_id=VALUES(order_id)," +
                    " order_date=VALUES(order_date)," +
                    " failure_reason=VALUES(failure_reason)";

    /** Latest non-null order_id from transactional_data (by id DESC). */
    public static final String FIND_LATEST_ORDER_ID_TXN =
            "SELECT order_id FROM transactional_data " +
                    "WHERE order_id IS NOT NULL ORDER BY id DESC LIMIT 1";

    /** Latest non-null order_id from e2e_binding (by id DESC). */
    public static final String FIND_LATEST_ORDER_ID_E2E =
            "SELECT order_id FROM e2e_binding " +
                    "WHERE order_id IS NOT NULL ORDER BY id DESC LIMIT 1";

    /** Update execution_status/failure_reason for an e2e row identified by (run_id, binding_test_case_name). */
    public static final String UPDATE_E2E_STATUS_FOR_RUN =
            "UPDATE e2e_binding SET execution_status=?, failure_reason=? " +
                    "WHERE run_id=? AND binding_test_case_name=?";

    /** Minimal upsert for e2e_binding (no order columns). */
    public static final String UPSERT_E2E_EXECUTION =
            "INSERT INTO e2e_binding " +
                    " (application, test_type, functionality, scenario, binding_test_case_name, " +
                    "  run_id, execution_status, failure_reason) " +
                    "VALUES (?,?,?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    " application=VALUES(application), " +
                    " test_type=VALUES(test_type), " +
                    " functionality=VALUES(functionality), " +
                    " scenario=VALUES(scenario), " +
                    " binding_test_case_name=VALUES(binding_test_case_name), " +
                    " execution_status=VALUES(execution_status), " +
                    " failure_reason=VALUES(failure_reason)";

}
