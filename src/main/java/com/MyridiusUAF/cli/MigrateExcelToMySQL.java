package com.MyridiusUAF.cli;

import com.MyridiusUAF.utils.db.migration.ExcelToDbMigrator;

/**
 * Simple CLI entry point to migrate Excel data into MySQL.
 * <p>
 * Preserves original behavior: invokes {@link ExcelToDbMigrator#migrateAll()}
 * and prints "Migration complete." on success.
 * </p>
 */
public class MigrateExcelToMySQL {

    /**
     * Runs the Excel → MySQL migration.
     *
     * @param args not used (reserved for future options)
     * @throws Exception any exception from the migration is allowed to propagate
     *                   to keep behavior identical to the original code
     */
    public static void main(String[] args) throws Exception {
        // Execute all configured migrations from Excel to DB.
        new ExcelToDbMigrator().migrateAll();

        // Keep this exact message for existing scripts/log parsers.
        System.out.println("Migration complete.");
    }
}
