package com.MyridiusUAF.utils.reporting;

import com.MyridiusUAF.config.ConfigReader;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Utility class for sending test execution summary emails with HTML content, inline logo, and attached report.
 */
public final class EmailSenderUtil {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderUtil.class);

    // Configuration values
    private static final String SMTP_HOST = ConfigReader.getProperty("smtp.host");
    private static final String SMTP_PORT = ConfigReader.getProperty("smtp.port");
    private static final String DISPLAY_NAME = ConfigReader.getProperty("email.display.name");
    private static final String USERNAME = ConfigReader.getProperty("smtp.username");
    private static final String PASSWORD = System.getenv("SMTP_PASSWORD") != null
            ? System.getenv("SMTP_PASSWORD")
            : ConfigReader.getDecryptedProperty("smtp.password");

    private static final String FROM = ConfigReader.getProperty("email.from");
    private static final String[] TO_RECIPIENTS = ConfigReader.getProperty("email.to").split(",");
    private static final String[] CC_RECIPIENTS = ConfigReader.getProperty("email.cc").split(",");
    private static final String[] BCC_RECIPIENTS = ConfigReader.getProperty("email.bcc").split(",");


    /**
     * Sends an automated test execution summary email.
     * <p>
     * This method composes and sends an HTML email with:
     * <ul>
     *     <li>An embedded inline logo</li>
     *     <li>A formatted summary of total, passed, and failed test counts</li>
     *     <li>The attached HTML report</li>
     * </ul>
     * It supports TLS-secured SMTP with authentication and respects configurations
     * from the framework's `config.properties` file.
     *
     * <p>Recipients (To, CC, BCC) and logo path are dynamically resolved.
     * <p>This utility is typically invoked after test suite execution completes.
     *
     * @param totalTests  total number of test cases executed
     * @param testsPassed total number of tests passed
     * @param testsFailed total number of tests failed
     */
    public static void sendTestResultEmail(int totalTests, int testsPassed, int testsFailed) {
        // Resolve report file path from ExtentReport manager
        String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        logger.info("Attaching report from: {}", reportPath);

        // Configure SMTP session properties
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        // Authenticate session using provided credentials
        Session session = Session.getInstance(props, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);

            // Set sender's email address with friendly display name
            try {
                message.setFrom(new InternetAddress(FROM, DISPLAY_NAME));
            } catch (UnsupportedEncodingException e) {
                logger.warn("Failed to apply display name. Using plain email.");
                message.setFrom(new InternetAddress(FROM));
            }

            // Add recipients from config
            addRecipients(message, Message.RecipientType.TO, TO_RECIPIENTS);
            addRecipients(message, Message.RecipientType.CC, CC_RECIPIENTS);
            addRecipients(message, Message.RecipientType.BCC, BCC_RECIPIENTS);

            // Set email subject with timestamp
            String subjectTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            message.setSubject("Automation POC - Test Execution Report - " + subjectTime);

            // ─── Build Multipart Email with Inline Logo ───────────────────────

            Multipart multipart = new MimeMultipart("related");

            // 1. HTML body part with embedded summary and logo reference
            MimeBodyPart htmlBody = new MimeBodyPart();
            String htmlContent = buildHtmlContent(totalTests, testsPassed, testsFailed);
            htmlBody.setContent(htmlContent, "text/html; charset=utf-8");
            multipart.addBodyPart(htmlBody);

            // 2. HTML report file as downloadable attachment
            MimeBodyPart attachmentPart = new MimeBodyPart();
            attachmentPart.attachFile(new File(reportPath));
            multipart.addBodyPart(attachmentPart);

            // Finalize the composed message
            message.setContent(multipart);

            // Dispatch the email
            Transport.send(message);
            logger.info("Email sent successfully!");

        } catch (Exception e) {
            logger.error("Failed to send test result email.", e);
        }
    }

    /**
     * Returns the current system date and time formatted as "yyyy-MM-dd HH:mm:ss".
     *
     * @return Formatted datetime string
     */
    private static String getCurrentDateTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Adds multiple recipients to the message.
     */
    private static void addRecipients(MimeMessage message, Message.RecipientType type, String[] recipients)
            throws MessagingException {
        for (String email : recipients) {
            if (!email.isBlank()) {
                message.addRecipient(type, new InternetAddress(email.trim()));
            }
        }
    }

    /**
     * Builds dynamic HTML body content summarizing the test execution,
     * including the inline logo at the top.
     *
     * @param totalTests  Total number of executed tests
     * @param testsPassed Passed tests count
     * @param testsFailed Failed tests count
     * @return Formatted HTML string
     */
    private static String buildHtmlContent(int totalTests, int testsPassed, int testsFailed) {
        double passPct = totalTests == 0 ? 0 : (testsPassed * 100.0 / totalTests);
        double failPct = totalTests == 0 ? 0 : (testsFailed * 100.0 / totalTests);
        String executionDate = getCurrentDateTime();

        String template = """
        <html>
           <body style="font-family: Arial, sans-serif; font-size: 14px; background-color: #f9f9f9;">
              <!-- Top header row with title and logo -->
              <table width="100%" style="border: none; margin: 50; padding: 0;">
                 <tr style="vertical-align: middle;">
                    <td align="left">
                       <h2 style="color: #007B04; margin: -30px 0 0 0;">Project: <span style="font-weight: bold;">Automation POC</span></h2>
                    </td>
                    <td align="right">
                       <img src="https://www.bizztracker.com/wp-content/uploads/2019/11/shutterstock_1257993892-1350x600-1.jpg"
                          alt="POC Logo" width="250" height="100" style="margin: 0;" />
                    </td>
                 </tr>
              </table>
              <p style="margin-top: -25px;">Hi Team,</p>
              <p>The automation execution has completed. Please find the summary below:</p>
              <!-- Execution summary table -->
              <table border="1" cellpadding="10" cellspacing="0" style="border-collapse: collapse; width: 60%;">
                 <thead style="background-color: #e6f7ff;">
                    <tr style="text-align: center;">
                       <th>Execution Date</th>
                       <th>Total Tests</th>
                       <th style="color: green;">Passed</th>
                       <th style="color: red;">Failed</th>
                    </tr>
                 </thead>
                 <tbody>
                    <tr style="text-align: center;">
                       <td>{{DATE}}</td>
                       <td>{{TOTAL}}</td>
                       <td style="color: green;">{{PASS}} ({{PASS_PCT}}%)</td>
                       <td style="color: red;">{{FAIL}} ({{FAIL_PCT}}%)</td>
                    </tr>
                 </tbody>
              </table>
              <p>Please check the attached report for detailed logs and screenshots.</p>
              <p style="color: #999;">This is an automated email from the Automation Framework.</p>
           </body>
        </html>
        """;

        return template
                .replace("{{DATE}}", executionDate)
                .replace("{{TOTAL}}", String.valueOf(totalTests))
                .replace("{{PASS}}", String.valueOf(testsPassed))
                .replace("{{FAIL}}", String.valueOf(testsFailed))
                .replace("{{PASS_PCT}}", String.format("%.2f", passPct))
                .replace("{{FAIL_PCT}}", String.format("%.2f", failPct));
    }
}
