package com.MyridiusUAF.cli;

import com.MyridiusUAF.ai.AiSwitches;
import com.MyridiusUAF.ai.LlmClient;
import com.MyridiusUAF.ai.LlmClientFactory;
import com.MyridiusUAF.ai.agents.SyntheticDataAgent;
import com.MyridiusUAF.ai.openai.OpenAiConfig;
import com.MyridiusUAF.ai.openai.OpenAiLlmClient;
import com.MyridiusUAF.config.ConfigReader;
import com.MyridiusUAF.utils.excel.ExcelUtil;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLI tool: uses Agentic AI to generate Synthetic_Data sheet rows.
 *
 * Usage (from project root):
 *
 * mvn -q -DskipTests exec:java ^
 *   -Dexec.mainClass="com.MyridiusUAF.cli.AiGenerateSyntheticData" ^
 *   -Dexec.args="5 DemoWebShop login & logout flows"
 */
public class AiGenerateSyntheticData {

    private static final Logger log = LoggerFactory.getLogger(AiGenerateSyntheticData.class);

    public static void main(String[] args) throws Exception {
        if (!AiSwitches.openAiGloballyEnabled() || !AiSwitches.dataGenEnabled()) {
            log.error("AI data generation is disabled. " +
                    "Set ai.openai.enabled=true and ai.datagen.enabled=true in config.properties.");
            System.exit(1);
        }

        if (args.length < 2) {
            log.error("Usage: AiGenerateSyntheticData <RowCount> <Scenario description...>");
            System.exit(1);
        }

        int rowCount = Integer.parseInt(args[0]);
        String scenario = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));

        LlmClient llm = LlmClientFactory.maybeCreate();
        if (llm == null) {
            log.error("Unable to create LLM client. Check OPENAI_API_KEY and LLM_MODEL.");
            System.exit(1);
        }

        // Resolve Excel file path + sheet name from config
        String excelPathStr = ConfigReader.getProperty("Test_Data_File_Path");
        String sheetName = ConfigReader.getProperty("Synthetic_Data_Sheet_Name", "Synthetic_Data");
        if (excelPathStr == null || excelPathStr.isBlank()) {
            log.error("Test_Data_File_Path is not configured in config.properties.");
            System.exit(1);
        }

        Path excelPath = Path.of(excelPathStr);
        log.info("Using Excel file: {}", excelPath.toAbsolutePath());
        log.info("Sheet name: {}", sheetName);

        try (FileInputStream fis = new FileInputStream(excelPath.toFile());
             Workbook wb = new XSSFWorkbook(fis)) {

            Sheet sheet = wb.getSheet(sheetName);
            if (sheet == null) {
                log.error("Sheet '{}' not found in {}", sheetName, excelPath);
                System.exit(1);
            }

            // Header row at index 1 (row 2)
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                log.error("Header row (index 1) is missing in sheet '{}'.", sheetName);
                System.exit(1);
            }

            List<String> headers = new ArrayList<>();
            int lastCell = headerRow.getLastCellNum();
            DataFormatter fmt = new DataFormatter();
            for (int c = 0; c < lastCell; c++) {
                Cell cell = headerRow.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell == null) {
                    headers.add("");
                } else {
                    headers.add(fmt.formatCellValue(cell).trim());
                }
            }
            log.info("Sheet headers: {}", headers);

            // ---- Find Input ID column and last used value (Ip1, Ip2, Ip3 → Ip4...) ----
            int inputIdCol = -1;
            String inputIdHeader = null;
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i);
                if (h == null) continue;
                String normalized = h.replace(" ", "").toLowerCase();
                if ("inputid".equals(normalized)) {
                    inputIdCol = i;
                    inputIdHeader = h;
                    break;
                }
            }

            String lastInputId = null;
            if (inputIdCol >= 0) {
                for (int r = sheet.getLastRowNum(); r >= 2; r--) { // data from row index 2
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Cell cell = row.getCell(inputIdCol, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) continue;
                    String value = fmt.formatCellValue(cell).trim();
                    if (!value.isEmpty()) {
                        lastInputId = value;
                        break;
                    }
                }
            }

            String inputIdPrefix = "Ip";
            int nextInputNumber = 1;
            if (lastInputId != null) {
                Matcher m = Pattern.compile("([A-Za-z]+)(\\d+)").matcher(lastInputId);
                if (m.matches()) {
                    inputIdPrefix = m.group(1);           // e.g. "Ip"
                    nextInputNumber = Integer.parseInt(m.group(2)) + 1; // e.g. 3 -> 4
                }
            }
            log.info("Last Input ID: {}, next will start from {}{}", lastInputId, inputIdPrefix, nextInputNumber);

            // ---- 2) Ask AI to generate rows for these headers ----
            SyntheticDataAgent agent = new SyntheticDataAgent(llm);
            List<Map<String, String>> rows = agent.generateRows(headers, rowCount, scenario);

            if (rows.isEmpty()) {
                log.warn("AI returned no rows. Nothing will be written.");
                return;
            }

            // ---- Override Input ID values with deterministic IpX sequence ----
            if (inputIdHeader != null) {
                for (Map<String, String> rowData : rows) {
                    String newId = inputIdPrefix + nextInputNumber++;
                    rowData.put(inputIdHeader, newId);
                }
                log.info("Assigned sequential Input IDs for {} new rows.", rows.size());
            }

            // ---- 3) Append rows to the bottom of the sheet ----
            CellStyle borderStyle = createBorderStyle(wb);

            int startRow = 2; // data starts at row index 2 (row 3)
            int nextRowIdx = ExcelUtil.findAppendRow(sheet, startRow);
            log.info("Appending {} rows starting from row index {}", rows.size(), nextRowIdx);

            for (Map<String, String> rowData : rows) {
                Row row = sheet.getRow(nextRowIdx);
                if (row == null) row = sheet.createRow(nextRowIdx);

                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    cell.setCellStyle(borderStyle);

                    if (header == null || header.isBlank()) {
                        cell.setBlank();
                        continue;
                    }

                    String value = rowData.getOrDefault(header, "");
                    cell.setCellValue(value);
                }
                nextRowIdx++;
            }

            // 4) Save workbook back to disk
            try (FileOutputStream fos = new FileOutputStream(excelPath.toFile())) {
                wb.write(fos);
            }

            log.info("Successfully appended {} AI-generated rows to sheet '{}' in {}",
                    rows.size(), sheetName, excelPath.toAbsolutePath());
            log.info("AI Config: {}", OpenAiConfig.configSummary());

            // Log token usage if using OpenAI client
            if (llm instanceof OpenAiLlmClient) {
                OpenAiLlmClient openAiClient = (OpenAiLlmClient) llm;
                openAiClient.logSessionUsage();
            }
        }
    }

    private static CellStyle createBorderStyle(Workbook workbook) {
        final CellStyle style = workbook.createCellStyle();
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
