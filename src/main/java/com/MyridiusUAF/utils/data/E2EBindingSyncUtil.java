package com.MyridiusUAF.utils.data;

import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.E2EBindingColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelColumnIndex;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import com.MyridiusUAF.utils.reporting.LogUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import com.MyridiusUAF.utils.db.DataMode;
import com.MyridiusUAF.utils.db.dao.TransactionalDao;
import com.MyridiusUAF.utils.db.dao.E2EBindingDao;
import java.sql.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * Syncs the latest Transactional row that contains BOTH Order ID and Order Date
 * into the E2E Binding sheet by appending a new row (no overwrite).
 *
 * <p><b>Duplicate guard:</b> Skips appending when a row with the same pair
 * (OrderID, OrderDate) already exists anywhere in E2E Binding.</p>
 *
 * <p><b>Note on RunID:</b> This utility mirrors the Transactional sheet’s RunID
 * (R-series) into E2E Binding. It does <i>not</i> generate an E2E-series id.</p>
 */
public final class E2EBindingSyncUtil {

    private static final int TXN_START_ROW = 2; // Transactional data starts at row index 2 (0-based)
    private static final int E2E_START_ROW = 1; // E2E Binding data starts at row index 1 (0-based)

    private E2EBindingSyncUtil() { /* utility */ }

    public static synchronized void appendLatestTransactionalOrderToE2EBinding() {
        if (DataMode.isDb()) {
            try (Connection c = com.MyridiusUAF.utils.db.DataSourceProvider.get().getConnection();
                 PreparedStatement ps = c.prepareStatement(com.MyridiusUAF.utils.db.Sql.LATEST_TXN_WITH_ORDER);
                 ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) return;

                String application    = rs.getString("application");
                String testType       = rs.getString("test_type");
                String functionality  = rs.getString("functionality");
                String scenario       = rs.getString("scenario");
                String bindingTC      = rs.getString("test_case");
                String runId          = rs.getString("run_id");
                java.sql.Date execDate = rs.getDate("execution_date");
                java.sql.Time execTime = rs.getTime("execution_time");
                String status         = rs.getString("execution_status");
                String orderId        = rs.getString("order_id");
                java.sql.Date orderDate = rs.getDate("order_date");

                E2EBindingDao e2e = new E2EBindingDao();
                if (!e2e.existsOrder(orderId, orderDate)) {
                    // use UPSERT to be idempotent (also works if a row with same run_id exists)
                    e2e.upsertBinding(
                            application, testType, functionality, scenario,
                            bindingTC, runId, execDate, execTime, status,
                            orderId, orderDate, null
                    );
                }
            } catch (SQLException e) {
                LogUtil.warn(E2EBindingSyncUtil.class, "DB E2E sync failed: " + e.getMessage());
            }
            return;
        }

        final String filePath = ConfigReader.getProperty("Test_Data_File_Path");
        final String txnSheetName = ConfigReader.getProperty("Transactional_Data_Sheet_Name");
        final String e2eSheetName = ConfigReader.getProperty("End_To_End_Sheet_Name");

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            final Sheet txn = workbook.getSheet(txnSheetName);
            final Sheet e2e = workbook.getSheet(e2eSheetName);

            if (txn == null || e2e == null) {
                LogUtil.warn(E2EBindingSyncUtil.class,
                        "Transactional or E2E sheet not found (txn='%s', e2e='%s'). Skipping sync."
                                .formatted(txnSheetName, e2eSheetName));
                return;
            }

            final TxnRow latest = findLatestTransactionalOrderRow(txn);
            if (latest == null) {
                LogUtil.info(E2EBindingSyncUtil.class,
                        "No Transactional rows with BOTH Order ID and Order Date found. Nothing to sync.");
                return;
            }

            // Global duplicate guard on (OrderID, OrderDate)
            final boolean existsInE2E = ExcelUtil.containsPairInColumns(
                    e2e,
                    E2EBindingColumnIndex.ORDER_ID, latest.orderId(),
                    E2EBindingColumnIndex.ORDER_DATE, latest.orderDate(),
                    E2E_START_ROW
            );
            if (existsInE2E) {
                LogUtil.info(E2EBindingSyncUtil.class,
                        "Skipping sync: Order already present in E2E Binding -> ID=%s, Date=%s"
                                .formatted(latest.orderId(), latest.orderDate()));
                return;
            }

            // Secondary tiny guard: compare with last filled E2E RunID (helps when sheets are mid-update)
            final int lastE2eRowIdx = ExcelUtil.getLastFilledRow(e2e, E2EBindingColumnIndex.RUN_ID, E2E_START_ROW);
            if (lastE2eRowIdx >= E2E_START_ROW) {
                Row lastE2eRow = e2e.getRow(lastE2eRowIdx);
                String lastRunId = ExcelUtil.getCellString(lastE2eRow, E2EBindingColumnIndex.RUN_ID);
                if (java.util.Objects.equals(lastRunId, latest.runId())) {
                    LogUtil.info(E2EBindingSyncUtil.class,
                            "Skipping sync: last E2E row already has RunID %s".formatted(lastRunId));
                    return;
                }
            }

            // Append at next available row
            final int targetRowIdx = ExcelUtil.findNextAvailableRow(e2e, E2EBindingColumnIndex.RUN_ID, E2E_START_ROW);
            Row target = e2e.getRow(targetRowIdx);
            if (target == null) target = e2e.createRow(targetRowIdx);

            final CellStyle bordered = createBorderStyle(workbook);

            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.RUN_ID, latest.runId(), bordered);
            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.EXEC_DATE, latest.execDate(), bordered);
            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.EXEC_TIME, latest.execTime(), bordered);
            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.EXEC_STATUS, latest.execStatus(), bordered);
            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.ORDER_ID, latest.orderId(), bordered);
            ExcelUtil.setCellValue(target, E2EBindingColumnIndex.ORDER_DATE, latest.orderDate(), bordered);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }

//            LogUtil.info(E2EBindingSyncUtil.class,
//                    "E2E Binding appended: RunID=%s, OrderID=%s, OrderDate=%s"
//                            .formatted(latest.runId(), latest.orderId(), latest.orderDate()));

              LogUtil.info(E2EBindingSyncUtil.class,
                    "Mirrored %s to E2E Binding -> ID=%s, Date=%s"
                            .formatted(latest.runId(), latest.orderId(), latest.orderDate()));

        } catch (Exception e) {
            LogUtil.error(E2EBindingSyncUtil.class,
                    "E2E Binding sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * Scans Transactional from bottom to top to find the newest row containing BOTH Order ID and Order Date.
     * Returns a {@link TxnRow} with mirrored execution details for writing to E2E Binding.
     */
    private static TxnRow findLatestTransactionalOrderRow(Sheet txn) {
        for (int r = txn.getLastRowNum(); r >= TXN_START_ROW; r--) {
            Row row = txn.getRow(r);
            if (row == null) continue;

            String orderId = ExcelUtil.getCellString(row, ExcelColumnIndex.ORDER_ID);
            String orderDate = ExcelUtil.getCellString(row, ExcelColumnIndex.ORDER_DATE);

            if (!orderId.isBlank() && !orderDate.isBlank()) {
                return new TxnRow(
                        ExcelUtil.getCellString(row, ExcelColumnIndex.RUN_ID),
                        ExcelUtil.getCellString(row, ExcelColumnIndex.EXEC_DATE),
                        ExcelUtil.getCellString(row, ExcelColumnIndex.EXEC_TIME),
                        ExcelUtil.getCellString(row, ExcelColumnIndex.EXEC_STATUS),
                        orderId,
                        orderDate
                );
            }
        }
        return null;
    }

    // ────────────────────────── Helpers ──────────────────────────

    /**
     * Standard thin-borders cell style for consistency with other writers.
     */
    private static CellStyle createBorderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Lightweight value object for Transactional row data we mirror.
     */
    private record TxnRow(
            String runId,      // R-series from Transactional (mirrored into E2E)
            String execDate,
            String execTime,
            String execStatus,
            String orderId,
            String orderDate
    ) {
    }
}
