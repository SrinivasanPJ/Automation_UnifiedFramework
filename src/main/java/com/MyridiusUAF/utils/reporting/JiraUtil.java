package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * Utility class for all JIRA REST API interactions.
 * Supports issue creation, comment addition, screenshot attachment, and live status fetch.
 * Designed for robust use in automation frameworks and CI/CD pipelines.
 */
public class JiraUtil {
    private static final Logger logger = LoggerFactory.getLogger(JiraUtil.class);

    private static final String JIRA_URL         = ConfigReader.getProperty("jira.url");         // e.g. https://myorg.atlassian.net
    private static final String JIRA_EMAIL       = ConfigReader.getProperty("jira.email");
    private static final String JIRA_TOKEN       = ConfigReader.getProperty("jira.api.token");
    private static final String JIRA_PROJECT_KEY = ConfigReader.getProperty("jira.project.key"); // e.g. "KAN"

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    // ======== NEW: Add ADF (Atlassian Document Format) comment for enterprise Jira ========

    /**
     * Adds a rich ADF comment to a JIRA issue with test automation results.
     * @param issueKey JIRA issue key (e.g., KAN-101)
     * @param result   The ITestResult from TestNG (for detailed info)
     */
    public static void addADFComment(String issueKey, ITestResult result) {
        try {
            String endpoint = JIRA_URL + "/rest/api/3/issue/" + issueKey + "/comment";
            String encodedAuth = encodeAuth();

            String statusStr = switch (result.getStatus()) {
                case ITestResult.SUCCESS -> "✅ PASSED";
                case ITestResult.FAILURE -> "❌ FAILED";
                case ITestResult.SKIP    -> "⏭️ SKIPPED";
                default                  -> "UNKNOWN";
            };

            String reportUrl = ExtentReportManager.INSTANCE.getReportUrl();

            // --- Failure-specific blocks ---
            StringBuilder failureBlock = new StringBuilder();
            if (result.getStatus() == ITestResult.FAILURE) {
                Throwable ex = result.getThrowable();
                String stack = ex != null ? ex.toString() : "No stacktrace";
                // Failure Reason (code block)
                failureBlock.append(",{")
                        .append("\"type\":\"paragraph\",")
                        .append("\"content\":[")
                        .append("{\"type\":\"text\",\"text\":\"Failure Reason:\",\"marks\":[{\"type\":\"strong\"}]}")
                        .append("]}")
                        .append(",{")
                        .append("\"type\":\"codeBlock\",\"attrs\":{\"language\":\"java\"},\"content\":[")
                        .append("{\"type\":\"text\",\"text\":\"")
                        .append(stack.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", ""))
                        .append("\"}]}")
                        // Screenshot line
                        .append(",{")
                        .append("\"type\":\"paragraph\",")
                        .append("\"content\":[{\"type\":\"text\",\"text\":\"Screenshot attached below.\"}]")
                        .append("}");
            }

            // Compose ADF JSON (with failure details injected if any)
            String adfBody = String.format("""
        {
          "body": {
            "type": "doc",
            "version": 1,
            "content": [
              {
                "type": "heading",
                "attrs": { "level": 2 },
                "content": [
                  { "type": "text", "text": "🔹 Automated Test Execution Summary" }
                ]
              },
              {
                "type": "bulletList",
                "content": [
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Test Name: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Test Class: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Status: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Executed On: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Environment: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Browser: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  },
                  {
                    "type": "listItem",
                    "content": [{
                      "type": "paragraph",
                      "content": [
                        { "type": "text", "text": "Executed By: ", "marks": [ { "type": "strong" } ] },
                        { "type": "text", "text": "%s" }
                      ]
                    }]
                  }
                ]
              },
              {
                "type": "paragraph",
                "content": [
                  {
                    "type": "text",
                    "text": "View Full Automation Report: ",
                    "marks": [ { "type": "strong" } ]
                  },
                  {
                    "type": "text",
                    "text": "Open Report",
                    "marks": [
                      {
                        "type": "link",
                        "attrs": { "href": "%s" }
                      }
                    ]
                  }
                ]
              }
              %s
            ]
          }
        }
        """,
                    result.getName(),
                    result.getTestClass().getName(),
                    statusStr,
                    new java.util.Date(),
                    System.getProperty("env.name", "Unknown"),
                    result.getTestContext().getAttribute("Browser"),
                    System.getProperty("user.name"),
                    reportUrl,
                    failureBlock // this adds the extra blocks for failure only
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(adfBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                logger.info("ADF comment added to JIRA issue {}", issueKey);
            } else {
                logger.error("Failed to add ADF comment to {}: {}", issueKey, response.body());
            }
        } catch (Exception e) {
            logger.error("Error adding ADF comment to JIRA issue.", e);
        }
    }

    // ======================= The rest of your original code is unchanged below =========================

    public static String createIssue(String summary, String description) {
        // ... same as your code ...
        return "Unknown"; // or null if you prefer
    }

    public static void addComment(String issueKey, String comment) {
        // ... same as your code ...
    }

    public static void attachScreenshot(String issueKey, String screenshotPath) {
        // ... same as your code ...
    }

    public static String getIssueStatus(String issueKey) {
        // ... same as your code ...
        return "Unknown"; // or null if you prefer
    }

    // ----------------- PRIVATE HELPERS -------------------

    private static String encodeAuth() {
        String auth = JIRA_EMAIL + ":" + JIRA_TOKEN;
        return Base64.getEncoder().encodeToString(auth.getBytes());
    }

    private static String parseIssueKeyFromBody(String body) {
        try {
            int keyIndex = body.indexOf("\"key\":\"");
            if (keyIndex == -1) return null;
            int start = keyIndex + 7;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        } catch (Exception e) {
            logger.error("Failed to parse issue key from JIRA response body.", e);
            return null;
        }
    }

    private static String escapeForJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}
