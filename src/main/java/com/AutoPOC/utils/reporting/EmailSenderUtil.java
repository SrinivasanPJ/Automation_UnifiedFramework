package com.AutoPOC.utils.reporting;

import com.AutoPOC.config.ConfigReader;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Properties;

/**
 * Utility class to send automation test result summary emails with attached Extent Reports.
 * <p>
 * Supports dynamic total/pass/fail counters, rich HTML formatting, and multiple CC recipients.
 */
public final class EmailSenderUtil {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderUtil.class);

    private static final String SMTP_HOST = ConfigReader.getProperty("smtp.host");
    private static final String SMTP_PORT = ConfigReader.getProperty("smtp.port");
    private static final String USERNAME  = ConfigReader.getProperty("smtp.username");
    private static final String PASSWORD = System.getenv("SMTP_PASSWORD") != null
            ? System.getenv("SMTP_PASSWORD")
            : ConfigReader.getDecryptedProperty("smtp.password");

    private static final String FROM      = ConfigReader.getProperty("email.from");
    private static final String TO        = ConfigReader.getProperty("email.to");

    private static final String[] CC_RECIPIENTS = ConfigReader.getProperty("email.cc").split(",");

    /** -------------------------------------------------
     *  Public Methods
     *  ------------------------------------------------- */

    /**
     * Sends a test result summary email with attached latest execution report.
     *
     * @param totalTests  Total number of tests executed
     * @param testsPassed Number of tests passed
     * @param testsFailed Number of tests failed
     */
    public static void sendTestResultEmail(int totalTests, int testsPassed, int testsFailed) {
        String reportPath = ExtentReportManager.INSTANCE.getReportPath();
        logger.info("Attaching report from: {}", reportPath);

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(USERNAME, PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(FROM));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(TO));

            for (String cc : CC_RECIPIENTS) {
                if (!cc.isBlank()) {
                    message.addRecipient(Message.RecipientType.CC, new InternetAddress(cc.trim()));
                }
            }

            String subjectTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            message.setSubject("Automation POC - Test Execution Report - " + subjectTime);

            Multipart multipart = new MimeMultipart();

            // Email body
            MimeBodyPart body = new MimeBodyPart();
            body.setContent(buildHtmlContent(totalTests, testsPassed, testsFailed), "text/html");
            multipart.addBodyPart(body);

            // Report attachment
            MimeBodyPart attachment = new MimeBodyPart();
            attachment.attachFile(new File(reportPath));
            multipart.addBodyPart(attachment);

            message.setContent(multipart);

            Transport.send(message);
            logger.info("Email sent successfully!");

        } catch (Exception e) {
            logger.error("Failed to send test result email.", e);
        }
    }

    /** -------------------------------------------------
     *  Private Helper Methods
     *  ------------------------------------------------- */

    /**
     * Builds dynamic HTML body content summarizing the test execution.
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

        return """
            <html>
              <body style="font-family: Arial, sans-serif; font-size: 14px; background-color: #f9f9f9; padding: 20px;">
                <h2 style="color: #007B04;">Project: Automation POC</h2>
                <p>Hi Team,</p>
                <p>The automation execution has completed. Please find the summary below:</p>
                <table border="1" cellpadding="10" cellspacing="0" style="border-collapse: collapse; width: 60%%;">
                  <thead style="background-color: #e6f7ff;">
                    <tr>
                      <th>Execution Date</th>
                      <th>Total Tests</th>
                      <th style="color: green;">Passed</th>
                      <th style="color: red;">Failed</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr style="text-align: center;">
                      <td>%s</td>
                      <td>%d</td>
                      <td style="color: green;">%d (%.2f%%)</td>
                      <td style="color: red;">%d (%.2f%%)</td>
                    </tr>
                  </tbody>
                </table>
                <p>Please check the attached report for detailed logs and screenshots.</p>
                <p style="color: #999;">This is an automated email from the Automation Framework.</p>
              </body>
            </html>
            """.formatted(executionDate, totalTests, testsPassed, passPct, testsFailed, failPct);
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
     * Gets the most recently modified report file from the reports folder.
     *
     * @return Latest File instance
     */
    private static File getLatestReportFile() {
        File reportDir = new File("reports");
        File[] files = reportDir.listFiles();

        if (files == null || files.length == 0) {
            throw new RuntimeException("No reports found in 'reports' directory.");
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return files[0];
    }
}
