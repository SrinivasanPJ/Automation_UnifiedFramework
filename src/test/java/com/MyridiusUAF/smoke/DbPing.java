package com.MyridiusUAF.smoke;

import com.MyridiusUAF.utils.db.DataSourceProvider;
import java.sql.Connection;

/**
 * Simple smoke test to verify database connectivity.
 *
 * <p>Behavior is unchanged: attempts to obtain a JDBC {@link Connection}
 * from {@link DataSourceProvider#get()} and prints
 * {@code "Connected -> <jdbc-url>"} on success.</p>
 *
 * <p>Usage:</p>
 * <pre>
 *   java -cp &lt;classpath&gt; com.MyridiusUAF.smoke.DbPing
 * </pre>
 */
public class DbPing {
    public static void main(String[] args) throws Exception {
        // Acquire a connection from the configured DataSource.
        try (Connection c = DataSourceProvider.get().getConnection()) {
            System.out.println("Connected -> " + c.getMetaData().getURL());
        }
    }
}
