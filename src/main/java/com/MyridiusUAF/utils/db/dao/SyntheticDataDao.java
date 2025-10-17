package com.MyridiusUAF.utils.db.dao;

import com.MyridiusUAF.utils.db.Sql;
import com.MyridiusUAF.utils.db.DataSourceProvider;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

/**
 * DAO for synthetic test data.
 *
 * <p>Backed by SQL statements in {@link Sql}, this class provides:
 * <ul>
 *   <li>Lookup of a single synthetic data row by {@code input_id}, returned as an
 *       Excel-header-compatible {@code Map&lt;String, String&gt;}</li>
 *   <li>Retrieval of all input IDs for use with TestNG/JUnit data providers</li>
 * </ul>
 *
 * <p><strong>Notes:</strong> Uses {@link DataSourceProvider#get()} for connections and
 * try-with-resources for safe JDBC resource handling.</p>
 */
public class SyntheticDataDao {

    /** Shared DataSource (obtained from application-level provider). */
    private final DataSource ds = DataSourceProvider.get();

    /**
     * Fetches a single row by {@code input_id} and maps it to Excel-style headers.
     *
     * <p>Returned keys intentionally match existing Excel headers to preserve
     * downstream compatibility:
     * "Input ID", "Application", "Test Type", "Functionality", "Scenario",
     * "Test Case", "Category", "Sub-Category", "Product title", "Country",
     * "State", "Zip", "Billing FirstName", "Billing LastName", "Email",
     * "City", "Address 1", "Phone".</p>
     *
     * @param inputId the input identifier to look up
     * @return a {@link LinkedHashMap} with stable iteration order if found;
     *         otherwise {@link Collections#emptyMap()}
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public Map<String,String> byInputId(String inputId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.SYN_BY_INPUT)) {

            // Bind parameters
            ps.setString(1, inputId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Collections.emptyMap();

                // Preserve insertion order for predictable iteration
                Map<String,String> m = new LinkedHashMap<>();

                // Preserve Excel header keys:
                m.put("Input ID", rs.getString("input_id"));
                m.put("Application", rs.getString("application"));
                m.put("Test Type", rs.getString("test_type"));
                m.put("Functionality", rs.getString("functionality"));
                m.put("Scenario", rs.getString("scenario"));
                m.put("Test Case", rs.getString("test_case"));
                m.put("Category", rs.getString("category"));
                m.put("Sub-Category", rs.getString("sub_category"));
                m.put("Product title", rs.getString("product_title"));
                m.put("Country", rs.getString("country"));
                m.put("State", rs.getString("state"));
                m.put("Zip", rs.getString("zip"));
                m.put("Billing FirstName", rs.getString("billing_first_name"));
                m.put("Billing LastName", rs.getString("billing_last_name"));
                m.put("Email", rs.getString("email"));
                m.put("City", rs.getString("city"));
                m.put("Address 1", rs.getString("address1"));
                m.put("Phone", rs.getString("phone"));

                return m;
            }
        } catch (SQLException e) {
            // Keep behavior: wrap checked SQL exception into a runtime exception
            throw new RuntimeException("SyntheticDataDao.byInputId failed", e);
        }
    }

    /**
     * Returns all {@code input_id} values as a two-dimensional array
     * suitable for TestNG data providers.
     *
     * @return {@code Object[][]} where each row contains a single element: the {@code input_id}
     * @throws RuntimeException wrapping {@link SQLException} on failure
     */
    public Object[][] allInputIdsForDataProvider() {
        List<Object[]> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(Sql.SYN_ALL_INPUTS);
             ResultSet rs = ps.executeQuery()) {

            // Each row: one column (input_id) wrapped as Object[]{ input_id }
            while (rs.next()) rows.add(new Object[]{rs.getString(1)});
        } catch (SQLException e) {
            // Keep behavior: wrap into runtime exception
            throw new RuntimeException("SyntheticDataDao.allInputIdsForDataProvider failed", e);
        }
        return rows.toArray(new Object[0][]);
    }
}
