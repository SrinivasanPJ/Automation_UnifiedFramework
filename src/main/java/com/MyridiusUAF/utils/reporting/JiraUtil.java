package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.HttpMultipartMode;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Jira Cloud v3 utility:
 * <ul>
 *   <li>Attachment upload + mediaId resolution (for ADF inline images)</li>
 *   <li>ADF comments with run metadata</li>
 *   <li>Create / search / update issues</li>
 *   <li>Environment, host, and runtime introspection (best-effort)</li>
 * </ul>
 * <p>All methods are best-effort and log failures instead of throwing.</p>
 */
public class JiraUtil {

    private static final Logger LOG = LoggerFactory.getLogger(JiraUtil.class);

    private static final String JIRA_URL   = ConfigReader.getProperty("jira.url");
    private static final String JIRA_EMAIL = ConfigReader.getProperty("jira.email");
    private static final String JIRA_TOKEN = ConfigReader.getProperty("jira.api.token");

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER    = new ObjectMapper();

    private static final Pattern MEDIA_UUID = Pattern.compile(
            "\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b",
            Pattern.CASE_INSENSITIVE);

    // ---- Types ----------------------------------------------------------------

    /** Metadata for a Jira attachment. */
    public record JiraAttachment(String id, String contentUrl, String filename, String mimeType, String mediaId) {}

    /** Best-effort network snapshot. */
    public record NetworkInfo(
            String hostName, String localIp, String publicIp,
            String countryName, String countryCode, String region, String city, String isp) {}

    // ---- Attachments -----------------------------------------------------------

    /**
     * Uploads a file to the issue and resolves its mediaId for inline ADF images.
     * Returns {@code null} on failure.
     */
    public static JiraAttachment attachFile(String issueKey, String filePath) {
        LOG.info("Uploading to Jira: {} -> {}", issueKey, filePath);
        if (isBlank(issueKey) || isBlank(filePath)) return null;

        filePath = filePath.replace("file:\\", "").replace("file:/", "");
        File file = new File(filePath);
        if (!file.exists()) {
            LOG.error("Attachment not found: {}", file.getAbsolutePath());
            return null;
        }

        final ContentType ct = guessContentType(file.getName());

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost post = new HttpPost(JIRA_URL + "/rest/api/3/issue/" + issueKey + "/attachments");
            post.setHeader("X-Atlassian-Token", "no-check");
            post.setHeader("Authorization", authHeader());
            post.setEntity(MultipartEntityBuilder.create()
                    .setMode(HttpMultipartMode.STRICT)
                    .addBinaryBody("file", file, ct, file.getName())
                    .build());

            String responseBody = client.execute(post, response -> {
                int status = response.getCode();
                String body = EntityUtils.toString(response.getEntity());
                if (status >= 200 && status < 300) return body;
                LOG.error("Attachment upload failed: {} -> {}", status, body);
                return null;
            });
            if (responseBody == null) return null;

            JsonNode root = MAPPER.readTree(responseBody);
            if (root.isArray() && root.size() > 0) {
                JsonNode n = root.get(0);
                String attachmentId = n.get("id").asText();
                String mediaId = resolveMediaId(attachmentId);
                return new JiraAttachment(
                        attachmentId,
                        n.get("content").asText(),
                        n.get("filename").asText(),
                        n.get("mimeType").asText(),
                        mediaId
                );
            }
            LOG.error("Unexpected attachment response: {}", responseBody);
        } catch (IOException e) {
            LOG.error("Error uploading attachment to Jira", e);
        }
        return null;
    }

    /** Convenience: upload file and return its content URL or {@code null}. */
    public static String attachFileAndGetUrl(String issueKey, String filePath) {
        JiraAttachment a = attachFile(issueKey, filePath);
        return a != null ? a.contentUrl() : null;
    }

    // ---- Comments (ADF) --------------------------------------------------------

    /**
     * Adds an ADF (Atlassian Document Format) comment with rich execution details.
     * If {@code screenshotMediaId} is provided, it will be embedded inline.
     */
    public static void addADFComment(String issueKey, ITestResult result, String reportUrl, String screenshotMediaId) {
        try {
            final String endpoint = JIRA_URL + "/rest/api/3/issue/" + issueKey + "/comment";

            // Status -> pretty string
            String statusStr = switch (result.getStatus()) {
                case ITestResult.SUCCESS -> "✅ PASSED";
                case ITestResult.FAILURE -> "❌ FAILED";
                case ITestResult.SKIP   -> "⏭️ SKIPPED";
                default -> "UNKNOWN";
            };

            // Links & env
            String adfReportLink = !isBlank(reportUrl) ? reportUrl : (JIRA_URL + "/browse/" + issueKey);
            String env = firstNonBlank(System.getProperty("env.name"), tryGetConfig("env.name"), "Test");
            String browser    = String.valueOf(result.getTestContext().getAttribute("Browser"));
            String executedBy = System.getProperty("user.name");
            String appUrl     = String.valueOf(result.getTestContext().getAttribute("ApplicationUrl") != null
                    ? result.getTestContext().getAttribute("ApplicationUrl") : "N/A");

            // Host/OS/IDE
            String osPretty    = isWindows() ? getWindowsPrettyName() : System.getProperty("os.name", "Unknown");
            String deviceModel = isWindows() ? getWindowsDeviceModel() : "N/A";
            String ideInfo     = detectIDE();
            NetworkInfo net    = getNetworkInfo();
            String runsOn      = detectRunner();

            // Times
            ZoneId zone           = ZoneId.systemDefault();
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
            String startStr   = Instant.ofEpochMilli(result.getStartMillis()).atZone(zone).format(dtf);
            String endStr     = Instant.ofEpochMilli(result.getEndMillis()).atZone(zone).format(dtf);
            String duration   = formatDuration(Math.max(0L, result.getEndMillis() - result.getStartMillis()));

            // Build ADF (kept as JSON string to preserve existing behavior/format)
            final StringBuilder content = new StringBuilder();
            content.append("""
            {
              "body": {
                "type": "doc",
                "version": 1,
                "content": [
                  { "type": "heading", "attrs": {"level": 2}, "content": [{ "type": "text", "text": "🔹 Automated Test Execution Summary" }] },
                  { "type": "bulletList", "content": [
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Test Name: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Test Class: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Status: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Executed On: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },

                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Environment: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Browser: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Executed By: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },

                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Application URL: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Device Model: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Operating System: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },

                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Runs On: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "IDE: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Host Name: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Local IP: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Public IP: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Country: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },

                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Test Start Time: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Test End Time: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] },
                    { "type": "listItem", "content": [{ "type": "paragraph", "content": [ { "type": "text", "text": "Execution Duration: ", "marks": [{"type":"strong"}]}, { "type": "text", "text": "%s" } ] }] }
                  ]},
                  { "type": "paragraph", "content": [
                    { "type": "text", "text": "View Full Automation Report: ", "marks": [{"type":"strong"}]},
                    { "type": "text", "text": "Open Report", "marks": [{ "type": "link", "attrs": { "href": "%s" } }] }
                  ]}
            """.formatted(
                    escapeForJson(result.getName()),
                    escapeForJson(result.getTestClass().getName()),
                    statusStr,
                    new java.util.Date().toString(),

                    escapeForJson(env),
                    escapeForJson(browser),
                    escapeForJson(executedBy),

                    escapeForJson(appUrl),
                    escapeForJson(deviceModel),
                    escapeForJson(osPretty),

                    escapeForJson(runsOn),
                    escapeForJson(ideInfo),
                    escapeForJson(net.hostName()),
                    escapeForJson(net.localIp()),
                    escapeForJson(net.publicIp()),
                    escapeForJson(net.countryName() + (isBlank(net.countryCode()) ? "" : " (" + net.countryCode() + ")")),

                    escapeForJson(startStr),
                    escapeForJson(endStr),
                    escapeForJson(duration),

                    escapeForJson(adfReportLink)
            ));

            if (result.getStatus() == ITestResult.FAILURE && result.getThrowable() != null) {
                String failure = escapeForJson(result.getThrowable().toString());
                content.append(",{ \"type\": \"paragraph\", \"content\": [{ \"type\": \"text\", \"text\": \"Failure Reason:\", \"marks\": [{\"type\": \"strong\"}] }] }");
                content.append(",{ \"type\": \"codeBlock\", \"attrs\": {\"language\": \"java\"}, \"content\": [{ \"type\": \"text\", \"text\": \"")
                        .append(failure).append("\" }] }");
            }

            if (!isBlank(screenshotMediaId)) {
                content.append(",{ \"type\": \"paragraph\", \"content\": [")
                        .append("{ \"type\": \"text\", \"text\": \"Screenshot: \" },")
                        .append("{ \"type\": \"mediaInline\", \"attrs\": {")
                        .append("\"id\": \"").append(screenshotMediaId).append("\", ")
                        .append("\"collection\": \"jira-attachments\", ")
                        .append("\"alt\": \"Failure screenshot\"")
                        .append("} } ] }");
            }

            content.append("]}}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", authHeader())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(content.toString()))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201) {
                LOG.error("Failed to add ADF comment to {}: {}", issueKey, response.body());
            }
        } catch (Exception e) {
            LOG.error("Error adding ADF comment to JIRA issue.", e);
        }
    }

    /** Back-compat overload: without screenshot. */
    public static void addADFComment(String issueKey, ITestResult result, String reportUrl) {
        addADFComment(issueKey, result, reportUrl, null);
    }

    // ---- Issues (create/search/update) ----------------------------------------

    /** Creates a new Jira issue (Task by default) with an ADF description; returns the issue key or {@code null}. */
    public static String createIssue(String summary, String description) {
        if (isBlank(summary)) {
            LOG.warn("JIRA: create skipped, empty summary");
            return null;
        }
        try {
            final String projectKey = ConfigReader.getProperty("jira.project.key");
            final String issueType  = firstNonBlank(ConfigReader.getProperty("jira.issue.type"), "Task");
            final String labelsCsv  = ConfigReader.getProperty("jira.issue.labels");
            final String priority   = ConfigReader.getProperty("jira.issue.priority");
            final String reporterId = ConfigReader.getProperty("jira.reporter.accountId");

            var root   = MAPPER.createObjectNode();
            var fields = root.putObject("fields");
            fields.putObject("project").put("key", projectKey);
            fields.put("summary", summary);
            fields.putObject("issuetype").put("name", issueType);

            // ADF description
            var desc = fields.putObject("description");
            desc.put("type", "doc").put("version", 1);
            var content = desc.putArray("content");
            if (isBlank(description)) description = "Created by automation.";
            for (String line : description.split("\\R")) {
                var p = MAPPER.createObjectNode().put("type", "paragraph");
                p.putArray("content").add(MAPPER.createObjectNode().put("type", "text").put("text", line));
                content.add(p);
            }

            // Optional fields
            if (!isBlank(labelsCsv)) {
                var labels = fields.putArray("labels");
                for (String s : labelsCsv.split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) labels.add(t);
                }
            }
            if (!isBlank(priority))   fields.putObject("priority").put("name", priority.trim());
            if (!isBlank(reporterId)) fields.putObject("reporter").put("id", reporterId.trim());

            String payload = MAPPER.writeValueAsString(root);
            LOG.info("JIRA: creating issue in project={}, type={}, summary='{}'", projectKey, issueType, summary);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JIRA_URL + "/rest/api/3/issue"))
                    .header("Authorization", authHeader())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) {
                String key = MAPPER.readTree(resp.body()).get("key").asText();
                LOG.info("JIRA: created issue {} for summary '{}'", key, summary);
                return key;
            } else {
                logJiraError("create", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            LOG.error("JIRA: create threw exception", e);
        }
        return null;
    }

    /** JQL search by summary; returns newest issue key or {@code null}. */
    public static String searchIssueBySummary(String summary) {
        if (isBlank(summary)) {
            LOG.warn("JIRA: search skipped, empty summary");
            return null;
        }
        try {
            final String project = ConfigReader.getProperty("jira.project.key");
            final String escapedSummary = summary.replace("\"", "\\\"");
            final String jqlReadable = "project=" + project + " AND summary ~ \"" + escapedSummary + "\" ORDER BY created DESC";
            final String jql = java.net.URLEncoder.encode(jqlReadable, StandardCharsets.UTF_8);

            LOG.info("JIRA: searching by summary (project={}, jql={})", project, jqlReadable);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JIRA_URL + "/rest/api/3/search?maxResults=1&fields=key&jql=" + jql))
                    .header("Authorization", authHeader())
                    .header("Accept", "application/json")
                    .GET().build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode issues = MAPPER.readTree(resp.body()).get("issues");
                if (issues != null && issues.isArray() && issues.size() > 0) {
                    String key = issues.get(0).get("key").asText();
                    LOG.info("JIRA: found existing issue {} for summary '{}'", key, summary);
                    return key;
                }
                LOG.info("JIRA: no issue found for summary '{}'", summary);
            } else {
                logJiraError("search", resp.statusCode(), resp.body());
            }
        } catch (Exception e) {
            LOG.warn("JIRA: search failed for summary '{}'", summary, e);
        }
        return null;
    }

    /** Updates summary & description; returns true on success. */
    public static boolean updateIssueSummaryAndDescription(String issueKey, String newSummary, String newDescription) {
        if (isBlank(issueKey)) return false;
        newSummary = trimTo255(firstNonBlank(newSummary, "Automation: " + issueKey));
        final String desc = firstNonBlank(newDescription, "");

        final String payload = """
        { "fields": {
            "summary": "%s",
            "description": { "type":"doc","version":1,
              "content":[{ "type":"paragraph","content":[{ "type":"text","text":"%s"}]}]
            }
        }}
        """.formatted(escapeForJson(newSummary), escapeForJson(desc));

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JIRA_URL + "/rest/api/3/issue/" + issueKey))
                    .header("Authorization", authHeader())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .method("PUT", HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 204) {
                LOG.info("JIRA: updated summary/description for {}", issueKey);
                return true;
            }
            logJiraError("update", resp.statusCode(), resp.body());
        } catch (Exception e) {
            LOG.error("JIRA: update failed for {}", issueKey, e);
        }
        return false;
    }

    /** Find by summary or create a new Task. */
    public static String findOrCreateIssue(String summary, String description) {
        LOG.info("JIRA: findOrCreate '{}'", summary);
        String existing = searchIssueBySummary(summary);
        return existing != null ? existing : createIssue(summary, description);
    }

    // Back-compat placeholders (kept)
    public static void attachScreenshot(String issueKey, String screenshotPath) { /* optional */ }
    public static String getIssueStatus(String issueKey) { return "Unknown"; }

    // ---- Internal helpers ------------------------------------------------------

    private static String resolveMediaId(String attachmentId) {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JIRA_URL + "/rest/api/3/attachment/content/" + attachmentId))
                    .header("Authorization", authHeader())
                    .GET().build();

            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            String location = resp.headers().firstValue("location").orElse(null);
            if (location != null) {
                var m = MEDIA_UUID.matcher(location);
                if (m.find()) {
                    String uuid = m.group();
                    LOG.info("Resolved mediaId for attachment {} -> {}", attachmentId, uuid);
                    return uuid;
                }
            } else {
                LOG.warn("No Location header while resolving mediaId for attachment {}", attachmentId);
            }
        } catch (Exception e) {
            LOG.warn("Could not resolve mediaId for attachment {}", attachmentId, e);
        }
        return null;
    }

    private static ContentType guessContentType(String name) {
        if (name == null) return ContentType.DEFAULT_BINARY;
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".png"))  return ContentType.IMAGE_PNG;
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return ContentType.IMAGE_JPEG;
        if (n.endsWith(".html")) return ContentType.TEXT_HTML;
        return ContentType.DEFAULT_BINARY;
    }

    private static String detectRunner() {
        if (System.getenv("JENKINS_HOME") != null) return "Jenkins";
        if (Boolean.parseBoolean(System.getenv("GITHUB_ACTIONS"))) return "GitHub Actions";
        if (Boolean.parseBoolean(System.getenv("GITLAB_CI"))) return "GitLab CI";
        if (System.getenv("TF_BUILD") != null) return "Azure Pipelines";
        if (Boolean.parseBoolean(System.getenv("CIRCLECI"))) return "CircleCI";
        if (System.getenv("CI") != null) return "CI Environment";
        return "Local Machine";
    }

    private static String formatDuration(long millis) {
        long s = millis / 1000;
        long h = s / 3600; s %= 3600;
        long m = s / 60;   s %= 60;
        return (h > 0) ? String.format("%02dh %02dm %02ds", h, m, s)
                : String.format("%02dm %02ds", m, s);
    }

    private static void logJiraError(String action, int status, String body) {
        try {
            JsonNode err = MAPPER.readTree(body);
            JsonNode errors = err.get("errors");
            if (errors != null && errors.fieldNames().hasNext()) {
                String firstField = errors.fieldNames().next();
                LOG.error("JIRA: {} failed [{}]: {} -> {}", action, status, firstField, errors.get(firstField).asText());
                return;
            }
        } catch (Exception ignored) { }
        LOG.error("JIRA: {} failed [{}]: {}", action, status, body);
    }

    private static String encodeAuth() {
        return Base64.getEncoder().encodeToString((JIRA_EMAIL + ":" + JIRA_TOKEN).getBytes(StandardCharsets.UTF_8));
    }
    private static String authHeader() { return "Basic " + encodeAuth(); }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String escapeForJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private static String tryGetConfig(String key) {
        try {
            String v = ConfigReader.getProperty(key);
            return (!isBlank(v)) ? v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- OS / Device (Windows) ------------------------------------------------

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static String getWindowsPrettyName() {
        String caption = runPowerShell("(Get-CimInstance Win32_OperatingSystem | Select-Object -Expand Caption)");
        String displayVersion = runPowerShell("(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion').DisplayVersion");
        if (isBlank(caption) && isBlank(displayVersion)) {
            String base = System.getProperty("os.name", "Windows");
            String ver  = System.getProperty("os.version", "");
            return ver.isBlank() ? base : base + " " + ver;
        }
        caption = caption == null ? "" : caption.trim();
        displayVersion = displayVersion == null ? "" : displayVersion.trim();
        return displayVersion.isBlank() ? caption : caption + " " + displayVersion;
    }

    private static String getWindowsDeviceModel() {
        String model = runPowerShell("(Get-CimInstance Win32_ComputerSystem | Select-Object -Expand Model)");
        if (!isBlank(model)) return model.trim();
        String csName = runPowerShell("(Get-CimInstance Win32_ComputerSystemProduct | Select-Object -Expand Name)");
        return !isBlank(csName) ? csName.trim() : "N/A";
    }

    /** Executes a short PowerShell command and returns the first non-empty line (Windows only). */
    private static String runPowerShell(String ps) {
        if (!isWindows()) return null;
        ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line, chosen = null;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) { chosen = line; break; }
                }
                p.waitFor();
                return chosen;
            }
        } catch (Exception e) {
            LOG.debug("PowerShell call failed: {}", ps, e);
            return null;
        }
    }

    // ---- Network / IDE --------------------------------------------------------

    private static NetworkInfo getNetworkInfo() {
        String host = null, localIp = null, publicIp = null, countryName = null, countryCode = null, region = null, city = null, isp = null;

        try { host = java.net.InetAddress.getLocalHost().getHostName(); } catch (Exception ignored) {}

        try {
            var ifaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                var ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                var addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    var a = addrs.nextElement();
                    if (a instanceof java.net.Inet4Address v4 && v4.isSiteLocalAddress()) {
                        localIp = v4.getHostAddress();
                        break;
                    }
                }
                if (localIp != null) break;
            }
        } catch (Exception ignored) {}

        try {
            String url = firstNonBlank(System.getProperty("geo.service.url"), tryGetConfig("geo.service.url"), "https://ipapi.co/json");
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent","MyridiusUAF/1.0")
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET().build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                var root = MAPPER.readTree(resp.body());
                publicIp    = optText(root,"ip");
                countryName = firstNonBlank(optText(root,"country_name"), optText(root,"countryName"));
                countryCode = firstNonBlank(optText(root,"country"), optText(root,"countryCode"));
                region      = firstNonBlank(optText(root,"region"), optText(root,"region_name"), optText(root,"state"));
                city        = optText(root,"city");
                isp         = firstNonBlank(optText(root,"org"), optText(root,"org_name"), optText(root,"asn_org"));
            }
        } catch (Exception ignored) { }

        if (isBlank(countryName)) {
            var loc = Locale.getDefault();
            countryCode = firstNonBlank(countryCode, loc.getCountry());
            countryName = firstNonBlank(countryName, loc.getDisplayCountry(Locale.ENGLISH), "N/A");
        }

        return new NetworkInfo(
                firstNonBlank(host,"N/A"),
                firstNonBlank(localIp,"N/A"),
                firstNonBlank(publicIp,"N/A"),
                firstNonBlank(countryName,"N/A"),
                firstNonBlank(countryCode,""),
                firstNonBlank(region,""),
                firstNonBlank(city,""),
                firstNonBlank(isp,"N/A")
        );
    }

    private static String optText(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }

    /** Detects common IDEs from runtime hints (best effort). */
    private static String detectIDE() {
        String agent = getJavaAgentPathArg("idea_rt.jar");
        if (agent != null) {
            String norm = agent.replace('\\','/');
            int i = norm.toLowerCase(Locale.ROOT).indexOf("/lib/idea_rt.jar");
            if (i > 0) {
                String root = norm.substring(0, i);
                String name = root.substring(root.lastIndexOf('/') + 1);
                if (!name.isBlank()) return name;
            }
            return "IntelliJ IDEA";
        }
        if (isClassPresent("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner") || System.getProperty("eclipse.buildId") != null) {
            return "Eclipse IDE";
        }
        if (System.getenv("VSCODE_PID") != null || "vscode".equalsIgnoreCase(System.getenv("TERM_PROGRAM"))) {
            return "Visual Studio Code";
        }
        return "N/A";
    }

    private static String getJavaAgentPathArg(String needleLower) {
        for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-javaagent:") && arg.toLowerCase(Locale.ROOT).contains(needleLower)) {
                String path = arg.substring("-javaagent:".length());
                int eq = path.indexOf('=');
                if (eq > 0) path = path.substring(0, eq);
                return path;
            }
        }
        return null;
    }

    private static boolean isClassPresent(String name) {
        try { Class.forName(name, false, JiraUtil.class.getClassLoader()); return true; }
        catch (Throwable ignore) { return false; }
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (!isBlank(v)) return v;
        return null;
    }

    /** Public utility so callers can cap titles safely. */
    public static String trimTo255(String s) {
        return (s == null) ? null : (s.length() > 255 ? s.substring(0, 255) : s);
    }

    public static void addComment(String issueKey, String comment) {
        if (isBlank(issueKey) || isBlank(comment)) return;
        String body = """
        {"body":{"type":"doc","version":1,"content":[
          {"type":"paragraph","content":[{"type":"text","text":"%s"}]}
        ]}}
        """.formatted(escapeForJson(comment));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(JIRA_URL + "/rest/api/3/issue/" + issueKey + "/comment"))
                    .header("Authorization", authHeader())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { /* best-effort */ }
    }

}
