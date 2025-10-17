package com.MyridiusUAF.utils.db.migration;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.db.DataSourceProvider;
import com.MyridiusUAF.utils.excel.ExcelReaderUtil;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public class ExcelToDbMigrator {
    private final DataSource ds = DataSourceProvider.get();

    public void migrateAll() throws Exception {
        // migrateLogin();
        // migrateSynthetic();
        // migrateTransactional();
        // migrateE2E();
    }

    private void migrateLogin() throws Exception {
        String file  = ConfigReader.getProperty("Test_Data_File_Path");
        String sheet = ConfigReader.getProperty("Login_Data_Sheet_Name");

        // Common_TestData header is on the FIRST row (index 0)
        List<Map<String,String>> rows = ExcelReaderUtil.getAllRows(file, sheet, 0);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO common_testdata (" +
                             " test_id,url,username,password,browser,application,register_date,register_time" +
                             ") VALUES (?,?,?,?,?,?,?,?)")) {

            int ok = 0, skipped = 0, i = 0;
            for (Map<String,String> r : rows) {
                i++;

                // >>> IMPORTANT: normalize keys once per row
                Map<String,String> n = normalizeRow(r);

                String testId = ci(n, "TestID","Test ID","test_id","Test Id");
                if (isBlank(testId)) {
                    skipped++;
                    System.err.println("[MIGRATE][Login] Skip row " + i
                            + " — missing TestID; headers seen: " + r.keySet());
                    continue;
                }

                ps.setString(1,  testId.trim());
                ps.setString(2,  ci(n, "URL","Url"));
                ps.setString(3,  ci(n, "Username","User Name","user_name"));
                ps.setString(4,  ci(n, "Password","Pass"));
                ps.setString(5,  ci(n, "Browser"));
                ps.setString(6,  ci(n, "Application","App"));
                ps.setDate(7,    safeDate(ci(n, "Register Date","Register_Date","register_date")));
                ps.setTime(8,    safeTime(ci(n, "Register Time","Register_Time","register_time")));

                ps.addBatch();
                ok++;
                if (ok % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            System.out.println("[MIGRATE][Login] inserted=" + ok + ", skipped=" + skipped);
        }
    }

    // Build a normalized view once per row
    private static Map<String,String> normalizeRow(Map<String,String> row) {
        Map<String,String> norm = new HashMap<>();
        if (row == null) return norm;
        for (Map.Entry<String,String> e : row.entrySet()) {
            norm.put(normalizeKey(e.getKey()), e.getValue());
        }
        return norm;
    }

    // lookup in a pre-normalized map
    private static String ci(Map<String,String> norm, String... names) {
        for (String n : names) {
            String v = norm.get(normalizeKey(n));
            if (!isBlank(v)) return v;
        }
        return null;
    }

    private static String normalizeKey(String k) {
        if (k == null) return "";
        return k.trim().toLowerCase().replaceAll("[\\s_]+", " ");
    }

    private void migrateSynthetic() throws Exception {
        String file  = ConfigReader.getProperty("Test_Data_File_Path");
        String sheet = ConfigReader.getProperty("Synthetic_Data_Sheet_Name");

        // Header row is the SECOND row in your workbook
        List<Map<String,String>> rows = ExcelReaderUtil.getAllRows(file, sheet, 1);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "REPLACE INTO synthetic_data (" +
                             " input_id,application,test_type,functionality,scenario,test_case," +
                             " category,sub_category,product_title,country,state,zip," +
                             " billing_first_name,billing_last_name,email,city,address1,phone" +
                             ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
             )) {

            int ok = 0, skipped = 0, i = 0;
            for (Map<String,String> r : rows) {
                i++;

                // tolerate "Input ID" / "InputID"
                String inputId = firstNonBlank(r.get("Input ID"), r.get("InputID"));
                if (isBlank(inputId)) {
                    skipped++;
                    System.err.println("[MIGRATE][Synthetic] Skip row " + i + " — missing Input ID");
                    continue;
                }

                ps.setString(1,  inputId.trim());
                ps.setString(2,  r.get("Application"));
                ps.setString(3,  firstNonBlank(r.get("Test Type"), r.get("TestType")));
                ps.setString(4,  r.get("Functionality"));
                ps.setString(5,  r.get("Scenario"));
                ps.setString(6,  firstNonBlank(r.get("Test Case"), r.get("TestCase")));
                ps.setString(7,  r.get("Category"));
                ps.setString(8,  firstNonBlank(r.get("Sub-Category"), r.get("SubCategory")));
                ps.setString(9,  firstNonBlank(r.get("Product title"), r.get("Product Title"), r.get("Product")));
                ps.setString(10, r.get("Country"));
                ps.setString(11, r.get("State"));
                ps.setString(12, firstNonBlank(r.get("Zip"), r.get("ZIP"), r.get("Postcode")));
                ps.setString(13, firstNonBlank(r.get("Billing FirstName"), r.get("Billing First Name")));
                ps.setString(14, firstNonBlank(r.get("Billing LastName"), r.get("Billing Last Name")));
                ps.setString(15, firstNonBlank(r.get("Email"), r.get("E-mail")));
                ps.setString(16, r.get("City"));
                ps.setString(17, firstNonBlank(r.get("Address 1"), r.get("Address1"), r.get("Address")));
                ps.setString(18, firstNonBlank(r.get("Phone"), r.get("Phone Number")));

                ps.addBatch(); ok++;
                if (ok % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            System.out.println("[MIGRATE][Synthetic] inserted=" + ok + ", skipped=" + skipped);
        }
    }

    /* =========================
       TRANSACTIONAL_DATA
       ========================= */
    private void migrateTransactional() throws Exception {
        String file  = ConfigReader.getProperty("Test_Data_File_Path");
        String sheet = ConfigReader.getProperty("Transactional_Data_Sheet_Name");

        // Header row is the SECOND row in that sheet (row 1, index 1)
        List<Map<String,String>> rows = ExcelReaderUtil.getAllRows(file, sheet, 1);

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     // NOTE: column names here must match your DB schema
                     "INSERT INTO transactional_data (" +
                             " application, test_type, functionality, scenario, test_case," +
                             " run_id, execution_date, execution_time, execution_status," +
                             " order_id, order_date, failure_reason" +
                             ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE " +
                             " application=VALUES(application)," +
                             " test_type=VALUES(test_type)," +
                             " functionality=VALUES(functionality)," +
                             " scenario=VALUES(scenario)," +
                             " test_case=VALUES(test_case)," +
                             " execution_date=VALUES(execution_date)," +
                             " execution_time=VALUES(execution_time)," +
                             " execution_status=VALUES(execution_status)," +
                             " order_id=VALUES(order_id)," +
                             " order_date=VALUES(order_date)," +
                             " failure_reason=VALUES(failure_reason)"
             )) {

            int ok=0, skipped=0, i=0;
            for (Map<String,String> r0 : rows) {
                i++;
                Map<String,String> r = normalizeRow(r0);

                String runId = ci(r,"Run ID","RunID","run_id");
                if (isBlank(runId)) { skipped++; continue; }

                ps.setString(1,  ci(r,"Application"));
                ps.setString(2,  ci(r,"Test Type","TestType"));
                ps.setString(3,  ci(r,"Functionality"));
                ps.setString(4,  ci(r,"Scenario"));
                ps.setString(5,  ci(r,"Test Case","TestCase"));
                ps.setString(6,  runId.trim());
                ps.setDate(7,    safeDate(ci(r,"Execution Date","Exec Date","execution_date")));
                ps.setTime(8,    safeTime(ci(r,"Execution Time","Exec Time","execution_time")));
                ps.setString(9,  ci(r,"Execution Status","Exec Status","execution_status"));
                ps.setString(10, ci(r,"Order ID","OrderID","order_id"));
                ps.setDate(11,   safeDate(ci(r,"Order Date","OrderDate","order_date")));
                ps.setString(12, ci(r,"Failure Reason","failure_reason"));

                ps.addBatch();
                if (++ok % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            System.out.println("[MIGRATE][Transactional] inserted/updated="+ok+", skipped="+skipped);
        }
    }

    /*=========================
 E2E BINDING
=========================*/
    /*=========================
   E2E BINDING (auto header detect)
 =========================*/
    private void migrateE2E() throws Exception {
        String file  = ConfigReader.getProperty("Test_Data_File_Path");
        String sheet = ConfigReader.getProperty("End_To_End_Sheet_Name"); // "E2E Binding"

        // Try likely header rows in this order
        int[] headerCandidates = {2, 1, 0};
        List<Map<String,String>> rows = null;

        for (int idx : headerCandidates) {
            rows = ExcelReaderUtil.getAllRows(file, sheet, idx);
            if (!rows.isEmpty()) {
                // Do we see at least one expected header?
                Set<String> keys = normalizeRow(rows.get(0)).keySet();
                if (keys.stream().anyMatch(k ->
                        k.contains("application") ||
                                k.contains("run id") ||
                                k.contains("order id") ||
                                k.contains("execution date"))) {
                    // Looks like we picked the right header row
                    break;
                }
            }
            rows = null; // force retry with next candidate
        }

        if (rows == null) {
            System.err.println("[MIGRATE][E2E] Could not detect header row; aborting.");
            return;
        }

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO e2e_binding (" +
                             "  run_id, application, test_type, functionality, scenario, binding_test_case_name," +
                             "  execution_date, execution_time, execution_status, order_id, order_date, failure_reason" +
                             ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) " +
                             "ON DUPLICATE KEY UPDATE " +
                             "  application=VALUES(application)," +
                             "  test_type=VALUES(test_type)," +
                             "  functionality=VALUES(functionality)," +
                             "  scenario=VALUES(scenario)," +
                             "  binding_test_case_name=VALUES(binding_test_case_name)," +
                             "  execution_date=VALUES(execution_date)," +
                             "  execution_time=VALUES(execution_time)," +
                             "  execution_status=VALUES(execution_status)," +
                             "  order_id=VALUES(order_id)," +
                             "  order_date=VALUES(order_date)," +
                             "  failure_reason=VALUES(failure_reason)"
             )) {

            int ok = 0, skipped = 0, i = 0;

            for (Map<String,String> r0 : rows) {
                i++;
                Map<String,String> r = normalizeRow(r0);

                String runId   = ci(r, "Run ID", "RunID", "run_id");
                String orderId = ci(r, "Order ID", "OrderID", "order_id");

                if (isBlank(runId) && isBlank(orderId)) {
                    skipped++;
                    // Print the *keys* to help if it happens again
                    System.err.println("[MIGRATE][E2E] Skip row " + i +
                            " — missing both Run ID and Order ID; keys: " + r.keySet());
                    continue;
                }

                ps.setString(1,  blankToNull(runId));
                ps.setString(2,  ci(r, "Application"));
                ps.setString(3,  ci(r, "Test Type", "TestType"));
                ps.setString(4,  ci(r, "Functionality"));
                ps.setString(5,  ci(r, "Scenario"));
                ps.setString(6,  ci(r, "Binding Test Case Name", "Binding TestCase Name", "Binding TestCase"));

                ps.setDate (7,  safeDate(ci(r, "Execution Date", "Exec Date", "execution date", "execution_date")));
                ps.setTime (8,  safeTime(ci(r, "Execution Time", "Exec Time", "execution time", "execution_time")));

                ps.setString(9,  ci(r, "Execution Status", "Exec Status", "Status", "execution_status"));
                ps.setString(10, blankToNull(orderId));
                ps.setDate (11, safeDate(ci(r, "Order Date", "OrderDate", "Order_Date", "order date")));
                ps.setString(12, ci(r, "Failure Reason", "FailureReason", "failure_reason"));

                ps.addBatch();
                if (++ok % 500 == 0) ps.executeBatch();
            }

            ps.executeBatch();
            System.out.println("[MIGRATE][E2E] inserted/updated=" + ok + ", skipped=" + skipped);
        }
    }

    private static String blankToNull(String s){ return isBlank(s) ? null : s.trim(); }

    // private static String nullIfBlank(String s){ return isBlank(s) ? null : s.trim(); }

    /* ---- tiny helpers in the same class ---- */
    private static boolean isBlank(String s) { return s == null || s.trim().isEmpty(); }
    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }

    private static java.sql.Date safeDate(String s) {
        if (isBlank(s)) return null;
        String t = s.trim();

        // 1) ISO first
        try { return java.sql.Date.valueOf(LocalDate.parse(t)); } catch (Exception ignore) {}

        // 2) Common texty Java Date
        try {
            var f = new java.text.SimpleDateFormat(
                    "EEE MMM dd HH:mm:ss zzz yyyy", java.util.Locale.ENGLISH);
            return new java.sql.Date(f.parse(t).getTime());
        } catch (Exception ignore) {}

        // 3) Try a bunch of day/month & month/day patterns
        String[] patterns = {
                "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy",
                "MM/dd/yyyy", "M/d/yyyy", "MM-dd-yyyy", "M-d-yyyy"
        };
        for (String p : patterns) {
            try {
                var fmt = java.time.format.DateTimeFormatter.ofPattern(p);
                return java.sql.Date.valueOf(LocalDate.parse(t, fmt));
            } catch (Exception ignore) {}
        }

        // 4) Excel serial number (if your Excel reader ever passes it through)
        try {
            double serial = Double.parseDouble(t);
            // Excel epoch: 1899-12-30; cast to days
            LocalDate base = LocalDate.of(1899, 12, 30);
            return java.sql.Date.valueOf(base.plusDays((long) serial));
        } catch (Exception ignore) {}

        return null;
    }


    private static java.sql.Time safeTime(String s) {
        if (isBlank(s)) return null;
        String t = s.trim();
        try { return java.sql.Time.valueOf(t); } catch (Exception ignore) {}
        try {
            var lt = java.time.LocalTime.parse(t, java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            return java.sql.Time.valueOf(lt);
        } catch (Exception ignore) {}
        return null;
    }
}
